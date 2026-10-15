import { type FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { BranchRecent } from './BranchRecent';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import { buildReceiptFromLive, type PaymentReceiptModel } from '../../lib/paymentReceipt';
import { isLiveOk, type DfsTxnResponse } from '../../lib/liveTransfers';

export function BranchCashInPage() {
  const { session } = useAuth();
  const [accountNo, setAccountNo] = useState('');
  const [amount, setAmount] = useState('');
  const [narration, setNarration] = useState('Cash in');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [receipt, setReceipt] = useState<PaymentReceiptModel | null>(null);

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="Cash In" subtitle="Branch desk" />
        <p className="muted">Login as branch user. <Link to="/login">Login</Link></p>
      </div>
    );
  }

  async function execute(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/branch/cash-in', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ accountNo, amount, narration }),
      });
      if (!isLiveOk(r)) {
        setError(r.messages || 'Cash in failed');
        return;
      }
      const data = (r.data && typeof r.data === 'object' ? r.data : {}) as { beneficiaryName?: string };
      setReceipt(buildReceiptFromLive(r, {
        product: 'CASH_IN',
        amount,
        beneficiaryName: data.beneficiaryName,
        accountNumber: accountNo,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cash in failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page desk-page">
      <PageHeader title="Cash In" subtitle="Saved branch wallet pays the customer. Wallet MPIN is used. No customer OTP." />
      {error && <div className="alert alert-error">{error}</div>}
      <div className="desk-split">
      <form className="desk-card desk-card-accent" onSubmit={execute}>
        <p className="desk-kicker">Customer</p>
        <h3 className="desk-card-title">Pay this customer</h3>
        <div className="form-row">
          <label>Customer mobile</label>
          <input value={accountNo} onChange={(e) => setAccountNo(e.target.value)} required />
        </div>
        <div className="form-row">
          <label>Amount</label>
          <input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" required />
        </div>
        <div className="form-row">
          <label>Narration</label>
          <input value={narration} onChange={(e) => setNarration(e.target.value)} />
        </div>
        <button className="btn btn-primary desk-btn-block" type="submit" disabled={loading}>
          {loading ? 'Working…' : 'Cash in'}
        </button>
        <Link className="desk-back-link" to="/branch">← Branch home</Link>
      </form>
      <BranchRecent type="CASH_IN" refreshKey={receipt?.portalTxnRef} />
      </div>
      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
