import { useState } from 'react';
import { gql, useMutation, useQuery } from '@apollo/client';

const LOAN_PANEL = gql`
  query LoanPanel {
    currentOffer {
      id
      shortfallMonth
      repaymentMonth
      deficit
      principal
      fee
      projectedSurplus
      explanation
    }
    loans {
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
    }
    creditProfile {
      tier
      cap
      offerStatus
    }
    demoClock {
      currentMonth
      offset
      enabled
    }
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
const ADVANCE = gql`
  mutation Advance {
    advanceMonth {
      currentMonth
    }
  }
`;

type Offer = {
  id: string;
  shortfallMonth: string;
  repaymentMonth: string;
  deficit: number;
  principal: number;
  fee: number;
  projectedSurplus: number;
  explanation: string | null;
};
type Loan = {
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
};
type PanelData = {
  currentOffer: Offer | null;
  loans: Loan[];
  creditProfile: { tier: number; cap: number; offerStatus: string };
  demoClock: { currentMonth: string; offset: number; enabled: boolean };
};

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const OPEN = ['ACCEPTED', 'DISBURSING', 'ACTIVE', 'REPAYING', 'LATE'];
const NO_OFFER_TEXT: Record<string, string> = {
  NO_SHORTFALL: 'No shortfall is projected, so no micro-loan is needed right now.',
  EXCEEDS_TIER_CAP: 'A shortfall is projected, but it is larger than your current credit limit.',
  NO_REPAYMENT_MONTH: 'A shortfall is projected, but no month within the repayment window can cover it.',
  HAS_OPEN_LOAN: 'You already have an open micro-loan.',
  OFFER_AVAILABLE: 'An offer is being prepared…',
};

function monthLabel(ym: string) {
  const [y, m] = ym.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, 1)).toLocaleDateString('en-US', { month: 'long', year: 'numeric', timeZone: 'UTC' });
}

function TxLink({ href }: { href: string }) {
  return (
    <>
      {' '}
      <a className="small" href={href} target="_blank" rel="noopener noreferrer">
        on-chain ↗
      </a>
    </>
  );
}

export default function LoansPanel({ onChanged }: { onChanged: () => void }) {
  // Offers and settlements are produced asynchronously via Kafka, so poll while the panel is visible.
  const { data, refetch } = useQuery<PanelData>(LOAN_PANEL, { pollInterval: 3000 });
  const [accept, acceptState] = useMutation(ACCEPT);
  const [decline] = useMutation(DECLINE);
  const [reschedule] = useMutation(RESCHEDULE);
  const [repayEarly] = useMutation(REPAY_EARLY);
  const [advance, advanceState] = useMutation(ADVANCE);
  const [consent, setConsent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
      await refetch();
      onChanged();
      // The forecast refresh is event-driven; pick it up a moment later as well.
      setTimeout(onChanged, 2500);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    }
  }

  if (!data) return null;
  const { currentOffer: offer, loans, creditProfile, demoClock } = data;
  const openLoan = loans.find((l) => OPEN.includes(l.status));

  return (
    <section className="panel">
      <div className="dash-header">
        <h2>Micro-loans</h2>
        <span className="muted small">
          Credit tier {creditProfile.tier} · limit {usd.format(creditProfile.cap)}
        </span>
      </div>

      {offer ? (
        <div className="offer">
          <span className="badge">Proactive offer</span>
          <h3>
            {usd.format(offer.principal)} in {monthLabel(offer.shortfallMonth)}
          </h3>
          {offer.explanation && <p>{offer.explanation}</p>}
          <dl className="terms">
            <dt>Projected shortfall</dt>
            <dd>{usd.format(offer.deficit)}</dd>
            <dt>Flat fee</dt>
            <dd>{usd.format(offer.fee)}</dd>
            <dt>Auto-repayment</dt>
            <dd>
              {usd.format(offer.principal + offer.fee)} in {monthLabel(offer.repaymentMonth)}
            </dd>
          </dl>
          <label className="consent">
            <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />I agree to
            receive {usd.format(offer.principal)} and to an automatic draft of {usd.format(offer.principal + offer.fee)} in{' '}
            {monthLabel(offer.repaymentMonth)}. Repayment is skipped if it would leave my balance too low.
          </label>
          <div className="actions">
            <button
              disabled={!consent || acceptState.loading}
              onClick={() => run(() => accept({ variables: { offerId: offer.id, consent } }))}
            >
              {acceptState.loading ? 'Accepting…' : 'Accept offer'}
            </button>
            <button className="secondary" onClick={() => run(() => decline({ variables: { offerId: offer.id } }))}>
              No thanks
            </button>
          </div>
        </div>
      ) : (
        !openLoan && <p className="muted">{NO_OFFER_TEXT[creditProfile.offerStatus] ?? creditProfile.offerStatus}</p>
      )}

      {loans.length > 0 && (
        <table>
          <thead>
            <tr>
              <th>Status</th>
              <th>Amount</th>
              <th>Disbursed</th>
              <th>Repayment</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {loans.map((l) => (
              <tr key={l.id} className={l.status === 'LATE' || l.status === 'DEFAULTED' ? 'row-neg' : ''}>
                <td>
                  <span className="badge">{l.status}</span>
                  {l.lastFailure && <span className="muted small"> {l.lastFailure.toLowerCase().replace('_', ' ')}</span>}
                </td>
                <td>
                  {usd.format(l.principal)} <span className="muted small">+ {usd.format(l.fee)} fee</span>
                </td>
                <td>
                  {monthLabel(l.disburseMonth)}
                  {l.disburseTxUrl && <TxLink href={l.disburseTxUrl} />}
                </td>
                <td>
                  {usd.format(l.totalDue)} in {monthLabel(l.repayMonth)}
                  {l.graceUntilMonth && <span className="muted small"> (grace until {monthLabel(l.graceUntilMonth)})</span>}
                  {l.repayTxUrl && <TxLink href={l.repayTxUrl} />}
                </td>
                <td className="actions">
                  {['ACCEPTED', 'DISBURSING', 'ACTIVE'].includes(l.status) && l.rescheduleCount === 0 && (
                    <button className="link" onClick={() => run(() => reschedule({ variables: { loanId: l.id } }))}>
                      Push repayment 1 month
                    </button>
                  )}
                  {l.status === 'ACTIVE' && (
                    <button className="link" onClick={() => run(() => repayEarly({ variables: { loanId: l.id } }))}>
                      Repay now
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {demoClock.enabled && (
        <div className="demo-clock">
          <span>
            Demo month: <strong>{monthLabel(demoClock.currentMonth)}</strong>
          </span>
          <button className="secondary" disabled={advanceState.loading} onClick={() => run(() => advance())}>
            {advanceState.loading ? 'Advancing…' : 'Advance one month'}
          </button>
        </div>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  );
}
