import { Link } from 'react-router-dom';
import { gql, useQuery } from '@apollo/client';
import { ArrowRight, RefreshCw } from 'lucide-react';
import Beanstalk from '../components/Beanstalk';
import { ActiveLoanCard, LOAN_FIELDS, OPEN_STATUSES, OfferCard, type Loan, type Offer } from '../components/LoanCards';
import RunwayChart from '../components/RunwayChart';
import { addMonths, dateTime, monthLong, usd, usdWhole } from '../lib/format';

const HOME = gql`
  query Home {
    forecast(months: 12) {
      currentMonth
      startingBalance
      balanceAsOf
      months {
        month
        endingBalance
        shortfall
      }
    }
    currentOffer {
      id
      shortfallMonth
      repaymentMonth
      deficit
      principal
      fee
      explanation
    }
    loans {
      ...LoanFields
    }
    creditProfile {
      tier
      cap
      offerStatus
    }
    credential {
      tier
      cap
      nextTierAt
      effectiveOnTime
      onTimeRepayments
      defaults
    }
  }
  ${LOAN_FIELDS}
`;

type HomeData = {
  forecast: {
    currentMonth: string;
    startingBalance: number;
    balanceAsOf: string | null;
    months: { month: string; endingBalance: number; shortfall: boolean }[];
  } | null;
  currentOffer: Offer | null;
  loans: Loan[];
  creditProfile: { tier: number; cap: number; offerStatus: string };
  credential: {
    tier: number;
    cap: number;
    nextTierAt: number | null;
    effectiveOnTime: number;
    onTimeRepayments: number;
    defaults: number;
  };
};

export const TIER_CAPS = [200, 500, 1000];

export default function Home() {
  const { data, loading, error, refetch } = useQuery<HomeData>(HOME, { pollInterval: 30000 });

  if (loading && !data) return <PageSkeleton />;
  if (error || !data) {
    return (
      <div className="page">
        <p className="form-error" role="alert">
          We couldn't load your accounts. Check your connection and try again.
        </p>
        <button type="button" className="button" onClick={() => void refetch()}>
          Try Again
        </button>
      </div>
    );
  }

  const { forecast, currentOffer, loans, creditProfile, credential } = data;
  const openLoan = loans.find((l) => OPEN_STATUSES.includes(l.status));
  const months = forecast?.months ?? [];
  const firstShortfall = months.find((m) => m.shortfall);

  return (
    <div className="page home">
      <section className="answer" aria-labelledby="answer-title">
        <h1 id="answer-title" className="answer__headline">
          <Headline months={months} firstShortfall={firstShortfall} hasForecast={Boolean(forecast)} />
        </h1>
        {forecast && (
          <p className="answer__balance">
            Balance <span className="figure">{usd.format(forecast.startingBalance)}</span>
            <span className="muted">
              {forecast.balanceAsOf ? ` as of ${dateTime.format(new Date(forecast.balanceAsOf))}` : ' (not synced yet)'}
            </span>
          </p>
        )}
        {months.length > 0 && (
          <figure className="answer__chart">
            <figcaption>
              Projected end-of-month balance
              {firstShortfall && <span className="figcaption__mark"> · first shortfall {monthLong(firstShortfall.month)} (dark red)</span>}
            </figcaption>
            <RunwayChart months={months} height={230} selected={firstShortfall?.month ?? null} />
          </figure>
        )}
        <Link to="/forecast" className="text-link">
          See the month-by-month forecast <ArrowRight aria-hidden="true" size={16} />
        </Link>
      </section>

      <div className="home__side">
        {openLoan ? (
          <ActiveLoanCard loan={openLoan} />
        ) : currentOffer ? (
          <OfferCard offer={currentOffer} onChanged={() => void refetch()} />
        ) : (
          <NoOffer status={creditProfile.offerStatus} cap={creditProfile.cap} firstShortfall={firstShortfall?.month} />
        )}

        <section className="stalk-preview" aria-labelledby="stalk-preview-title">
          <div className="stalk-preview__text">
            <h2 id="stalk-preview-title">Your beanstalk</h2>
            <p>
              Tier {credential.tier} · limit <span className="figure">{usdWhole.format(credential.cap)}</span>
            </p>
            <p className="muted">
              {credential.nextTierAt === null
                ? 'Highest tier reached.'
                : `${credential.nextTierAt - credential.effectiveOnTime} more on-time repayment${
                    credential.nextTierAt - credential.effectiveOnTime === 1 ? '' : 's'
                  } to Tier ${credential.tier + 1}.`}
            </p>
            <Link to="/beanstalk" className="text-link">
              How your beanstalk grows <ArrowRight aria-hidden="true" size={16} />
            </Link>
          </div>
          <Beanstalk
            variant="preview"
            onTime={credential.onTimeRepayments}
            defaults={credential.defaults}
            claimed={Boolean(openLoan)}
            tier={credential.tier}
            tierCaps={TIER_CAPS}
          />
        </section>
      </div>
    </div>
  );
}

function Headline({
  months,
  firstShortfall,
  hasForecast,
}: {
  months: { month: string; endingBalance: number }[];
  firstShortfall?: { month: string; endingBalance: number };
  hasForecast: boolean;
}) {
  if (!hasForecast || months.length === 0) {
    return <>Sync your bank data to see the months ahead.</>;
  }
  if (!firstShortfall) {
    return <>You're covered for the next 12 months.</>;
  }
  const short = <strong className="neg">{usd.format(-firstShortfall.endingBalance)} short</strong>;
  if (firstShortfall.month === months[0].month) {
    return (
      <>
        Next month, {monthLong(firstShortfall.month)}, you're projected to be {short}.
      </>
    );
  }
  return (
    <>
      You're covered until {monthLong(addMonths(firstShortfall.month, -1))}. In {monthLong(firstShortfall.month)} you're
      projected to be {short}.
    </>
  );
}

function NoOffer({ status, cap, firstShortfall }: { status: string; cap: number; firstShortfall?: string }) {
  const copy: Record<string, { title: string; text: string }> = {
    NO_SHORTFALL: { title: 'No loan needed', text: "Your projected balance stays above zero. We'll let you know if that changes." },
    EXCEEDS_TIER_CAP: {
      title: 'Above your current limit',
      text: `The projected shortfall${firstShortfall ? ` in ${monthLong(firstShortfall)}` : ''} is larger than your ${usdWhole.format(cap)} limit. Repaying on time grows your beanstalk and raises it.`,
    },
    NO_REPAYMENT_MONTH: {
      title: 'No safe repayment month yet',
      text: "None of the next three months has room to repay a loan, so we won't offer one. Adding expected income helps.",
    },
    OFFER_AVAILABLE: { title: 'Preparing your offer…', text: 'This takes a moment after your forecast changes.' },
  };
  const c = copy[status] ?? { title: 'No offer right now', text: '' };
  return (
    <section className="calm-card" aria-labelledby="no-offer-title">
      <h2 id="no-offer-title">{c.title}</h2>
      <p>{c.text}</p>
      {status === 'OFFER_AVAILABLE' && <RefreshCw className="spin" aria-hidden="true" size={18} />}
    </section>
  );
}

export function PageSkeleton() {
  return (
    <div className="page" aria-busy="true">
      <p className="muted">Loading your accounts…</p>
      <div className="skeleton skeleton--title" />
      <div className="skeleton skeleton--block" />
    </div>
  );
}
