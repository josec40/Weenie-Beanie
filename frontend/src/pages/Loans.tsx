import { useState } from 'react';
import { Link } from 'react-router-dom';
import { gql, useMutation, useQuery } from '@apollo/client';
import { ArrowRight, ExternalLink, AlertCircle } from 'lucide-react';
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

type LoansData = {
  loans: Loan[];
  currentOffer: { id: string; principal: number; shortfallMonth: string } | null;
  forecast: { startingBalance: number } | null;
};

const STEPS: { key: string; label: string; done: (s: string) => boolean }[] = [
  { key: 'accepted', label: 'Accepted', done: () => true },
  { key: 'deposited', label: 'Money deposited', done: (s) => ['ACTIVE', 'REPAYING', 'LATE', 'REPAID', 'DEFAULTED'].includes(s) },
  { key: 'repaid', label: 'Repaid, bean planted', done: (s) => s === 'REPAID' },
];

export default function Loans() {
  const { data, loading, error, refetch } = useQuery<LoansData>(LOANS, { pollInterval: 30000 });
  const [reschedule, rescheduleState] = useMutation(RESCHEDULE);
  const [repayEarly, repayState] = useMutation(REPAY_EARLY);
  const [actionError, setActionError] = useState<string | null>(null);

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
  const isInsufficient = open ? currentBalance < open.totalDue : false;

  async function act(action: () => Promise<unknown>) {
    setActionError(null);
    try {
      await action();
      await refetch();
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Something went wrong. Please try again.');
    }
  }

  return (
    <div className="page loans">
      <div className="page__head">
        <h1>Loans</h1>
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
