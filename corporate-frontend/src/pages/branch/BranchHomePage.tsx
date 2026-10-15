import { type FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';

type Wallet = { configured?: boolean; mobile?: string; cnic?: string; imei?: string };

export function BranchHomePage() {
  const { session } = useAuth();
  const [mobile, setMobile] = useState('');
  const [cnic, setCnic] = useState('');
  const [mpin, setMpin] = useState('');
  const [imei, setImei] = useState('');
  const [walletMsg, setWalletMsg] = useState('');
  const [walletErr, setWalletErr] = useState('');

  useEffect(() => {
    if (!session?.token || session.role !== 'BRANCH_USER') return;
    void api<Wallet>('/api/branch/wallet', { token: session.token }).then((w) => {
      setMobile(w.mobile || '');
      setCnic(w.cnic || '');
      setImei(w.imei || '');
      if (w.configured) setWalletMsg('DFS wallet is saved. MPIN is kept on the server.');
    }).catch(() => { /* first visit */ });
  }, [session?.token, session?.role]);

  async function saveWallet(e: FormEvent) {
    e.preventDefault();
    if (!session?.token) return;
    setWalletErr('');
    setWalletMsg('');
    try {
      await api('/api/branch/wallet', {
        method: 'PUT',
        token: session.token,
        body: JSON.stringify({ mobile, cnic, mpin, imei }),
      });
      setMpin('');
      setWalletMsg('DFS wallet saved for cash-in and cash-out.');
    } catch (err) {
      setWalletErr(err instanceof Error ? err.message : 'Could not save wallet');
    }
  }

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="Branch" subtitle="Branch desk" />
        <p className="muted">Please <Link to="/login">login</Link> as a branch user.</p>
      </div>
    );
  }

  return (
    <div className="page desk-page">
      <PageHeader
        title="Branch desk"
        subtitle="Cash in, cash out, single IBFT, and bill payment"
      />
      {walletErr && <div className="alert alert-error">{walletErr}</div>}
      {walletMsg && <div className="alert alert-success">{walletMsg}</div>}
      <form className="desk-card" onSubmit={saveWallet} style={{ marginBottom: '1rem' }}>
        <p className="desk-kicker">DFS wallet</p>
        <h3 className="desk-card-title">Branch agent account</h3>
        <p className="muted desk-card-lead">Cash in pays the customer from this wallet. Cash out pays this wallet from the customer. Fund transfer, IBFT, and bills use it too.</p>
        <div className="form-row"><label>Mobile</label><input value={mobile} onChange={(e) => setMobile(e.target.value)} required /></div>
        <div className="form-row"><label>CNIC</label><input value={cnic} onChange={(e) => setCnic(e.target.value)} required /></div>
        <div className="form-row"><label>MPIN</label><input type="password" value={mpin} onChange={(e) => setMpin(e.target.value)} required placeholder="Stored for cash-in" /></div>
        <div className="form-row"><label>IMEI</label><input value={imei} onChange={(e) => setImei(e.target.value)} placeholder="Optional" /></div>
        <button className="btn btn-primary desk-btn-block" type="submit">Save wallet</button>
      </form>

      <div className="desk-hero">
        <div className="desk-hero-copy">
          <p className="desk-kicker">Counter operations</p>
          <h2 className="desk-hero-title">Serve corporates at the branch</h2>
          <p className="muted desk-hero-lead">
            Check an XPin to identify the company, or pay / skip bills from their uploaded file.
          </p>
        </div>
      </div>

      <div className="desk-tile-grid">
        <Link className="desk-tile" to="/branch/cash-in">
          <span className="desk-tile-icon" aria-hidden>↓</span>
          <span className="desk-tile-body">
            <strong>Cash In</strong>
            <span className="muted">Saved wallet pays the customer</span>
          </span>
          <span className="desk-tile-arrow" aria-hidden>→</span>
        </Link>
        <Link className="desk-tile" to="/branch/cash-out">
          <span className="desk-tile-icon" aria-hidden>↑</span>
          <span className="desk-tile-body">
            <strong>Cash Out</strong>
            <span className="muted">Customer pays this wallet</span>
          </span>
          <span className="desk-tile-arrow" aria-hidden>→</span>
        </Link>
        <Link className="desk-tile" to="/branch/ft">
          <span className="desk-tile-icon" aria-hidden>→</span>
          <span className="desk-tile-body">
            <strong>Fund transfer</strong>
            <span className="muted">Wallet to wallet · payer MPIN</span>
          </span>
          <span className="desk-tile-arrow" aria-hidden>→</span>
        </Link>
        <Link className="desk-tile" to="/branch/ibft">
          <span className="desk-tile-icon" aria-hidden>⇄</span>
          <span className="desk-tile-body">
            <strong>Single IBFT</strong>
            <span className="muted">Title fetch and advice</span>
          </span>
          <span className="desk-tile-arrow" aria-hidden>→</span>
        </Link>
        <Link className="desk-tile" to="/branch/live-bill">
          <span className="desk-tile-icon" aria-hidden>▤</span>
          <span className="desk-tile-body">
            <strong>Bill payment</strong>
            <span className="muted">Live inquiry and pay</span>
          </span>
          <span className="desk-tile-arrow" aria-hidden>→</span>
        </Link>
        <Link className="desk-tile" to="/branch/history">
          <span className="desk-tile-icon" aria-hidden>☰</span>
          <span className="desk-tile-body">
            <strong>History</strong>
            <span className="muted">Recent cash in, cash out, transfers, and bills</span>
          </span>
          <span className="desk-tile-arrow" aria-hidden>→</span>
        </Link>
        <Link className="desk-tile" to="/branch/xpin">
          <span className="desk-tile-icon" aria-hidden>⬡</span>
          <span className="desk-tile-body">
            <strong>Enter XPin</strong>
            <span className="muted">Validate PIN · show company details</span>
          </span>
          <span className="desk-tile-arrow" aria-hidden>→</span>
        </Link>
        <Link className="desk-tile" to="/branch/pay-bill">
          <span className="desk-tile-icon" aria-hidden>▤</span>
          <span className="desk-tile-body">
            <strong>Pay Bill</strong>
            <span className="muted">Company bills · pay / skip / finish</span>
          </span>
          <span className="desk-tile-arrow" aria-hidden>→</span>
        </Link>
      </div>
    </div>
  );
}
