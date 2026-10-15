import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../components/PageHeader';
import { PaymentReceipt } from '../../components/PaymentReceipt';
import { api } from '../../lib/api';
import { useAuth } from '../../auth/AuthContext';
import type { PaymentReceiptModel } from '../../lib/paymentReceipt';

type Company = { publicId: string; name: string; ntn?: string | null; email: string; billCount: number };
type Bill = {
  publicId: string;
  consumerNo: string;
  consumerName: string;
  amount: number;
  amountAfterDueDate?: number | null;
  dueDate?: string | null;
};

export function BranchPayBillPage() {
  const { session } = useAuth();
  const [companies, setCompanies] = useState<Company[]>([]);
  const [companyId, setCompanyId] = useState('');
  const [bills, setBills] = useState<Bill[]>([]);
  const [consumerNo, setConsumerNo] = useState('');
  const [selected, setSelected] = useState<Bill | null>(null);
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [loading, setLoading] = useState(false);
  const [receipt, setReceipt] = useState<PaymentReceiptModel | null>(null);

  const loadCompanies = useCallback(async () => {
    if (!session?.token) return;
    try {
      const rows = await api<Company[]>('/api/branch/companies', { token: session.token });
      setCompanies(rows);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load companies');
    }
  }, [session?.token]);

  useEffect(() => {
    void loadCompanies();
  }, [loadCompanies]);

  useEffect(() => {
    if (!session?.token || !companyId) {
      setBills([]);
      setSelected(null);
      return;
    }
    void (async () => {
      try {
        const rows = await api<Bill[]>(`/api/branch/companies/${companyId}/bills`, { token: session.token });
        setBills(rows);
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load consumers');
      }
    })();
  }, [session?.token, companyId]);

  useEffect(() => {
    const bill = bills.find((b) => b.consumerNo === consumerNo.trim()) || null;
    setSelected(bill);
  }, [consumerNo, bills]);

  async function submitAction(action: 'PAY' | 'SKIP' | 'FINISH') {
    if (!session?.token || !companyId) return;
    setError('');
    setOk('');
    setLoading(true);
    try {
      const res = await api<{
        message: string;
        action: string;
        companyName?: string;
        consumerNo?: string;
        consumerName?: string;
        amount?: number | null;
        publicId: string;
      }>('/api/branch/pay-bill', {
        method: 'POST',
        token: session.token,
        body: JSON.stringify({
          companyPublicId: companyId,
          consumerNo: consumerNo.trim() || (bills[0]?.consumerNo ?? 'N/A'),
          action,
        }),
      });
      setOk(res.message);
      setReceipt({
        product: 'BILL',
        productLabel: 'Branch bill',
        success: true,
        statusLabel: res.action,
        amountDisplay: res.amount != null
          ? Number(res.amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
          : '—',
        currency: 'PKR',
        paidAt: new Date().toISOString(),
        portalTxnRef: res.publicId,
        counterpartyTitle: res.consumerName || res.consumerNo,
        counterpartySubtitle: res.companyName,
        rows: [
          { label: 'Company', value: res.companyName || '—' },
          { label: 'Consumer', value: res.consumerNo || '—' },
          { label: 'Action', value: res.action },
        ],
        networkRows: res.publicId ? [{ label: 'Reference', value: res.publicId }] : [],
        message: res.message,
      });
      if (action === 'FINISH') {
        setCompanyId('');
        setConsumerNo('');
        setSelected(null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Action failed');
    } finally {
      setLoading(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void submitAction('PAY');
  }

  const company = companies.find((c) => c.publicId === companyId);

  if (session?.role !== 'BRANCH_USER') {
    return (
      <div className="page">
        <PageHeader title="Pay Bill" subtitle="Branch desk only" />
        <p className="muted">Login as branch user. <Link to="/login">Login</Link></p>
      </div>
    );
  }

  return (
    <div className="page desk-page">
      <PageHeader
        title="Pay Bill"
        subtitle="Select company and consumer from uploaded bills — record pay / skip (no wallet deduct)"
      />
      {error && <div className="alert alert-error">{error}</div>}
      {ok && <div className="alert alert-success">{ok}</div>}

      <div className="desk-split">
        <form className="desk-card" onSubmit={onSubmit}>
          <p className="desk-kicker">Bill desk</p>
          <h3 className="desk-card-title">Select & process</h3>

          <div className="form-row">
            <label htmlFor="pay-company">Company</label>
            <select
              id="pay-company"
              value={companyId}
              onChange={(e) => { setCompanyId(e.target.value); setConsumerNo(''); }}
              required
            >
              <option value="">Select company…</option>
              {companies.map((c) => (
                <option key={c.publicId} value={c.publicId}>
                  {c.name} ({c.billCount} bills)
                </option>
              ))}
            </select>
          </div>

          <div className="form-row">
            <label htmlFor="pay-consumer">Consumer number</label>
            <input
              id="pay-consumer"
              list="consumer-list"
              value={consumerNo}
              onChange={(e) => setConsumerNo(e.target.value)}
              placeholder="From bill upload"
              required
              disabled={!companyId}
            />
            <datalist id="consumer-list">
              {bills.map((b) => (
                <option key={b.publicId} value={b.consumerNo}>
                  {b.consumerName}
                </option>
              ))}
            </datalist>
          </div>

          <div className="desk-actions">
            <button className="btn btn-primary" type="submit" disabled={loading || !selected}>
              {loading ? 'Working…' : 'Pay'}
            </button>
            <button
              className="btn btn-ghost"
              type="button"
              disabled={loading || !selected}
              onClick={() => void submitAction('SKIP')}
            >
              Skip
            </button>
            <button
              className="btn btn-ghost"
              type="button"
              disabled={loading || !companyId}
              onClick={() => void submitAction('FINISH')}
            >
              Finish
            </button>
          </div>
          <Link className="desk-back-link" to="/branch">← Branch home</Link>
        </form>

        <div className="desk-card desk-preview-card">
          <p className="desk-kicker">Preview</p>
          {!companyId && (
            <p className="muted desk-card-lead">Choose a company to load consumers from their bill file.</p>
          )}
          {companyId && !selected && (
            <p className="muted desk-card-lead">
              {company?.name} — {bills.length} consumer(s). Type or pick a consumer number.
            </p>
          )}
          {selected && (
            <>
              <h3 className="desk-card-title">{selected.consumerName}</h3>
              <dl className="desk-facts">
                <div><dt>Consumer No</dt><dd>{selected.consumerNo}</dd></div>
                <div><dt>Amount</dt><dd className="desk-amount">{Number(selected.amount).toLocaleString()}</dd></div>
                <div><dt>After due</dt><dd>{selected.amountAfterDueDate != null ? Number(selected.amountAfterDueDate).toLocaleString() : '—'}</dd></div>
                <div><dt>Due date</dt><dd>{selected.dueDate ?? '—'}</dd></div>
                {company && (
                  <div><dt>Company</dt><dd>{company.name}{company.ntn ? ` · ${company.ntn}` : ''}</dd></div>
                )}
              </dl>
            </>
          )}
        </div>
      </div>
      {receipt && <PaymentReceipt receipt={receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}
