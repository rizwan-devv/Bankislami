import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import {
  buildReceiptFromBranchHistory,
  type BranchHistoryRow,
  type PaymentReceiptModel,
} from '../../lib/paymentReceipt';

type HistoryRow = BranchHistoryRow;

function money(amount: HistoryRow['amount']): string {
  if (amount === null || amount === undefined || amount === '') return '—';
  const n = typeof amount === 'number' ? amount : Number(amount);
  if (Number.isNaN(n)) return String(amount);
  return `PKR ${n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function when(at?: string): string {
  if (!at) return '—';
  const d = new Date(at);
  if (Number.isNaN(d.getTime())) return at;
  return d.toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function BranchHistoryPage() {
  const { session } = useAuth();
  const [rows, setRows] = useState<HistoryRow[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [receipt, setReceipt] = useState<PaymentReceiptModel | null>(null);

  useEffect(() => {
    if (!session?.token || session.role !== 'BRANCH_USER') return;
    setLoading(true);
    api<HistoryRow[]>('/api/branch/history', { token: session.token })
      .then(setRows)
      .catch((err) => setError(err instanceof Error ? err.message : 'Could not load history'))
      .finally(() => setLoading(false));
  }, [session?.token, session?.role]);

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="History" subtitle="Branch desk" />
        <p className="muted">Login as branch user. <Link to="/login">Login</Link></p>
      </div>
    );
  }

  return (
    <div className="page desk-page">
      <PageHeader title="History" subtitle="Recent cash in, cash out, transfers, and bills." />
      {error && <div className="alert alert-error">{error}</div>}
      <div className="desk-card">
        {loading && <p className="muted">Loading…</p>}
        {!loading && rows.length === 0 && <p className="muted">No transactions yet.</p>}
        {!loading && rows.length > 0 && (
          <div className="table-wrap desk-table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Time</th>
                  <th>Type</th>
                  <th>From</th>
                  <th>To</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Reference</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr
                    key={row.reference || `${row.at}-${row.toAccount}`}
                    className="desk-history-row"
                    onClick={() => setReceipt(buildReceiptFromBranchHistory(row))}
                  >
                    <td>{when(row.at)}</td>
                    <td>
                      {row.typeLabel || '—'}
                      {row.detail ? <div className="muted">{row.detail}</div> : null}
                    </td>
                    <td>{row.fromAccount || '—'}</td>
                    <td>{row.toAccount || '—'}</td>
                    <td>{money(row.amount)}</td>
                    <td>{row.statusLabel || '—'}</td>
                    <td className="mono">{row.reference || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Link className="desk-back-link" to="/branch">← Branch home</Link>
      </div>
      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
