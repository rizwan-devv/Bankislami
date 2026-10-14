import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { useAuth } from '../../auth/AuthContext';

export function BranchHomePage() {
  const { session } = useAuth();

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
        subtitle="Verify visitor XPins and process uploaded corporate bills — no wallet deduction"
      />

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
