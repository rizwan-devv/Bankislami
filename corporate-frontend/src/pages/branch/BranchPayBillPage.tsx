import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';

type Company = { publicId: string; name: string; ntn?: string | null; email: string; billCount: number };
type Bill = {
  publicId: string;
  consumerNo: string;
  consumerName: string;
  amount: number;
  amountAfterDueDate?: number | null;
  dueDate?: string | null;
};

export function BranchPayBillPage() {
  const { session } = useAuth();
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState('');
  const [bills, setBills] = useState<Bill[]>([]);
  const [consumerNo, setConsumerNo] = useState('');
  const [selected, setSelected] = useState<Bill | null>(null);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [loading, setLoading] = useState(false);

  const loadCompanies = useCallback(async () => {
    if (!session?.token) return;
    try {
      const rows = await api<Company[]>('/api/branch/companies', { token: session.token });
      setCompanies(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load companies');
    }
  }, [session?.token]);

  useEffect(() => {
    void loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    if (!session?.token || !companyId) {
      setBills([]);
      setSelected(null);
      return;
    }
    void (async () => {
      try {
        const rows = await api<Bill[]>(`/api/branch/companies/${companyId}/bills`, { token: session.token });
        setBills(rows);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load consumers');
      }
    })();
  }, [session?.token, companyId]);

  useEffect(() => {
    const bill = bills.find((b) => b.consumerNo === consumerNo.trim()) || null;
    setSelected(bill);
  }, [consumerNo, bills]);

  async function submitAction(action: 'PAY' | 'SKIP' | 'FINISH') {
    if (!session?.token || !companyId) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const res = await api<{ message: string }>('/api/branch/pay-bill', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          companyPublicId: companyId,
          consumerNo: consumerNo.trim() || (bills[0]?.consumerNo ?? 'N/A'),
          action,
        }),
      });
      setOk(res.message);
      if (action === 'FINISH') {
        setCompanyId('');
        setConsumerNo('');
        setSelected(null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Action failed');
    } finally {
      setLoading(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void submitAction('PAY');
  }

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="Pay Bill" subtitle="Branch desk only" />
        <p className="muted">Login as branch user. <Link to="/login">Login</Link></p>
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader title="Pay Bill" subtitle="Company from uploads · consumer from bill file · no wallet deduct" />
      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-success">{ok}</div>}

      <form className="panel form-grid" onSubmit={onSubmit}>
        <div className="form-row">
          <label>Company</label>
          <select value={companyId} onChange={(e) => { setCompanyId(e.target.value); setConsumerNo(''); }} required>
            <option value="">Select company…</option>
            {companies.map((c) => (
              <option key={c.publicId} value={c.publicId}>
                {c.name} ({c.billCount} bills)
              </option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label>Consumer</label>
          <input
            list="consumer-list"
            value={consumerNo}
            onChange={(e) => setConsumerNo(e.target.value)}
            placeholder="Consumer number from upload"
            required
          />
          <datalist id="consumer-list">
            {bills.map((b) => (
              <option key={b.publicId} value={b.consumerNo}>
                {b.consumerName}
              </option>
            ))}
          </datalist>
        </div>

        {selected && (
          <div className="muted">
            <div><strong>{selected.consumerName}</strong></div>
            <div>Amount: {selected.amount}</div>
            <div>After due: {selected.amountAfterDueDate ?? '—'}</div>
            <div>Due: {selected.dueDate ?? '—'}</div>
          </div>
        )}

        <div className="btn-row" style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap' }}>
          <button className="btn btn-primary" type="submit" disabled={loading || !selected}>
            Pay
          </button>
          <button className="btn btn-ghost" type="button" disabled={loading || !selected} onClick={() => void submitAction('SKIP')}>
            Skip
          </button>
          <button className="btn btn-ghost" type="button" disabled={loading || !companyId} onClick={() => void submitAction('FINISH')}>
            Finish
          </button>
        </div>
      </form>
    </div>
  );
}
