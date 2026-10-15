import { useEffect, useState } from 'react';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import {
  buildReceiptFromBranchHistory,
  type BranchHistoryRow,
  type PaymentReceiptModel,
} from '../../lib/paymentReceipt';

type Props = {
  type: string;
  refreshKey?: string;
};

function money(amount: BranchHistoryRow['amount']): string {
  if (amount === null || amount === undefined || amount === '') return '—';
  const n = typeof amount === 'number' ? amount : Number(amount);
  if (Number.isNaN(n)) return String(amount);
  return `PKR ${n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function BranchRecent({ type, refreshKey }: Props) {
  const { session } = useAuth();
  const [rows, setRows] = useState<BranchHistoryRow[]>([]);
  const [receipt, setReceipt] = useState<PaymentReceiptModel | null>(null);

  useEffect(() => {
    if (!session?.token) return;
    api<BranchHistoryRow[]>(`/api/branch/history?type=${encodeURIComponent(type)}`, { token: session.token })
      .then((list) => setRows(list.slice(0, 3)))
      .catch(() => setRows([]));
  }, [session?.token, type, refreshKey]);

  return (
    <aside className="desk-card">
      <p className="desk-kicker">Recent</p>
      <h3 className="desk-card-title">Last 3</h3>
      {rows.length === 0 && <p className="muted">No transactions yet.</p>}
      {rows.length > 0 && (
        <ul className="desk-recent">
          {rows.map((row) => (
            <li key={row.reference || `${row.at}-${row.toAccount}`}>
              <button type="button" className="desk-recent-btn" onClick={() => setReceipt(buildReceiptFromBranchHistory(row))}>
                <span className="desk-recent-top">
                  <strong>{money(row.amount)}</strong>
                  <span className={row.statusLabel === 'Success' ? 'desk-recent-ok' : 'muted'}>{row.statusLabel || '—'}</span>
                </span>
                <span className="muted">{row.toAccount || row.detail || '—'}</span>
                <span className="desk-recent-ref mono">{row.reference || 'Receipt'}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}
    </aside>
  );
}
