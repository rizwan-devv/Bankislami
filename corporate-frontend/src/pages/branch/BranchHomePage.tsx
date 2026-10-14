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
    <div className="page">
      <PageHeader title="Branch desk" subtitle="XPin verify and pay bills (no wallet deduct)" />
      <div className="panel" style={{ display: 'grid', gap: '1rem' }}>
        <Link className="btn btn-primary" to="/branch/xpin">Enter XPin</Link>
        <Link className="btn btn-primary" to="/branch/pay-bill">Pay Bill</Link>
      </div>
    </div>
  );
}
