import { type FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import { buildReceiptFromLive, type PaymentReceiptModel } from '../../lib/paymentReceipt';
import type { DfsTxnResponse } from '../../lib/liveTransfers';

export function BranchCashOutPage() {
  const { session } = useAuth();
  const [accountNo, setAccountNo] = useState('');
  const [amount, setAmount] = useState('');
  const [narration, setNarration] = useState('Cash out');
  const [otpId, setOtpId] = useState('');
  const [otpPin, setOtpPin] = useState('');
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [loading, setLoading] = useState(false);
  const [receipt, setReceipt] = useState<PaymentReceiptModel | null>(null);

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="Cash Out" subtitle="Branch desk" />
        <p className="muted">Login as branch user. <Link to="/login">Login</Link></p>
      </div>
    );
  }

  async function sendOtp(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/branch/cash-out/otp', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ mobileNumber: accountNo }),
      });
      const data = (r.data && typeof r.data === 'object' ? r.data : {}) as { otpId?: string; otpID?: string };
      const id = data.otpId || data.otpID || '';
      if (!id) {
        setError(r.messages || 'OTP was not issued');
        return;
      }
      setOtpId(id);
      setOk('OTP sent to the customer. Enter the code they received.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'OTP failed');
    } finally {
      setLoading(false);
    }
  }

  async function execute() {
    if (!session?.token) return;
    setError('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/branch/cash-out', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ accountNo, amount, narration, otpId, otpPin }),
      });
      setReceipt(buildReceiptFromLive(r, {
        product: 'CASH_OUT',
        amount,
        accountNumber: accountNo,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Cash out failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page desk-page">
      <PageHeader title="Cash Out" subtitle="Customer wallet → branch. OTP required before success." />
      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-success">{ok}</div>}
      <form className="desk-card desk-card-accent" onSubmit={sendOtp}>
        <div className="form-row">
          <label>Customer mobile</label>
          <input value={accountNo} onChange={(e) => setAccountNo(e.target.value)} required />
        </div>
        <button className="btn btn-ghost" type="submit" disabled={loading}>Send OTP</button>
        <div className="form-row">
          <label>OTP</label>
          <input value={otpPin} onChange={(e) => setOtpPin(e.target.value)} inputMode="numeric" />
        </div>
        <div className="form-row">
          <label>Amount</label>
          <input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" required />
        </div>
        <div className="form-row">
          <label>Narration</label>
          <input value={narration} onChange={(e) => setNarration(e.target.value)} />
        </div>
        <button className="btn btn-primary" type="button" disabled={loading || !otpId || !otpPin} onClick={() => void execute()}>
          Cash out
        </button>
        <Link className="desk-back-link" to="/branch">← Branch home</Link>
      </form>
      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
