import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../components/PageHeader';
import { api } from '../lib/api';
import { useAuth } from '../auth/AuthContext';

type XpinRow = {
  publicId: string;
  pinCode: string;
  name: string;
  phone: string;
  email: string;
  amount: number;
  validUntil: string;
  status: string;
  createdAt: string;
};

export function CorporateClientPage() {
  const { session } = useAuth();
  const [list, setList] = useState<XpinRow[]>([]);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [amount, setAmount] = useState('');
  const [validityDays, setValidityDays] = useState('7');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const load = useCallback(async () => {
    if (!session?.token) return;
    try {
      const rows = await api<XpinRow[]>('/api/corporate/xpins', { token: session.token });
      setList(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load XPins');
    }
  }, [session?.token]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const created = await api<XpinRow>('/api/corporate/xpins', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          name: name.trim(),
          phone: phone.trim(),
          email: email.trim(),
          amount: Number(amount),
          validityDays: Number(validityDays),
        }),
      });
      setOk(`XPin ${created.pinCode} generated and sent to ${created.email}`);
      setName('');
      setPhone('');
      setEmail('');
      setAmount('');
      setValidityDays('7');
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Generate failed');
    } finally {
      setLoading(false);
    }
  }

  if (!session?.token) {
    return (
      <div className="page">
        <PageHeader title="Corporate Client" subtitle="Generate XPin for branch visits" />
        <p className="muted">Please <Link to="/login">login</Link> first.</p>
      </div>
    );
  }

  return (
    <div className="page desk-page">
      <PageHeader
        title="Generate XPin"
        subtitle="Issue a branch visit PIN — emailed to the visitor. Branch verifies; no auto deduct."
      />
      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-success">{ok}</div>}

      <div className="desk-split desk-split-wide">
        <form className="desk-card desk-card-accent" onSubmit={onSubmit}>
          <p className="desk-kicker">New PIN</p>
          <h3 className="desk-card-title">Visitor details</h3>
          <p className="muted desk-card-lead">PIN is emailed instantly for presentation at the branch counter.</p>

          <div className="desk-form-grid">
            <div className="form-row">
              <label htmlFor="xpin-name">Name</label>
              <input id="xpin-name" value={name} onChange={(e) => setName(e.target.value)} required />
            </div>
            <div className="form-row">
              <label htmlFor="xpin-phone">Phone</label>
              <input id="xpin-phone" value={phone} onChange={(e) => setPhone(e.target.value)} required />
            </div>
            <div className="form-row desk-span-2">
              <label htmlFor="xpin-email">Email</label>
              <input id="xpin-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="form-row">
              <label htmlFor="xpin-amount">Amount</label>
              <input
                id="xpin-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
              />
            </div>
            <div className="form-row">
              <label htmlFor="xpin-days">Validity (days)</label>
              <input
                id="xpin-days"
                type="number"
                min="1"
                max="365"
                value={validityDays}
                onChange={(e) => setValidityDays(e.target.value)}
                required
              />
            </div>
          </div>

          <button className="btn btn-primary desk-btn-block" type="submit" disabled={loading}>
            {loading ? 'Generating…' : 'Generate & email XPin'}
          </button>
        </form>

        <div className="desk-card">
          <div className="desk-list-head">
            <div>
              <p className="desk-kicker">History</p>
              <h3 className="desk-card-title">Recent XPins</h3>
            </div>
            <span className="desk-count">{list.length}</span>
          </div>
          {list.length === 0 ? (
            <p className="muted">No XPins yet — generate one to get started.</p>
          ) : (
            <ul className="desk-list">
              {list.map((r) => (
                <li key={r.publicId} className="desk-list-item">
                  <div>
                    <code className="desk-pin-code">{r.pinCode}</code>
                    <strong>{r.name}</strong>
                    <span className="muted">{r.email}</span>
                  </div>
                  <div className="desk-list-meta">
                    <span className="desk-amount">{Number(r.amount).toLocaleString()}</span>
                    <span className={`desk-badge desk-badge-${r.status === 'ACTIVE' ? 'ok' : 'muted'}`}>
                      {r.status}
                    </span>
                    <span className="muted">{new Date(r.validUntil).toLocaleDateString()}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
