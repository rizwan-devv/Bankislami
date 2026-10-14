import { useNavigate } from 'react-router-dom';

export function RegisterPage() {
  const nav = useNavigate();
  return (
    <div className="page">
      <div className="container">
        <div className="badge">REGISTRATION</div>
        <h1 style={{ fontFamily: 'var(--font-display)', marginTop: '0.5rem' }}>How would you like to continue?</h1>
        <p className="muted" style={{ maxWidth: '36rem' }}>
          New entities start fresh onboarding. Existing customers look up their NTN in our mock
          directory, then continue the same DFS-style OTP and onboarding flow.
        </p>
        <div className="choice-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(260px,1fr))', gap: '1rem', marginTop: '1.5rem' }}>
          <button
            type="button"
            className="panel"
            style={{ textAlign: 'left', cursor: 'pointer', border: '1px solid var(--border)' }}
            onClick={() => nav('/signup')}
          >
            <h3 style={{ marginTop: 0, fontFamily: 'var(--font-display)' }}>New onboarding</h3>
            <p className="muted">Same corporate master signup → OTP → entity wizard as before.</p>
          </button>
          <button
            type="button"
            className="panel"
            style={{ textAlign: 'left', cursor: 'pointer', border: '1px solid var(--border)' }}
            onClick={() => nav('/register/existing')}
          >
            <h3 style={{ marginTop: 0, fontFamily: 'var(--font-display)' }}>Existing customer</h3>
            <p className="muted">Enter NTN → load company &amp; accounts from mock DB → continue signup.</p>
          </button>
        </div>
      </div>
    </div>
  );
}
