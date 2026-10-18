import { useState } from 'react';
import { Link } from 'react-router-dom';
import { gql, useLazyQuery, useMutation } from '@apollo/client';
import { CheckCircle2, AlertTriangle, AlertCircle, Sparkles, ShoppingBag, X } from 'lucide-react';
import { monthLong, usd } from '../lib/format';
import type { RunwayMonth } from './RunwayChart';

const PURCHASE_CHECK = gql`
  query PurchaseCheck($amount: Float!, $label: String!) {
    purchaseCheck(amount: $amount, label: $label) {
      verdict
      reason
      balanceNow
      gapToday
      affordableFrom
      laterShortfallMonth
      proposal {
        principal
        fee
        repaymentMonth
        totalDue
      }
      months {
        month
        endingBalance
        shortfall
      }
      explanation {
        text
      }
    }
  }
`;

const ACCEPT_PURCHASE_LOAN = gql`
  mutation AcceptPurchaseLoan($amount: Float!, $label: String!, $consent: Boolean!, $expectedTotalDue: Float!) {
    acceptPurchaseLoan(amount: $amount, label: $label, consent: $consent, expectedTotalDue: $expectedTotalDue) {
      id
      status
      principal
      fee
      totalDue
      purpose
      purchaseLabel
      purchaseAmount
    }
  }
`;

type Proposal = {
  principal: number;
  fee: number;
  repaymentMonth: string;
  totalDue: number;
};

type CheckResult = {
  verdict: 'AFFORDABLE' | 'LOAN_AVAILABLE' | 'NOT_ELIGIBLE';
  reason: string | null;
  balanceNow: number;
  gapToday: number;
  affordableFrom: string | null;
  laterShortfallMonth: string | null;
  proposal: Proposal | null;
  months: RunwayMonth[];
  explanation: { text: string } | null;
};

const DEMO_PRESETS = [
  { label: 'Coffee Grinder', amount: 20, display: '$20 Grinder', status: 'Affordable' },
  { label: 'Cold Brew Keg', amount: 450, display: '$450 Keg', status: 'Micro-loan' },
  { label: 'Commercial Espresso Machine', amount: 900, display: '$900 Machine', status: 'Exceeds limit' },
  { label: 'Pro Roaster Batch System', amount: 1800, display: '$1,800 Roaster', status: 'Affordable' },
  { label: 'Artisan Sample Roaster', amount: 1900, display: '$1,900 Roaster', status: 'Micro-loan' },
  { label: 'Pro Roaster Batch System', amount: 2000, display: '$2,000 Roaster', status: 'Exceeds balance' },
];

