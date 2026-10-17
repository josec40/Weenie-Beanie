import { useState } from 'react';
import { gql, useQuery } from '@apollo/client';
import { Check, Copy, ExternalLink } from 'lucide-react';
import Beanstalk from '../components/Beanstalk';
import { ProgressTrack } from '../components/BeanWorld';
import { OPEN_STATUSES } from '../components/LoanCards';
import { usd, usdWhole } from '../lib/format';
import { PageSkeleton, TIER_CAPS } from './Home';

const BEANSTALK = gql`
  query BeanstalkPage {
    credential {
      onChain
      tier
      cap
      nextTierAt
      effectiveOnTime
      onTimeRepayments
      lateRepayments
      defaults
      totalRepaid
      walletAddress
      walletUrl
      credentialMint
      credentialUrl
      credentialTxUrl
      verifyUrl
    }
    loans {
      id
      status
    }
  }
`;

type Credential = {
  onChain: boolean;
  tier: number;
  cap: number;
  nextTierAt: number | null;
  effectiveOnTime: number;
  onTimeRepayments: number;
  lateRepayments: number;
  defaults: number;
  totalRepaid: number;
  walletAddress: string | null;
  walletUrl: string | null;
  credentialMint: string | null;
  credentialUrl: string | null;
  credentialTxUrl: string | null;
  verifyUrl: string | null;
};

function short(address: string) {
  return `${address.slice(0, 4)}…${address.slice(-4)}`;
}

export default function BeanstalkPage() {
  const { data, loading, error } = useQuery<{ credential: Credential; loans: { id: string; status: string }[] }>(BEANSTALK, {
    pollInterval: 30000,
  });
  const [copied, setCopied] = useState(false);

  if (loading && !data) return <PageSkeleton />;
  if (error || !data) {
    return (
      <div className="page">
        <p className="form-error" role="alert">
          We couldn't load your beanstalk. Try again in a moment.
        </p>
      </div>
    );
  }

  const c = data.credential;
  const claimed = data.loans.some((l) => OPEN_STATUSES.includes(l.status));
  const toNext = c.nextTierAt === null ? 0 : c.nextTierAt - c.effectiveOnTime;

  async function copyVerify() {
    if (!c.verifyUrl) return;
    await navigator.clipboard.writeText(c.verifyUrl);
    setCopied(true);
    window.setTimeout(() => setCopied(false), 2000);
  }

  return (
    <div className="page beanstalk-page">
      <div className="beanstalk-page__head">
        <h1>Your beanstalk</h1>
        <p className="beanstalk-page__lead">
          Tier {c.tier} · micro-loan limit <span className="figure">{usdWhole.format(c.cap)}</span>
        </p>
        <p>
          {c.nextTierAt === null
            ? "You've reached the highest tier."
            : `${toNext} more on-time repayment${toNext === 1 ? '' : 's'} to reach Tier ${c.tier + 1} and a ${usdWhole.format(TIER_CAPS[c.tier + 1])} limit.`}
        </p>
        <ProgressTrack
          label="Progress to the next tier"
          value={c.effectiveOnTime}
          max={c.nextTierAt ?? Math.max(1, c.effectiveOnTime)}
          bubble={`${c.effectiveOnTime} ${c.effectiveOnTime === 1 ? 'leaf' : 'leaves'}`}
          start={`Tier ${c.tier} · ${usdWhole.format(c.cap)}`}
          end={c.nextTierAt === null ? 'Top tier' : `Tier ${c.tier + 1} · ${usdWhole.format(TIER_CAPS[c.tier + 1])}`}
        />
      </div>

      <div className="beanstalk-page__plant">
        <Beanstalk
          onTime={c.onTimeRepayments}
          defaults={c.defaults}
          claimed={claimed}
          tier={c.tier}
          tierCaps={TIER_CAPS}
        />
      </div>

      <div className="beanstalk-page__facts">
        <section aria-labelledby="how-title" className="panel">
          <h2 id="how-title">How it grows</h2>
          <ol className="grow-steps">
            <li>Accepting an offer claims a bean. It waits beside the soil until you repay.</li>
            <li>Repaying on time, in the month you agreed to, plants it and grows a leaf.</li>
            <li>Two leaves reach Tier 1 ($500); five reach Tier 2 ($1,000).</li>
            <li>A default drops two leaves. Late or pushed repayments plant the bean without a leaf.</li>
          </ol>
        </section>

        <section aria-labelledby="record-title" className="panel">
          <h2 id="record-title">Your record</h2>
          <dl className="record">
            <div>
              <dt>On time</dt>
              <dd className="figure">{c.onTimeRepayments}</dd>
            </div>
            <div>
              <dt>Late</dt>
              <dd className="figure">{c.lateRepayments}</dd>
            </div>
            <div>
              <dt>Defaults</dt>
              <dd className="figure">{c.defaults}</dd>
            </div>
            <div>
              <dt>Repaid</dt>
              <dd className="figure">{usd.format(c.totalRepaid)}</dd>
            </div>
          </dl>
        </section>

        <section aria-labelledby="credential-title" className="panel">
          <h2 id="credential-title">Repayment credential</h2>
          {!c.onChain ? (
            <p className="muted">The on-chain credential isn't enabled in this environment.</p>
          ) : (
            <>
              <p>
                {c.credentialUrl ? (
                  <>
                    Your non-transferable credential is minted to your wallet.{' '}
                    <a href={c.credentialUrl} target="_blank" rel="noopener noreferrer" className="inline-link">
                      View credential {short(c.credentialMint!)}
                      <ExternalLink aria-hidden="true" size={14} />
                    </a>
                  </>
                ) : c.onTimeRepayments > 0 ? (
                  'Your earlier on-time repayments are recorded. Your credential is minted when your next loan settles.'
                ) : (
                  'Your credential is minted after your first on-time repayment.'
                )}
              </p>
              <p className="muted small">
                Wallet{' '}
                <a href={c.walletUrl!} target="_blank" rel="noopener noreferrer" className="inline-link" translate="no">
                  {short(c.walletAddress!)}
                  <ExternalLink aria-hidden="true" size={14} />
                </a>{' '}
                (Solana devnet, held for you by the bank)
              </p>
              {c.verifyUrl && (
                <div className="verify">
                  <p>
                    Partners can check your tier and repayment record at this link. It shows no name, email or account
                    number.
                  </p>
                  <div className="button-row">
                    <a className="button button--secondary" href={c.verifyUrl} target="_blank" rel="noopener noreferrer">
                      Open Verification
                      <ExternalLink aria-hidden="true" size={16} />
                    </a>
                    <button type="button" className="button button--secondary" onClick={() => void copyVerify()}>
                      {copied ? <Check aria-hidden="true" size={16} /> : <Copy aria-hidden="true" size={16} />}
                      {copied ? 'Copied' : 'Copy Link'}
                    </button>
                  </div>
                  <span className="visually-hidden" aria-live="polite">
                    {copied ? 'Verification link copied' : ''}
                  </span>
                </div>
              )}
            </>
          )}
        </section>
      </div>
    </div>
  );
}
