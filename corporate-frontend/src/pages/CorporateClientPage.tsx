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
      setOk(`XPin ${created.pinCode} generated and sent to ${created.email} / ${created.phone}`);
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
    <div className="page">
      <PageHeader title="Corporate Client" subtitle="Generate XPin — sent to email / phone. Branch verifies; no auto deduct." />
      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-success">{ok}</div>}

      <form className="panel form-grid" onSubmit={onSubmit}>
        <div className="form-row">
          <label>Name</label>
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </div>
        <div className="form-row">
          <label>Phone</label>
          <input value={phone} onChange={(e) => setPhone(e.target.value)} required />
        </div>
        <div className="form-row">
          <label>Email</label>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </div>
        <div className="form-row">
          <label>Amount</label>
          <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
        </div>
        <div className="form-row">
          <label>Validity (days)</label>
          <input type="number" min="1" max="365" value={validityDays} onChange={(e) => setValidityDays(e.target.value)} required />
        </div>
        <button className="btn btn-primary" type="submit" disabled={loading}>
          {loading ? 'Generating…' : 'Generate'}
        </button>
      </form>

      <div className="panel" style={{ marginTop: '1.5rem' }}>
        <h3 className="panel-title">Recent XPins</h3>
        {list.length === 0 ? (
          <p className="muted">No XPins yet.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th>PIN</th>
                  <th>Name</th>
                  <th>Amount</th>
                  <th>Valid until</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {list.map((r) => (
                  <tr key={r.publicId}>
                    <td><code>{r.pinCode}</code></td>
                    <td>{r.name}</td>
                    <td>{r.amount}</td>
                    <td>{new Date(r.validUntil).toLocaleString()}</td>
                    <td>{r.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
