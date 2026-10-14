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
    <div className="page">
      <PageHeader title="Enter XPin" subtitle="Check XPin exists → show company info (no deduct)" />
      {error && <div className="alert alert-error">{error}</div>}

      <form className="panel form-grid" onSubmit={onSubmit}>
        <div className="form-row">
          <label>XPin</label>
          <input value={xpin} onChange={(e) => setXpin(e.target.value)} required placeholder="6-digit PIN" />
        </div>
        <button className="btn btn-primary" type="submit" disabled={loading}>
          {loading ? 'Checking…' : 'Check XPin'}
        </button>
      </form>

      {result && (
        <div className="panel" style={{ marginTop: '1.5rem' }}>
          <h3 className="panel-title">Company info</h3>
          <p className="muted">{result.message}</p>
          <dl className="detail-list">
            <dt>Company</dt><dd>{result.companyName}</dd>
            <dt>NTN</dt><dd>{result.companyNtn || '—'}</dd>
            <dt>Company email</dt><dd>{result.companyEmail}</dd>
            <dt>Company phone</dt><dd>{result.companyPhone}</dd>
            <dt>Person</dt><dd>{result.personName}</dd>
            <dt>Person phone / email</dt><dd>{result.phone} / {result.email}</dd>
            <dt>Amount</dt><dd>{result.amount}</dd>
            <dt>Valid until</dt><dd>{new Date(result.validUntil).toLocaleString()}</dd>
          </dl>
          <button className="btn btn-primary" type="button" onClick={() => { setResult(null); setXpin(''); }}>
            OK
          </button>
        </div>
      )}
    </div>
  );
}
