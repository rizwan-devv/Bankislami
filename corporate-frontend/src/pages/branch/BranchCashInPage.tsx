import { type FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import { buildReceiptFromLive, type PaymentReceiptModel } from '../../lib/paymentReceipt';
import type { DfsTxnResponse } from '../../lib/liveTransfers';

type Inquiry = { accountNo?: string; name?: string; dob?: string };

export function BranchCashInPage() {
  const { session } = useAuth();
  const [accountNo, setAccountNo] = useState('');
  const [amount, setAmount] = useState('');
  const [narration, setNarration] = useState('Cash in');
  const [inquiry, setInquiry] = useState<Inquiry | null>(null);
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

  async function lookup(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setInquiry(null);
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/branch/cash-in/inquiry', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ accountNo }),
      });
      const data = (r.data && typeof r.data === 'object' ? r.data : {}) as Inquiry;
      if (r.responsecode && r.responsecode !== '000' && r.responsecode !== '00') {
        setError(r.messages || 'Inquiry failed');
        return;
      }
      setInquiry({
        accountNo: data.accountNo || accountNo,
        name: data.name,
        dob: data.dob,
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Inquiry failed');
    } finally {
      setLoading(false);
    }
  }

  async function execute() {
    if (!session?.token || !inquiry) return;
    setError('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/branch/cash-in', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          accountNo: inquiry.accountNo,
          amount,
          narration,
          depositorName: inquiry.name || '',
          depositorDob: inquiry.dob || '',
          depositorNid: '',
        }),
      });
      setReceipt(buildReceiptFromLive(r, {
        product: 'CASH_IN',
        amount,
        beneficiaryName: inquiry.name,
        accountNumber: inquiry.accountNo,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cash in failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page desk-page">
      <PageHeader title="Cash In" subtitle="Branch wallet → customer. No customer OTP." />
      {error && <div className="alert alert-error">{error}</div>}
      <div className="desk-split">
        <form className="desk-card desk-card-accent" onSubmit={lookup}>
          <p className="desk-kicker">Customer</p>
          <h3 className="desk-card-title">Account inquiry</h3>
          <div className="form-row">
            <label>Customer account / mobile</label>
            <input value={accountNo} onChange={(e) => setAccountNo(e.target.value)} required />
          </div>
          <button className="btn btn-primary desk-btn-block" type="submit" disabled={loading}>
            {loading ? 'Working…' : 'Inquiry'}
          </button>
          <Link className="desk-back-link" to="/branch">← Branch home</Link>
        </form>
        {inquiry && (
          <div className="desk-card">
            <h3 className="desk-card-title">{inquiry.name || 'Customer'}</h3>
            <dl className="desk-facts">
              <div><dt>Account</dt><dd>{inquiry.accountNo}</dd></div>
              <div><dt>DOB</dt><dd>{inquiry.dob || '—'}</dd></div>
            </dl>
            <div className="form-row">
              <label>Amount</label>
              <input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" required />
            </div>
            <div className="form-row">
              <label>Narration</label>
              <input value={narration} onChange={(e) => setNarration(e.target.value)} />
            </div>
            <button className="btn btn-primary" type="button" disabled={loading || !amount} onClick={() => void execute()}>
              Cash in
            </button>
          </div>
        )}
      </div>
      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
