import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { api, API_BASE } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type BillRow = {
  publicId: string;
  consumerNo: string;
  consumerName: string;
  amount: number;
  amountAfterDueDate?: number | null;
  dueDate?: string | null;
  batchId: string;
  createdAt: string;
};

type UploadResult = { batchId: string; imported: number; skipped: number; message: string };

export function UploadBillsPage() {
  const { session } = useAuth();
  const [list, setList] = useState<BillRow[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [dragOver, setDragOver] = useState(false);

  const load = useCallback(async () => {
    if (!session?.token) return;
    try {
      const rows = await api<BillRow[]>('/api/corporate/bills', { token: session.token });
      setList(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load bills');
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!session?.token || !file) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const form = new FormData();
      form.append('file', file);
      const res = await fetch(`${API_BASE}/api/corporate/bills/upload`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${session.token}` },
        body: form,
      });
      const data = (await res.json().catch(() => ({}))) as UploadResult & { error?: string };
      if (!res.ok) throw new Error(data.error || res.statusText || 'Upload failed');
      setOk(data.message || `Imported ${data.imported}`);
      setFile(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setLoading(false);
    }
  }

  if (!session?.token) {
    return (
      <div className="page">
        <PageHeader title="Upload Bills" subtitle="Batch bill file for branch pay" />
        <p className="muted">Please <Link to="/login">login</Link> first.</p>
      </div>
    );
  }

  return (
    <div className="page desk-page">
      <PageHeader
        title="Upload Bills"
        subtitle="CSV for branch pay desk — consumerNo, name, amount, amountAfterDueDate, dueDate"
      />
      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-success">{ok}</div>}

      <div className="desk-split desk-split-wide">
        <form className="desk-card desk-card-accent" onSubmit={onSubmit}>
          <p className="desk-kicker">Import</p>
          <h3 className="desk-card-title">Bill file (CSV)</h3>
          <p className="muted desk-card-lead">
            Branch staff will select company + consumer from this file. No wallet charge on upload.
          </p>

          <label
            className={`desk-dropzone${dragOver ? ' is-drag' : ''}${file ? ' has-file' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
            onDragLeave={() => setDragOver(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDragOver(false);
              const f = e.dataTransfer.files?.[0];
              if (f) setFile(f);
            }}
          >
            <input
              type="file"
              accept=".csv,text/csv"
              required={!file}
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            <span className="desk-dropzone-title">
              {file ? file.name : 'Drop CSV here or browse'}
            </span>
            <span className="muted">
              Example: <code>3451375,Ali Khan,1500.00,1650.00,2026-11-30</code>
            </span>
          </label>

          <button className="btn btn-primary desk-btn-block" type="submit" disabled={loading || !file}>
            {loading ? 'Uploading…' : 'Upload bills'}
          </button>
        </form>

        <div className="desk-card">
          <div className="desk-list-head">
            <div>
              <p className="desk-kicker">Library</p>
              <h3 className="desk-card-title">Uploaded bills</h3>
            </div>
            <span className="desk-count">{list.length}</span>
          </div>
          {list.length === 0 ? (
            <p className="muted">No bills yet — upload a CSV to populate the branch pay list.</p>
          ) : (
            <div className="table-wrap desk-table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Consumer</th>
                    <th>Name</th>
                    <th>Amount</th>
                    <th>After due</th>
                    <th>Due</th>
                  </tr>
                </thead>
                <tbody>
                  {list.map((r) => (
                    <tr key={r.publicId}>
                      <td><code>{r.consumerNo}</code></td>
                      <td>{r.consumerName}</td>
                      <td className="desk-amount">{Number(r.amount).toLocaleString()}</td>
                      <td>{r.amountAfterDueDate != null ? Number(r.amountAfterDueDate).toLocaleString() : '—'}</td>
                      <td>{r.dueDate ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
