import { useState } from 'react';
import { Link } from 'react-router-dom';
import { gql, useMutation, useQuery, useApolloClient } from '@apollo/client';
import { ArrowRight, ExternalLink, AlertCircle, FastForward, Sparkles } from 'lucide-react';
import { BeanTile } from '../components/BeanWorld';
import { LOAN_FIELDS, OPEN_STATUSES, loanCopy, type Loan } from '../components/LoanCards';
import { monthLong, monthShort, usd } from '../lib/format';
import { PageSkeleton } from './Home';

const LOANS = gql`
  query LoansPage {
    loans {
      ...LoanFields
    }
    currentOffer {
      id
      principal
      shortfallMonth
    }
    forecast(months: 1) {
      currentMonth
      startingBalance
    }
  }
  ${LOAN_FIELDS}
`;

const RESCHEDULE = gql`
  mutation Reschedule($loanId: ID!) {
    rescheduleLoan(loanId: $loanId) {
      id
    }
  }
`;
const REPAY_EARLY = gql`
  mutation RepayEarly($loanId: ID!) {
    repayLoanEarly(loanId: $loanId) {
      id
    }
  }
`;

const ADVANCE_SIMULATION = gql`
  mutation AdvanceSimulation($months: Int) {
    advanceMonth(months: $months) {
      currentMonth
      offset
      enabled
    }
  }
`;

type LoansData = {
  loans: Loan[];
  currentOffer: { id: string; principal: number; shortfallMonth: string } | null;
  forecast: { currentMonth: string; startingBalance: number } | null;
};

const STEPS: { key: string; label: string; done: (s: string) => boolean }[] = [
  { key: 'accepted', label: 'Accepted', done: () => true },
  { key: 'deposited', label: 'Money deposited', done: (s) => ['ACTIVE', 'REPAYING', 'LATE', 'REPAID', 'DEFAULTED'].includes(s) },
  { key: 'repaid', label: 'Repaid, bean planted', done: (s) => s === 'REPAID' },
];

function monthDiff(fromStr: string, toStr: string): number {
  if (!fromStr || !toStr) return 1;
  const [y1, m1] = fromStr.split('-').map(Number);
  const [y2, m2] = toStr.split('-').map(Number);
  if (isNaN(y1) || isNaN(m1) || isNaN(y2) || isNaN(m2)) return 1;
  return (y2 - y1) * 12 + (m2 - m1);
}