export default function PurchaseCheck({
  onWhatIfMonths,
  onLoanAccepted,
}: {
  onWhatIfMonths?: (months: RunwayMonth[] | null) => void;
  onLoanAccepted?: () => void;
}) {
  const [label, setLabel] = useState('');
  const [amountInput, setAmountInput] = useState('');
  const [consent, setConsent] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [acceptedSuccess, setAcceptedSuccess] = useState(false);

  const [checkPurchase, { data, loading, error }] = useLazyQuery<{ purchaseCheck: CheckResult }>(PURCHASE_CHECK, {
    fetchPolicy: 'network-only',
    onCompleted: (res) => {
      onWhatIfMonths?.(res.purchaseCheck.months);
    },
  });

  const [acceptLoan, { loading: accepting }] = useMutation(ACCEPT_PURCHASE_LOAN);

  const result = data?.purchaseCheck;

  async function handleCheck(e: React.FormEvent) {
    e.preventDefault();
    setActionError(null);
    setAcceptedSuccess(false);
    setConsent(false);
    const amount = parseFloat(amountInput);
    if (isNaN(amount) || amount <= 0) {
      setActionError('Please enter a valid amount greater than $0.');
      return;
    }
    const cleanLabel = label.trim() || 'One-time purchase';
    await checkPurchase({ variables: { amount, label: cleanLabel } });
  }

  function handlePreset(presetLabel: string, presetAmount: number) {
    setLabel(presetLabel);
    setAmountInput(presetAmount.toString());
    setActionError(null);
    setAcceptedSuccess(false);
    setConsent(false);
    void checkPurchase({ variables: { amount: presetAmount, label: presetLabel } });
  }

  function handleClear() {
    setLabel('');
    setAmountInput('');
    setActionError(null);
    setAcceptedSuccess(false);
    setConsent(false);
    onWhatIfMonths?.(null);
  }

  async function handleAccept() {
    if (!result?.proposal) return;
    setActionError(null);
    const amount = parseFloat(amountInput);
    try {
      await acceptLoan({
        variables: {
          amount,
          label: label.trim() || 'One-time purchase',
          consent,
          expectedTotalDue: result.proposal.totalDue,
        },
      });
      setAcceptedSuccess(true);
      onLoanAccepted?.();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to accept purchase loan.');
    }
  }

  return (
    <section className="card purchase-check" aria-labelledby="purchase-check-title">
      <div className="purchase-check__header">
        <div className="purchase-check__title-row">
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <ShoppingBag size={22} color="var(--navy-700)" />
            <h2 id="purchase-check-title" className="purchase-check__title">
              Can I afford this purchase?
            </h2>
          </div>
          {(label || amountInput || result) && (
            <button
              type="button"
              className="icon-button"
              aria-label="Clear check"
              onClick={handleClear}
              style={{ width: '32px', height: '32px' }}
            >
              <X size={16} />
            </button>
          )}
        </div>
        <p className="purchase-check__subtitle">
          Run a stateless what-if simulation against your 12-month runway before spending.
        </p>
      </div>

      {/* Preset demo buttons */}
      <div className="purchase-check__demo">
        <span className="purchase-check__demo-label">Demo items</span>
        <div className="purchase-check__demo-grid">
          {DEMO_PRESETS.map((p) => (
            <button
              key={`${p.display}-${p.amount}`}
              type="button"
              className="purchase-check__demo-btn"
              onClick={() => handlePreset(p.label, p.amount)}
            >
              <span className="purchase-check__demo-name">{p.display}</span>
              <span className="purchase-check__demo-status">({p.status})</span>
            </button>
          ))}
        </div>
      </div>

      <form onSubmit={(e) => void handleCheck(e)} className="purchase-check__form">
        <div className="purchase-check__form-row">
          <label className="field purchase-check__field-name">
            <span>Item or expense name</span>
            <input
              type="text"
              placeholder="e.g. Coffee Grinder"
              value={label}
              maxLength={80}
              onChange={(e) => setLabel(e.target.value)}
            />
          </label>
          <label className="field purchase-check__field-amount">
            <span>Amount ($)</span>
            <input
              type="number"
              step="0.01"
              min="0.01"
              placeholder="0.00"
              value={amountInput}
              onChange={(e) => setAmountInput(e.target.value)}
            />
          </label>
          <button
            type="submit"
            className="button purchase-check__submit-btn"
            disabled={loading}
          >
            {loading ? 'Checking…' : 'Check Affordability'}
          </button>
        </div>
      </form>

      {error && (
        <p className="form-error" role="alert">
          {error.message}
        </p>
      )}

      {actionError && (
        <p className="form-error" role="alert">
          {actionError}
        </p>
      )}

      {/* Results Box */}
      {result && (
        <div className="purchase-check__result">
          {result.verdict === 'AFFORDABLE' && (
            <div style={{ display: 'grid', gap: '10px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <CheckCircle2 color="var(--green-700)" size={20} />
                <span className="status status--good">Affordable with Current Funds</span>
              </div>
              <p style={{ margin: 0, fontWeight: 600 }}>
                You have {usd.format(result.balanceNow)} available. Spending {usd.format(parseFloat(amountInput))} will keep your runway safe and positive.
              </p>
              {result.affordableFrom && (
                <p className="small muted" style={{ margin: 0 }}>
                  Safe starting in <strong>{monthLong(result.affordableFrom)}</strong>.
                </p>
              )}
              {result.explanation?.text && (
                <p className="offer__why" style={{ margin: 0 }}>
                  <Sparkles size={16} />
                  <span>{result.explanation.text}</span>
                </p>
              )}
            </div>
          )}

          {result.verdict === 'LOAN_AVAILABLE' && result.proposal && (
            <div style={{ display: 'grid', gap: '14px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <CheckCircle2 color="var(--navy-700)" size={20} />
                <span className="status status--calm">Micro-Loan Available</span>
              </div>
              <p style={{ margin: 0 }}>
                This purchase creates a temporary gap today of {usd.format(result.gapToday)}. We can bridge it with an immediate micro-loan.
              </p>

              <dl className="terms" style={{ background: 'var(--surface)', borderRadius: 'var(--radius-sm)', padding: '0 12px' }}>
                <div>
                  <dt>Loan Principal</dt>
                  <dd>{usd.format(result.proposal.principal)} (covers gap immediately)</dd>
                </div>
                <div>
                  <dt>Flat Fee</dt>
                  <dd>{usd.format(result.proposal.fee)} (no interest)</dd>
                </div>
                <div>
                  <dt>Auto-Repayment</dt>
                  <dd>
                    {usd.format(result.proposal.totalDue)} at the end of {monthLong(result.proposal.repaymentMonth)}
                  </dd>
                </div>
                <div>
                  <dt>Safeguard</dt>
                  <dd>Skipped if repayment would breach your safety buffer</dd>
                </div>
              </dl>

              {result.laterShortfallMonth && (
                <p className="small muted" style={{ margin: 0 }}>
                  Note: An informational shortfall exists in {monthLong(result.laterShortfallMonth)} (beyond the 3-month loan window).
                </p>
              )}

              {result.explanation?.text && (
                <p className="offer__why" style={{ margin: 0 }}>
                  <Sparkles size={16} />
                  <span>{result.explanation.text}</span>
                </p>
              )}

              {acceptedSuccess ? (
                <div style={{ padding: '16px', background: '#f0fdf4', border: '1.5px solid #bbf7d0', borderRadius: 'var(--radius-sm)', textAlign: 'center', display: 'grid', gap: '12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px', color: '#166534', fontWeight: 700, fontSize: '1.1rem' }}>
                    <CheckCircle2 size={24} color="#166534" />
                    <span>Purchase Complete!</span>
                  </div>
                  <p style={{ margin: 0, fontSize: '0.9rem', color: '#166534' }}>
                    Micro-loan accepted for {usd.format(result.proposal.principal)}. Your purchase is funded and settled.
                  </p>
                  <div style={{ display: 'flex', gap: '10px', justifyContent: 'center', flexWrap: 'wrap', marginTop: '4px' }}>
                    <Link to="/expenses?tab=purchases" className="button" style={{ fontSize: '0.9rem', padding: '8px 14px' }}>
                      See All Purchases
                    </Link>
                    <Link to="/loans" className="button button--secondary" style={{ fontSize: '0.9rem', padding: '8px 14px' }}>
                      Loan Tab
                    </Link>
                  </div>
                </div>
              ) : (
                <div style={{ display: 'grid', gap: '10px' }}>
                  <label className="consent">
                    <input
                      type="checkbox"
                      checked={consent}
                      onChange={(e) => setConsent(e.target.checked)}
                    />
                    <span>
                      I agree to receive {usd.format(result.proposal.principal)} and to an automatic draft of{' '}
                      {usd.format(result.proposal.totalDue)} in {monthLong(result.proposal.repaymentMonth)} for this purchase.
                    </span>
                  </label>
                  <button
                    type="button"
                    className="button"
                    disabled={!consent || accepting}
                    onClick={() => void handleAccept()}
                  >
                    {accepting ? 'Accepting & Disbursing…' : `Accept Loan for ${usd.format(result.proposal.principal)}`}
                  </button>
                </div>
              )}
            </div>
          )}

          {result.verdict === 'NOT_ELIGIBLE' && (
            <div style={{ display: 'grid', gap: '10px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                {result.reason === 'HAS_OPEN_LOAN' ? (
                  <AlertCircle color="var(--amber)" size={20} />
                ) : (
                  <AlertTriangle color="var(--red)" size={20} />
                )}
                <span className={`status ${result.reason === 'HAS_OPEN_LOAN' ? 'status--warn' : 'status--bad'}`}>
                  {result.reason === 'HAS_OPEN_LOAN' ? 'Active Loan Open' : 'Not Eligible for Loan'}
                </span>
              </div>

              {result.gapToday > 0 && (
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    padding: '10px 14px',
                    borderRadius: 'var(--radius-sm)',
                    background: '#fff1f2',
                    border: '1.5px solid #fecdd3',
                    color: '#9f1239',
                    fontSize: '0.9rem',
                  }}
                >
                  <AlertCircle size={18} style={{ flexShrink: 0 }} />
                  <span>
                    <strong>Exceeds Available Balance:</strong> Price of {usd.format(parseFloat(amountInput))} exceeds your checking balance of {usd.format(result.balanceNow)} by <strong>{usd.format(result.gapToday)}</strong>.
                  </span>
                </div>
              )}

              <p style={{ margin: 0, fontWeight: 500 }}>
                {result.reason === 'HAS_OPEN_LOAN' &&
                  'You currently have an active micro-loan. You must repay your existing loan before requesting a new purchase loan.'}
                {result.reason === 'EXCEEDS_TIER_CAP' &&
                  'This purchase requires a loan that exceeds your current credit limit. Repaying loans on time grows your beanstalk and increases your limit.'}
                {result.reason === 'NO_REPAYMENT_MONTH' &&
                  'None of the upcoming 3 months has projected surplus room to safely repay a loan without breaching your safety buffer.'}
                {!['HAS_OPEN_LOAN', 'EXCEEDS_TIER_CAP', 'NO_REPAYMENT_MONTH'].includes(result.reason ?? '') &&
                  (result.reason || 'This purchase cannot be financed at this time.')}
              </p>

              {result.laterShortfallMonth && (
                <p className="small muted" style={{ margin: 0 }}>
                  Note: A projected shortfall exists in {monthLong(result.laterShortfallMonth)} (beyond the 3-month loan window).
                </p>
              )}

              {result.explanation?.text && (
                <p className="offer__why" style={{ margin: 0 }}>
                  <Sparkles size={16} />
                  <span>{result.explanation.text}</span>
                </p>
              )}
            </div>
          )}
        </div>
      )}
    </section>
  );
}
