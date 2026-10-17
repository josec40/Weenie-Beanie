import { useState } from 'react';
import { Link } from 'react-router-dom';
import { gql, useMutation } from '@apollo/client';
import { Sparkles } from 'lucide-react';
import { monthLong, usd } from '../lib/format';
import { BeanStrip, BeanTile } from './BeanWorld';

export type Offer = {
  id: string;
  shortfallMonth: string;
  repaymentMonth: string;
  deficit: number;
  principal: number;
  fee: number;
  explanation: string | null;
};

export type Loan = {
  id: string;
  status: string;
  principal: number;
  fee: number;
  totalDue: number;
  disburseMonth: string;
  repayMonth: string;
  graceUntilMonth: string | null;
  rescheduleCount: number;
  lastFailure: string | null;
  disburseTxUrl: string | null;
  repayTxUrl: string | null;
  nessieDepositId: string | null;
  nessieWithdrawalId: string | null;
  createdAt: string;
};

export const LOAN_FIELDS = gql`
  fragment LoanFields on Loan {
    id
    status
    principal
    fee
    totalDue
    disburseMonth
    repayMonth
    graceUntilMonth
    rescheduleCount
    lastFailure
    disburseTxUrl
    repayTxUrl
    nessieDepositId
    nessieWithdrawalId
    createdAt
  }
`;

const ACCEPT = gql`
  mutation Accept($offerId: ID!, $consent: Boolean!) {
    acceptOffer(offerId: $offerId, consent: $consent) {
      id
      status
    }
  }
`;
const DECLINE = gql`
  mutation Decline($offerId: ID!) {
    declineOffer(offerId: $offerId)
  }
`;

export const OPEN_STATUSES = ['ACCEPTED', 'DISBURSING', 'ACTIVE', 'REPAYING', 'LATE'];

export function OfferCard({ offer, onChanged }: { offer: Offer; onChanged: () => void }) {
  const [consent, setConsent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accept, acceptState] = useMutation(ACCEPT);
  const [decline, declineState] = useMutation(DECLINE);
  const due = offer.principal + offer.fee;

  async function run(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
      onChanged();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong. Please try again.');
    }
  }

  return (
    <section className="offer" aria-labelledby="offer-title">
      <BeanStrip />
      <h2 id="offer-title">
        Cover {monthLong(offer.shortfallMonth)} with {usd.format(offer.principal)}
      </h2>
      {offer.explanation && (
        <p className="offer__why">
          <Sparkles aria-hidden="true" size={16} />
          <span>{offer.explanation}</span>
        </p>
      )}
      <dl className="terms">
        <div>
          <dt>You receive</dt>
          <dd>{usd.format(offer.principal)} at the start of {monthLong(offer.shortfallMonth)}</dd>
        </div>
        <div>
          <dt>Flat fee</dt>
          <dd>{usd.format(offer.fee)}, no interest</dd>
        </div>
        <div>
          <dt>Auto-repayment</dt>
          <dd>
            {usd.format(due)} at the end of {monthLong(offer.repaymentMonth)}
          </dd>
        </div>
        <div>
          <dt>Safeguard</dt>
          <dd>Skipped if it would leave your balance under the safety buffer</dd>
        </div>
      </dl>
      <label className="consent">
        <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        <span>
          I agree to receive {usd.format(offer.principal)} and to an automatic draft of {usd.format(due)} in{' '}
          {monthLong(offer.repaymentMonth)}.
        </span>
      </label>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="button-row">
        <button
          type="button"
          className="button button--grow"
          disabled={!consent || acceptState.loading}
          onClick={() => void run(() => accept({ variables: { offerId: offer.id, consent } }))}
        >
          {acceptState.loading ? 'Accepting…' : `Accept ${usd.format(offer.principal)}`}
        </button>
        <button
          type="button"
          className="button button--secondary"
          disabled={declineState.loading}
          onClick={() => void run(() => decline({ variables: { offerId: offer.id } }))}
        >
          No Thanks
        </button>
      </div>
    </section>
  );
}

const STATUS_COPY: Record<string, (l: Loan) => { label: string; text: string; tone: 'calm' | 'warn' | 'bad' | 'good' }> = {
  ACCEPTED: (l) => ({ label: 'Accepted', text: `${usd.format(l.principal)} arrives at the start of ${monthLong(l.disburseMonth)}.`, tone: 'calm' }),
  DISBURSING: (l) => ({ label: 'On its way', text: `${usd.format(l.principal)} is being deposited now.`, tone: 'calm' }),
  ACTIVE: (l) => ({ label: 'Active', text: `${usd.format(l.totalDue)} repays automatically at the end of ${monthLong(l.repayMonth)}.`, tone: 'calm' }),
  REPAYING: (l) => ({ label: 'Repaying', text: `The ${usd.format(l.totalDue)} repayment is in progress.`, tone: 'calm' }),
  LATE: (l) => ({
    label: 'Repayment paused',
    text:
      l.lastFailure === 'INSUFFICIENT_FUNDS'
        ? `We skipped the draft to protect your balance and will try again${l.graceUntilMonth ? ` through ${monthLong(l.graceUntilMonth)}` : ''}.`
        : `A transfer didn't complete. We'll retry${l.graceUntilMonth ? ` through ${monthLong(l.graceUntilMonth)}` : ''}.`,
    tone: 'warn',
  }),
  REPAID: (l) => ({ label: 'Repaid', text: `${usd.format(l.totalDue)} repaid. Your bean is planted.`, tone: 'good' }),
  DEFAULTED: () => ({ label: 'Defaulted', text: 'The grace period ended without a repayment.', tone: 'bad' }),
};

export function loanCopy(l: Loan) {
  return (STATUS_COPY[l.status] ?? (() => ({ label: l.status, text: '', tone: 'calm' as const })))(l);
}

/** Compact open-loan summary with the claimed bean. */
export function ActiveLoanCard({ loan }: { loan: Loan }) {
  const copy = loanCopy(loan);
  return (
    <section className={`loan-card loan-card--${copy.tone}`} aria-labelledby="loan-card-title">
      <div className="loan-card__head">
        <h2 id="loan-card-title">Your micro-loan</h2>
        <span className={`status status--${copy.tone}`}>{copy.label}</span>
      </div>
      <p className="loan-card__amount">
        <span className="figure">{usd.format(loan.principal)}</span>
        <span className="muted"> + {usd.format(loan.fee)} fee</span>
      </p>
      <p>{copy.text}</p>
      {loan.lastFailure && loan.status !== 'LATE' && (
        <p className="form-error">There's a delay settling this loan. No money moves twice; we'll keep retrying.</p>
      )}
      <div className="bean-claim">
        <BeanTile size={36} />
        <p>
          {loan.rescheduleCount > 0 ? (
            <>
              <strong>Bean claimed.</strong> Because the repayment moved to {monthLong(loan.repayMonth)}, it will be planted
              without a new leaf.
            </>
          ) : (
            <>
              <strong>Bean claimed.</strong> Repay on time in {monthLong(loan.repayMonth)} to plant it and grow your
              beanstalk.
            </>
          )}
        </p>
      </div>
      <Link to="/loans" className="text-link">
        Loan details and options
      </Link>
    </section>
  );
}
