import { useState } from 'react';
import { Link } from 'react-router-dom';
import { gql, useQuery } from '@apollo/client';
import { ArrowRight, ChartColumn, ReceiptText, RefreshCw } from 'lucide-react';
import Beanstalk from '../components/Beanstalk';
import { ActionList, ActionRow, BeanIcon, HeroLeaves, ProgressTrack } from '../components/BeanWorld';
import { ActiveLoanCard, LOAN_FIELDS, OPEN_STATUSES, OfferCard, type Loan, type Offer } from '../components/LoanCards';
import RunwayChart, { type RunwayMonth } from '../components/RunwayChart';
import PurchaseCheck from '../components/PurchaseCheck';
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
    recurringExpenses {
      id
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
  recurringExpenses: { id: string }[];
};

export const TIER_CAPS = [200, 500, 1000];

export default function Home() {
  const { data, loading, error, refetch } = useQuery<HomeData>(HOME, { pollInterval: 30000 });
  const [whatIfMonths, setWhatIfMonths] = useState<RunwayMonth[] | null>(null);

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

  const { forecast, currentOffer, loans, creditProfile, credential, recurringExpenses } = data;
  const openLoan = loans.find((l) => OPEN_STATUSES.includes(l.status));
  const months = forecast?.months ?? [];
  const firstShortfall = months.find((m) => m.shortfall);
  const shortMonths = months.filter((m) => m.shortfall).length;
  const toNext = credential.nextTierAt === null ? 0 : credential.nextTierAt - credential.effectiveOnTime;

  return (
    <div className="page home">
      <section className="hero" aria-labelledby="answer-title">
        <div className="hero__inner">
          <div className="hero__text">
            <p className="hero__greeting">{greeting()}</p>
            <h1 id="answer-title" className="hero__headline">
              <Headline months={months} firstShortfall={firstShortfall} hasForecast={Boolean(forecast)} />
            </h1>
            {forecast && (
              <p className="hero__balance">
                Balance <span className="figure">{usd.format(forecast.startingBalance)}</span>
                {forecast.balanceAsOf ? ` as of ${dateTime.format(new Date(forecast.balanceAsOf))}` : ' (not synced yet)'}
              </p>
            )}
          </div>
          <div className="hero__brandscape">
            <div className="hero__capital-one">
              <img src="/brand/capital-one.png" alt="Capital One" />
            </div>
            <HeroLeaves />
          </div>
        </div>
      </section>

      <div className="home__grid">
        <div className="home__main">
          {months.length > 0 && (
            <figure className="card answer__chart">
              <figcaption>
                Projected end-of-month balance
                {firstShortfall && <span className="figcaption__mark"> · first shortfall {monthLong(firstShortfall.month)}</span>}
              </figcaption>
              <RunwayChart months={months} whatIfMonths={whatIfMonths} height={250} selected={firstShortfall?.month ?? null} />
            </figure>
          )}

          <PurchaseCheck onWhatIfMonths={setWhatIfMonths} onLoanAccepted={() => void refetch()} />

          <ActionList label="Shortcuts">
            <ActionRow
              to="/forecast"
              icon={ChartColumn}
              verb="See"
              rest="your 12-month forecast"
              amount={shortMonths ? <span className="neg">{shortMonths} short</span> : 'All covered'}
            />
            <ActionRow
              to="/loans"
              tone="bean"
              tile={<BeanIcon />}
              verb="Track"
              rest="your micro-loans"
              amount={openLoan ? usd.format(openLoan.totalDue) : 'None open'}
            />
            <ActionRow
              to="/expenses"
              icon={ReceiptText}
              verb="Plan"
              rest="your recurring expenses"
              amount={`${recurringExpenses.length}`}
            />
          </ActionList>
        </div>

        <div className="home__side">
          {openLoan ? (
            <ActiveLoanCard loan={openLoan} />
          ) : currentOffer ? (
            <OfferCard offer={currentOffer} onChanged={() => void refetch()} />
          ) : (
            <NoOffer status={creditProfile.offerStatus} cap={creditProfile.cap} firstShortfall={firstShortfall?.month} />
          )}

          <Link to="/beanstalk" className="card card--link stalk-preview" aria-label={`Your beanstalk: Tier ${credential.tier}, limit ${usdWhole.format(credential.cap)}. Open your beanstalk.`}>
            <div className="stalk-preview__text">
              <h2>Your beanstalk</h2>
              <p>
                Tier {credential.tier} · limit <span className="figure">{usdWhole.format(credential.cap)}</span>
              </p>
              <ProgressTrack
                label="Progress to the next tier"
                value={credential.effectiveOnTime}
                max={credential.nextTierAt ?? Math.max(1, credential.effectiveOnTime)}
                bubble={`${credential.effectiveOnTime} ${credential.effectiveOnTime === 1 ? 'leaf' : 'leaves'}`}
                start={`Tier ${credential.tier}`}
                end={credential.nextTierAt === null ? 'Top tier' : `${toNext} until Tier ${credential.tier + 1}`}
              />
              <span className="text-link">
                How it grows <ArrowRight aria-hidden="true" size={16} />
              </span>
            </div>
            <Beanstalk
              variant="preview"
              onTime={credential.onTimeRepayments}
              defaults={credential.defaults}
              claimed={Boolean(openLoan)}
              tier={credential.tier}
              tierCaps={TIER_CAPS}
            />
          </Link>
        </div>
      </div>
    </div>
  );
}

function greeting() {
  const h = new Date().getHours();
  return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
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
