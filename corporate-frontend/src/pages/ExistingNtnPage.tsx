import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';

type AccountDto = {
  accountNo: string;
  accountTitle: string;
  iban?: string;
  accountType: string;
};

type NtnLookup = {
  ntnNumber: string;
  legalName: string;
  entityType: string;
  taxCountry: string;
  registeredAddress?: string;
  natureOfBusiness?: string;
  contactPhone?: string;
  contactEmail?: string;
  accounts: AccountDto[];
};

export function ExistingNtnPage() {
  const nav = useNavigate();
  const [ntn, setNtn] = useState('1234567-8');
  const [lookup, setLookup] = useState<NtnLookup | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function doLookup(e: FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const data = await api<NtnLookup>('/api/public/registration/ntn/lookup', {
        method: 'POST',
        body: JSON.stringify({ ntnNumber: ntn }),
      });
      setLookup(data);
      setSelected(data.accounts.map((a) => a.accountNo));
      setFullName(data.legalName);
      setPhone(data.contactPhone || '');
      setEmail(data.contactEmail || '');
    } catch (err) {
      setLookup(null);
      setError(err instanceof Error ? err.message : 'Lookup failed');
    } finally {
      setLoading(false);
    }
  }

  function toggle(no: string) {
    setSelected((prev) => (prev.includes(no) ? prev.filter((x) => x !== no) : [...prev, no]));
  }

  async function startSignup(e: FormEvent) {
    e.preventDefault();
    if (!lookup) return;
    setError('');
    setLoading(true);
    try {
      const res = await api<{ email: string; devOtpHint?: string }>('/api/auth/signup', {
        method: 'POST',
        body: JSON.stringify({
          partyType: 'MERCHANT',
          fullName,
          businessName: lookup.legalName,
          email,
          phone,
          ntnNumber: lookup.ntnNumber,
          entityType: lookup.entityType,
          taxCountry: lookup.taxCountry,
          registeredAddress: lookup.registeredAddress,
          natureOfBusiness: lookup.natureOfBusiness,
          selectedAccountNos: selected,
        }),
      });
      nav('/verify-otp', { state: { email: res.email, devOtpHint: res.devOtpHint } });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Signup failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page">
      <div className="container">
        <div className="badge">EXISTING CUSTOMER</div>
        <h2 style={{ fontFamily: 'var(--font-display)' }}>NTN lookup</h2>
        <p className="muted">Mock directory — try 1234567-8, 9876543-1, or 5555555-5.</p>

        <div className="panel" style={{ marginBottom: '1rem' }}>
          <form className="form-grid" onSubmit={doLookup}>
            <div className="form-row">
              <label>NTN number</label>
              <input value={ntn} onChange={(e) => setNtn(e.target.value)} required />
            </div>
            <div className="actions">
              <button className="btn btn-primary" disabled={loading} type="submit">Look up</button>
              <Link className="btn btn-ghost" to="/register">Back</Link>
            </div>
          </form>
          {error && <div className="alert alert-error">{error}</div>}
        </div>

        {lookup && (
          <div className="panel">
            <h3 style={{ marginTop: 0 }}>{lookup.legalName}</h3>
            <p className="muted">
              {lookup.entityType.replaceAll('_', ' ')} · {lookup.ntnNumber} · {lookup.registeredAddress}
            </p>
            <h4>Accounts</h4>
            <div style={{ display: 'grid', gap: '0.5rem', marginBottom: '1rem' }}>
              {lookup.accounts.map((a) => (
                <label key={a.accountNo} style={{ display: 'flex', gap: '0.75rem', padding: '0.75rem', border: '1px solid var(--border)', borderRadius: 10 }}>
                  <input type="checkbox" checked={selected.includes(a.accountNo)} onChange={() => toggle(a.accountNo)} />
                  <span>
                    <strong>{a.accountTitle}</strong>
                    <div className="muted">{a.accountNo} · {a.iban} · {a.accountType}</div>
                  </span>
                </label>
              ))}
            </div>
            <form className="form-grid" onSubmit={startSignup}>
              <div className="form-row">
                <label>Authorized person / contact name</label>
                <input value={fullName} onChange={(e) => setFullName(e.target.value)} required />
              </div>
              <div className="form-row">
                <label>Mobile</label>
                <input value={phone} onChange={(e) => setPhone(e.target.value)} required />
              </div>
              <div className="form-row">
                <label>Email (OTP)</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
              </div>
              <button className="btn btn-primary" disabled={loading} type="submit">
                {loading ? 'Creating…' : 'Continue — send OTP'}
              </button>
            </form>
          </div>
        )}
      </div>
    </div>
  );
}
