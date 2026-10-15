import { type FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import { buildReceiptFromLive, type PaymentReceiptModel } from '../../lib/paymentReceipt';
import { billersFromResponse, type DfsTxnResponse, type UbpBillerLive } from '../../lib/liveTransfers';

export function BranchLiveBillPage() {
  const { session } = useAuth();
  const [billers, setBillers] = useState<UbpBillerLive[]>([]);
  const [companyCode, setCompanyCode] = useState('');
  const [consumerNo, setConsumerNo] = useState('');
  const [amount, setAmount] = useState('');
  const [title, setTitle] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [receipt, setReceipt] = useState<PaymentReceiptModel | null>(null);

  useEffect(() => {
    if (!session?.token) return;
    void api<DfsTxnResponse>('/api/transfers/live/ubp/billers', { token: session.token })
      .then((r) => setBillers(billersFromResponse(r)))
      .catch((e) => setError(e instanceof Error ? e.message : 'Biller list failed'));
  }, [session?.token]);

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="Bill payment" subtitle="Branch desk" />
        <p className="muted">Login as branch user. <Link to="/login">Login</Link></p>
      </div>
    );
  }

  async function inquire(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setLoading(true);
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ubp/inquiry', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ utilityCompanyCode: companyCode, consumerNo }),
      });
      const data = (r.data && typeof r.data === 'object' ? r.data : {}) as {
        amount?: string; billAmount?: string; consumerName?: string;
      };
      if (r.responsecode && r.responsecode !== '000') {
        setError(r.messages || 'Inquiry failed');
        return;
      }
      setAmount(String(data.amount || data.billAmount || amount));
      setTitle(data.consumerName || consumerNo);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Inquiry failed');
    } finally {
      setLoading(false);
    }
  }

  async function pay() {
    if (!session?.token) return;
    setLoading(true);
    setError('');
    try {
      const r = await api<DfsTxnResponse>('/api/transfers/live/ubp/pay', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          utilityCompanyCode: companyCode,
          consumerNo,
          amount,
          beneficiaryName: title,
        }),
      });
      const biller = billers.find((b) => String(b.code) === companyCode);
      setReceipt(buildReceiptFromLive(r, {
        product: 'UBP',
        amount,
        utilityCompanyCode: companyCode,
        utilityCompanyName: biller?.name ? String(biller.name) : companyCode,
        consumerNo,
      }));
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Bill pay failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page desk-page">
      <PageHeader title="Single bill payment" subtitle="Portal live bill inquiry, then pay." />
      {error && <div className="alert alert-error">{error}</div>}
      <form className="desk-card desk-card-accent desk-flow" onSubmit={inquire}>
        <div className="form-row">
          <label>Biller</label>
          <select value={companyCode} onChange={(e) => setCompanyCode(e.target.value)} required>
            <option value="">Select biller…</option>
            {billers.map((b) => (
              <option key={String(b.code || b.name)} value={String(b.code || '')}>{b.name || b.code}</option>
            ))}
          </select>
        </div>
        <div className="form-row">
          <label>Consumer number</label>
          <input value={consumerNo} onChange={(e) => setConsumerNo(e.target.value)} required />
        </div>
        <button className="btn btn-ghost desk-btn-block" type="submit" disabled={loading}>Inquiry</button>
        <div className="form-row">
          <label>Amount</label>
          <input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" required />
        </div>
        <button className="btn btn-primary desk-btn-block" type="button" disabled={loading || !amount} onClick={() => void pay()}>
          Pay bill
        </button>
        <Link className="desk-back-link" to="/branch">← Branch home</Link>
      </form>
      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
