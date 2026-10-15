import { type FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { BranchRecent } from './BranchRecent';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import { buildReceiptFromLive, type PaymentReceiptModel } from '../../lib/paymentReceipt';
import { banksFromResponse, type DfsTxnResponse, type IbftBank } from '../../lib/liveTransfers';

export function BranchIbftPage() {
  const { session } = useAuth();
  const [banks, setBanks] = useState<IbftBank[]>([]);
  const [accountNo, setAccountNo] = useState('');
  const [bankImd, setBankImd] = useState('');
  const [amount, setAmount] = useState('');
  const [purpose, setPurpose] = useState('Payment');
  const [title, setTitle] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [receipt, setReceipt] = useState<PaymentReceiptModel | null>(null);

  useEffect(() => {
    if (!session?.token) return;
    void api<DfsTxnResponse>('/api/transfers/live/ibft/banks', { token: session.token })
      .then((r) => setBanks(banksFromResponse(r)))
      .catch((e) => setError(e instanceof Error ? e.message : 'Bank list failed'));
  }, [session?.token]);

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="IBFT" subtitle="Branch desk" />
        <p className="muted">Login as branch user. <Link to="/login">Login</Link></p>
      </div>
    );
  }

  async function fetchTitle(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setTitle('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ibft/title-fetch', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          beneficiaryAccountNo: accountNo,
          beneficiaryBankImd: bankImd,
          amount,
        }),
      });
      const data = (r.data && typeof r.data === 'object' ? r.data : {}) as { beneficiaryName?: string; accountTitle?: string };
      if (r.responsecode && r.responsecode !== '000') {
        setError(r.messages || 'Title fetch failed');
        return;
      }
      setTitle(data.beneficiaryName || data.accountTitle || 'Title fetched');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Title fetch failed');
    } finally {
      setLoading(false);
    }
  }

  async function pay() {
    if (!session?.token) return;
    setLoading(true);
    setError('');
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ibft/advice', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          beneficiaryAccountNo: accountNo,
          beneficiaryBankImd: bankImd,
          amount,
          purposeOfPayment: purpose,
          beneficiaryName: title,
        }),
      });
      const bank = banks.find((b) => String(b.bankImd) === bankImd);
      setReceipt(buildReceiptFromLive(r, {
        product: 'IBFT',
        amount,
        accountNumber: accountNo,
        beneficiaryName: title,
        bankImd,
        bankName: bank?.bankName ? String(bank.bankName) : undefined,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'IBFT failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page desk-page">
      <PageHeader title="Single IBFT" subtitle="Portal live IBFT — title fetch, then advice." />
      {error && <div className="alert alert-error">{error}</div>}
      <div className="desk-split">
      <form className="desk-card desk-card-accent" onSubmit={fetchTitle}>
        <div className="form-row">
          <label>Bank</label>
          <select value={bankImd} onChange={(e) => setBankImd(e.target.value)} required>
            <option value="">Select bank…</option>
            {banks.map((b) => (
              <option key={String(b.bankImd)} value={String(b.bankImd)}>{b.bankName || b.bankImd}</option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label>Beneficiary account</label>
          <input value={accountNo} onChange={(e) => setAccountNo(e.target.value)} required />
        </div>
        <div className="form-row">
          <label>Amount</label>
          <input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" required />
        </div>
        <div className="form-row">
          <label>Purpose</label>
          <input value={purpose} onChange={(e) => setPurpose(e.target.value)} required />
        </div>
        <div className="desk-actions">
          <button className="btn btn-ghost" type="submit" disabled={loading}>Title fetch</button>
          {title && <p className="muted">Title: <strong>{title}</strong></p>}
          <button className="btn btn-primary" type="button" disabled={loading || !title} onClick={() => void pay()}>
            Send IBFT
          </button>
        </div>
        <Link className="desk-back-link" to="/branch">← Branch home</Link>
      </form>
      <BranchRecent type="IBFT" refreshKey={receipt?.portalTxnRef} />
      </div>
      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