export default function Loans() {
  const client = useApolloClient();
  const { data, loading, error, refetch } = useQuery<LoansData>(LOANS, { pollInterval: 30000 });
  const [reschedule, rescheduleState] = useMutation(RESCHEDULE);
  const [repayEarly, repayState] = useMutation(REPAY_EARLY);
  const [advanceSim, advanceSimState] = useMutation(ADVANCE_SIMULATION);
  const [actionError, setActionError] = useState<string | null>(null);
  const [simSuccessMsg, setSimSuccessMsg] = useState<string | null>(null);

  if (loading && !data) return <PageSkeleton />;
  if (error || !data) {
    return (
      <div className="page">
        <p className="form-error" role="alert">
          We couldn't load your loans. Try again in a moment.
        </p>
      </div>
    );
  }

  const open = data.loans.find((l) => OPEN_STATUSES.includes(l.status));
  const past = data.loans.filter((l) => l !== open);
  const currentBalance = data.forecast?.startingBalance ?? 0;
  const currentMonth = data.forecast?.currentMonth ?? '';
  const monthsUntilDue = currentMonth && open?.repayMonth ? monthDiff(currentMonth, open.repayMonth) : 1;
  const isInsufficient = open ? currentBalance < open.totalDue : false;

  async function act(action: () => Promise<unknown>) {
    setActionError(null);
    setSimSuccessMsg(null);
    try {
      await action();
      await refetch();
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Something went wrong. Please try again.');
    }
  }

  async function handleSimulate(months: number) {
    setActionError(null);
    setSimSuccessMsg(null);
    try {
      const res = await advanceSim({ variables: { months } });
      const nextMonth = res.data?.advanceMonth?.currentMonth;
      await client.refetchQueries({ include: 'active' });
      setSimSuccessMsg(
        months > 1
          ? `Advanced ${months} months to ${monthLong(nextMonth ?? '')}! Aggregate financial statements updated & due loan fulfilled.`
          : `Advanced 1 month to ${monthLong(nextMonth ?? '')}! Aggregate financial statements updated.`
      );
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Simulation failed');
    }
  }

  return (
    <div className="page loans">
      <div className="page__head" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
        <div>
          <h1 style={{ margin: 0 }}>Loans</h1>
          <p className="muted small" style={{ margin: '4px 0 0 0' }}>
            Micro-loans designed to prevent overdrafts with automated draft settlement.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
          <button
            type="button"
            className="button button--secondary button--small"
            disabled={advanceSimState.loading}
            onClick={() => void handleSimulate(1)}
            style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
          >
            <Sparkles size={14} aria-hidden="true" />
            {advanceSimState.loading ? 'Simulating…' : 'Simulate Month (+1)'}
          </button>
        </div>
      </div>

      {data.currentOffer && !open && (
        <Link to="/" className="notice-link">
          You have an offer of {usd.format(data.currentOffer.principal)} for {monthLong(data.currentOffer.shortfallMonth)}. Review it on
          Home <ArrowRight aria-hidden="true" size={16} />
        </Link>
      )}

      {open ? (
        <section className="panel loan-detail" aria-labelledby="open-loan-title">
          <div className="loan-detail__head">
            <h2 id="open-loan-title">
              {usd.format(open.principal)} for {monthLong(open.disburseMonth)}
            </h2>
            <span className={`status status--${loanCopy(open).tone}`}>{loanCopy(open).label}</span>
          </div>
          <p>{loanCopy(open).text}</p>

          <ol className="steps">
            {STEPS.map((s) => (
              <li key={s.key} className={s.done(open.status) ? 'is-done' : ''}>
                <span className="steps__dot" aria-hidden="true" />
                <span>{s.label}</span>
                <span className="visually-hidden">{s.done(open.status) ? ' (done)' : ' (pending)'}</span>
              </li>
            ))}
          </ol>

          <dl className="terms">
            <div>
              <dt>Deposited</dt>
              <dd>
                {usd.format(open.principal)} in {monthLong(open.disburseMonth)}
              </dd>
            </div>
            <div>
              <dt>Repayment</dt>
              <dd>
                {usd.format(open.totalDue)} at the end of {monthLong(open.repayMonth)}
                {open.graceUntilMonth && `, grace until ${monthLong(open.graceUntilMonth)}`}
              </dd>
            </div>
          </dl>

          <div className="bean-claim">
            <BeanTile size={36} />
            <p>
              <strong>Bean claimed.</strong>{' '}
              {open.rescheduleCount > 0
                ? 'The repayment moved, so the bean will be planted without a new leaf.'
                : `Repay by the end of ${monthLong(open.repayMonth)} to plant it and grow a leaf.`}
            </p>
          </div>

          <Proof loan={open} />

          {open.status === 'ACTIVE' && isInsufficient && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                padding: '14px 16px',
                borderRadius: 'var(--radius-sm)',
                background: '#fff8eb',
                border: '1.5px solid #fde68a',
                color: '#92400e',
                margin: '16px 0',
                fontSize: '0.9rem',
              }}
            >
              <AlertCircle size={22} style={{ flexShrink: 0 }} />
              <div>
                <strong>Early repayment blocked:</strong> Available checking balance ({usd.format(currentBalance)}) is less than
                total due ({usd.format(open.totalDue)}).
                Automatic draft will safely execute at the end of <strong>{monthLong(open.repayMonth)}</strong> when your expected income is received.
              </div>
            </div>
          )}

          {simSuccessMsg && (
            <div
              style={{
                padding: '12px 16px',
                borderRadius: 'var(--radius-sm)',
                background: '#ecfdf5',
                border: '1.5px solid #a7f3d0',
                color: '#065f46',
                margin: '16px 0',
                fontSize: '0.9rem',
                fontWeight: 600,
              }}
              role="status"
            >
              {simSuccessMsg}
            </div>
          )}

          {['ACCEPTED', 'DISBURSING', 'ACTIVE'].includes(open.status) && (
            <div className="loan-simulation-callout">
              <div className="loan-simulation-callout__icon">
                <Sparkles size={20} />
              </div>
              <div className="loan-simulation-callout__body">
                <h4>Simulate Time & Fulfill Repayment</h4>
                <p>
                  This micro-loan is scheduled to automatically repay at the end of{' '}
                  <strong>{monthLong(open.repayMonth)}</strong>
                  {monthsUntilDue > 0 ? ` (${monthsUntilDue} ${monthsUntilDue === 1 ? 'month' : 'months'} away)` : ''}.
                  Simulate the calendar to that month to realize next month's cashflow statements and fulfill this loan immediately!
                </p>
                <div className="button-row" style={{ marginTop: '12px' }}>
                  <button
                    type="button"
                    className="button button--small"
                    disabled={advanceSimState.loading}
                    onClick={() => void handleSimulate(Math.max(1, monthsUntilDue))}
                  >
                    <FastForward size={14} aria-hidden="true" />
                    {advanceSimState.loading
                      ? 'Simulating…'
                      : monthsUntilDue > 1
                      ? `Fast-Forward ${monthsUntilDue} Months (Fulfill Loan ⚡)`
                      : `Simulate to ${monthShort(open.repayMonth)} (Fulfill Loan ⚡)`}
                  </button>
                  <button
                    type="button"
                    className="button button--secondary button--small"
                    disabled={advanceSimState.loading}
                    onClick={() => void handleSimulate(1)}
                  >
                    Simulate +1 Month
                  </button>
                </div>
              </div>
            </div>
          )}

          {actionError && (
            <p className="form-error" role="alert">
              {actionError}
            </p>
          )}
          <div className="button-row">
            {open.status === 'ACTIVE' && (
              <button
                type="button"
                className="button"
                disabled={repayState.loading || isInsufficient}
                onClick={() => void act(() => repayEarly({ variables: { loanId: open.id } }))}
                title={isInsufficient ? `Balance (${usd.format(currentBalance)}) is less than total due (${usd.format(open.totalDue)})` : undefined}
              >
                {repayState.loading
                  ? 'Starting repayment…'
                  : isInsufficient
                  ? `Insufficient Balance to Repay (${usd.format(currentBalance)} / ${usd.format(open.totalDue)})`
                  : `Repay ${usd.format(open.totalDue)} Now`}
              </button>
            )}
            {['ACCEPTED', 'DISBURSING', 'ACTIVE'].includes(open.status) && open.rescheduleCount === 0 && (
              <button
                type="button"
                className="button button--secondary"
                disabled={rescheduleState.loading}
                onClick={() => void act(() => reschedule({ variables: { loanId: open.id } }))}
              >
                {rescheduleState.loading ? 'Moving…' : 'Push Repayment One Month'}
              </button>
            )}
          </div>
          {['ACCEPTED', 'DISBURSING', 'ACTIVE'].includes(open.status) && open.rescheduleCount === 0 && (
            <p className="muted small">You can push the repayment once. A pushed repayment doesn't count as on time.</p>
          )}
        </section>
      ) : (
        <section className="calm-card">
          <h2>No open micro-loan</h2>
          <p>When your forecast shows a shortfall that fits your limit, an offer appears on Home.</p>
          <div style={{ marginTop: '16px' }}>
            <button
              type="button"
              className="button button--secondary button--small"
              disabled={advanceSimState.loading}
              onClick={() => void handleSimulate(1)}
              style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}
            >
              <Sparkles size={14} />
              {advanceSimState.loading ? 'Simulating…' : 'Simulate Next Month'}
            </button>
          </div>
        </section>
      )}

      <section className="panel" aria-labelledby="history-title">
        <h2 id="history-title">History</h2>
        {past.length === 0 ? (
          <p className="muted">Repaid and closed loans will appear here.</p>
        ) : (
          <div className="table-wrap">
            <table className="data-table">
              <thead>
                <tr>
                  <th scope="col">Loan</th>
                  <th scope="col">Status</th>
                  <th scope="col" className="num">Repaid</th>
                  <th scope="col">Proof</th>
                </tr>
              </thead>
              <tbody>
                {past.map((l) => (
                  <tr key={l.id}>
                    <th scope="row">
                      {usd.format(l.principal)} · {monthShort(l.disburseMonth)}
                    </th>
                    <td>
                      <span className={`status status--${loanCopy(l).tone}`}>{loanCopy(l).label}</span>
                    </td>
                    <td className="num">{l.status === 'REPAID' ? usd.format(l.totalDue) : '—'}</td>
                    <td>
                      <Proof loan={l} compact />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function Proof({ loan, compact = false }: { loan: Loan; compact?: boolean }) {
  if (!loan.disburseTxUrl && !loan.repayTxUrl && !loan.vendorTxUrl) {
    return compact ? <span className="muted">—</span> : null;
  }
  return (
    <ul className={compact ? 'proof proof--compact' : 'proof'} aria-label="On-chain records">
      {loan.disburseTxUrl && (
        <li>
          <a href={loan.disburseTxUrl} target="_blank" rel="noopener noreferrer">
            {compact ? 'Deposit' : 'Deposit on Solana Explorer'}
            <ExternalLink aria-hidden="true" size={14} />
          </a>
        </li>
      )}
      {loan.vendorTxUrl && (
        <li>
          <a href={loan.vendorTxUrl} target="_blank" rel="noopener noreferrer">
            {compact ? 'Vendor Payment' : 'Vendor Payment on Solana Explorer'}
            <ExternalLink aria-hidden="true" size={14} />
          </a>
        </li>
      )}
      {loan.repayTxUrl && (
        <li>
          <a href={loan.repayTxUrl} target="_blank" rel="noopener noreferrer">
            {compact ? 'Repayment' : 'Repayment on Solana Explorer'}
            <ExternalLink aria-hidden="true" size={14} />
          </a>
        </li>
      )}
    </ul>
  );
}
