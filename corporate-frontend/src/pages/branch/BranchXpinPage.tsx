import { type FormEvent, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';

type VerifyResult = {
  valid: boolean;
  pinCode: string;
  personName: string;
  phone: string;
  email: string;
  amount: number;
  validUntil: string;
  companyPublicId: string;
  companyName: string;
  companyEmail: string;
  companyPhone: string;
  companyNtn?: string | null;
  message: string;
};

export function BranchXpinPage() {
  const { session } = useAuth();
  const [xpin, setXpin] = useState('');
  const [result, setResult] = useState<VerifyResult | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setResult(null);
    setLoading(true);
    try {
      const res = await api<VerifyResult>('/api/branch/xpin/verify', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({ xpin: xpin.trim() }),
      });
      setResult(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Verify failed');
    } finally {
      setLoading(false);
    }
  }

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="Branch — Enter XPin" subtitle="Branch desk only" />
        <p className="muted">Login as branch user. <Link to="/login">Login</Link></p>
      </div>
    );
  }

  return (
    <div className="page desk-page">
      <PageHeader
        title="Enter XPin"
        subtitle="Confirm the visitor PIN — company info only, no funds deducted"
      />
      {error && <div className="alert alert-error">{error}</div>}

      <div className="desk-split">
        <form className="desk-card desk-card-accent" onSubmit={onSubmit}>
          <p className="desk-kicker">Verification</p>
          <h3 className="desk-card-title">Visitor XPin</h3>
          <p className="muted desk-card-lead">Enter the 6-digit code from the corporate email / SMS.</p>
          <div className="form-row">
            <label htmlFor="branch-xpin">XPin</label>
            <input
              id="branch-xpin"
              className="desk-pin-input"
              value={xpin}
              onChange={(e) => setXpin(e.target.value.replace(/\D/g, '').slice(0, 8))}
              required
              inputMode="numeric"
              autoComplete="one-time-code"
              placeholder="••••••"
            />
          </div>
          <button className="btn btn-primary desk-btn-block" type="submit" disabled={loading || xpin.length < 4}>
            {loading ? 'Checking…' : 'Check XPin'}
          </button>
          <Link className="desk-back-link" to="/branch">← Branch home</Link>
        </form>

        {result && (
          <div className="desk-card desk-result-card">
            <div className="desk-result-banner">
              <span className="desk-status-dot" />
              Valid · no deduct
            </div>
            <h3 className="desk-card-title">{result.companyName}</h3>
            <p className="muted">{result.message}</p>
            <dl className="desk-facts">
              <div><dt>NTN</dt><dd>{result.companyNtn || '—'}</dd></div>
              <div><dt>Company email</dt><dd>{result.companyEmail}</dd></div>
              <div><dt>Company phone</dt><dd>{result.companyPhone}</dd></div>
              <div><dt>Visitor</dt><dd>{result.personName}</dd></div>
              <div><dt>Contact</dt><dd>{result.phone} · {result.email}</dd></div>
              <div><dt>Amount</dt><dd className="desk-amount">{Number(result.amount).toLocaleString()}</dd></div>
              <div><dt>Valid until</dt><dd>{new Date(result.validUntil).toLocaleString()}</dd></div>
            </dl>
            <button
              className="btn btn-primary"
              type="button"
              onClick={() => { setResult(null); setXpin(''); }}
            >
              Done — next visitor
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
