import { type FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { BranchRecent } from './BranchRecent';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import { buildReceiptFromLive, type PaymentReceiptModel } from '../../lib/paymentReceipt';
import { isLiveOk, type DfsTxnResponse } from '../../lib/liveTransfers';

export function BranchFtPage() {
  const { session } = useAuth();
  const [accountNo, setAccountNo] = useState('');
  const [amount, setAmount] = useState('');
  const [narration, setNarration] = useState('Fund transfer');
  const [mpin, setMpin] = useState('');
  const [title, setTitle] = useState('');
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [loading, setLoading] = useState(false);
  const [receipt, setReceipt] = useState<PaymentReceiptModel | null>(null);

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="Fund transfer" subtitle="Branch desk" />
        <p className="muted">Login as branch user. <Link to="/login">Login</Link></p>
      </div>
    );
  }

  async function initiate(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setReady(false);
    setTitle('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ft/initiate', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ accountNo, amount, accountType: 'W' }),
      });
      if (!isLiveOk(r)) {
        setError(r.messages || 'Fund transfer initiate failed');
        return;
      }
      const data = (r.data && typeof r.data === 'object' ? r.data : {}) as {
        beneficiaryTitle?: string;
        title?: string;
        accountTitle?: string;
      };
      setTitle(data.beneficiaryTitle || data.title || data.accountTitle || '');
      setReady(true);
      setOk(r.messages || 'Enter the payer wallet MPIN to confirm.');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Fund transfer initiate failed');
    } finally {
      setLoading(false);
    }
  }

  async function confirm() {
    if (!session?.token) return;
    setError('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ft/confirm', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          accountNo,
          amount,
          accountType: 'W',
          mpin,
          narration,
          beneficiaryName: title || undefined,
        }),
      });
      if (!isLiveOk(r)) {
        setError(r.messages || 'Fund transfer failed');
        return;
      }
      setReceipt(buildReceiptFromLive(r, {
        product: 'FT',
        amount,
        accountNumber: accountNo,
        beneficiaryName: title || undefined,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Fund transfer failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page desk-page">
      <PageHeader title="Fund transfer" subtitle="Branch wallet → another DFS wallet. Payer MPIN confirms." />
      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-success">{ok}</div>}
      <div className="desk-split">
      <form className="desk-card desk-card-accent" onSubmit={initiate}>
        <p className="desk-kicker">Step 1</p>
        <h3 className="desk-card-title">Beneficiary</h3>
        <div className="form-row">
          <label>Beneficiary mobile</label>
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
        <button className="btn btn-ghost desk-btn-block" type="submit" disabled={loading}>Initiate</button>
        {ready && (
          <>
            <p className="desk-kicker" style={{ marginTop: '1.25rem' }}>Step 2</p>
            <h3 className="desk-card-title">{title || 'Confirm'}</h3>
            <div className="form-row">
              <label>Payer MPIN</label>
              <input value={mpin} onChange={(e) => setMpin(e.target.value)} type="password" inputMode="numeric" required />
            </div>
            <button className="btn btn-primary desk-btn-block" type="button" disabled={loading || mpin.length < 4} onClick={() => void confirm()}>
              Send
            </button>
          </>
        )}
        <Link className="desk-back-link" to="/branch">← Branch home</Link>
      </form>
      <BranchRecent type="FT" refreshKey={receipt?.portalTxnRef} />
      </div>
      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
