import { useEffect, useState } from 'react';
import { gql, useMutation, useQuery } from '@apollo/client';
import { ExternalLink, FastForward, Presentation, RotateCcw, X } from 'lucide-react';
import { monthLong } from '../lib/format';

export const DEMO_STATUS = gql`
  query DemoStatus {
    demoStatus {
      isDemoUser
      resetState
      resetMessage
      bankUrl
      poolUrl
      reputationConfigUrl
      clock {
        currentMonth
        offset
        enabled
      }
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

const RESET = gql`
  mutation ResetDemo {
    resetDemo {
      resetState
      resetMessage
    }
  }
`;

type DemoStatus = {
  isDemoUser: boolean;
  resetState: 'IDLE' | 'RUNNING' | 'DONE' | 'FAILED';
  resetMessage: string | null;
  bankUrl: string | null;
  poolUrl: string | null;
  reputationConfigUrl: string | null;
  clock: { currentMonth: string; offset: number; enabled: boolean };
};

/** Presenter-only controls for the scripted demo; not part of the customer experience. */
export default function DemoPanel() {
  const [open, setOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { data, refetch, startPolling, stopPolling } = useQuery<{ demoStatus: DemoStatus }>(DEMO_STATUS);
  const [advance, advanceState] = useMutation(ADVANCE);
  const [reset] = useMutation(RESET);
  const status = data?.demoStatus;
  const resetting = status?.resetState === 'RUNNING';

  useEffect(() => {
    if (resetting) startPolling(2000);
    else stopPolling();
  }, [resetting, startPolling, stopPolling]);

  if (!status?.isDemoUser) return null;

  async function run(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong');
    }
  }

  async function onReset() {
    setConfirming(false);
    await run(async () => {
      await reset();
      await refetch();
    });
  }

  return (
    <div className="demo">
      {!open ? (
        <button type="button" className="demo__toggle" onClick={() => setOpen(true)} aria-expanded={false}>
          <Presentation aria-hidden="true" size={16} />
          <span>Presenter Tools</span>
        </button>
      ) : (
        <aside className="demo__panel" aria-label="Presenter tools">
          <header>
            <h2>Presenter Tools</h2>
            <button type="button" className="icon-button" aria-label="Close presenter tools" onClick={() => setOpen(false)}>
              <X aria-hidden="true" size={16} />
            </button>
          </header>
          <p className="demo__month">
            Simulated month <strong>{monthLong(status.clock.currentMonth)}</strong>
          </p>
          <div className="demo__actions">
            <button
              type="button"
              className="button"
              disabled={advanceState.loading || resetting}
              onClick={() => void run(async () => {
                await advance();
                await refetch();
              })}
            >
              <FastForward aria-hidden="true" size={16} />
              {advanceState.loading ? 'Advancing…' : 'Advance One Month'}
            </button>
            {!confirming ? (
              <button type="button" className="button button--ghost-dark" disabled={resetting} onClick={() => setConfirming(true)}>
                <RotateCcw aria-hidden="true" size={16} />
                Reset Demo
              </button>
            ) : (
              <div className="demo__confirm" role="group" aria-label="Confirm demo reset">
                <p>This clears Alex's loans, offers, notifications and wallet, re-seeds bank history (about a minute) and rewinds the clock.</p>
                <div>
                  <button type="button" className="button button--danger" onClick={() => void onReset()}>
                    Reset Demo
                  </button>
                  <button type="button" className="button button--ghost-dark" onClick={() => setConfirming(false)}>
                    Cancel
                  </button>
                </div>
              </div>
            )}
          </div>
          {(resetting || status.resetState === 'FAILED' || status.resetState === 'DONE') && status.resetMessage && (
            <p className={`demo__status demo__status--${status.resetState.toLowerCase()}`} role="status">
              {status.resetMessage}
            </p>
          )}
          {error && (
            <p className="demo__status demo__status--failed" role="alert">
              {error}
            </p>
          )}
          {(status.bankUrl || status.poolUrl || status.reputationConfigUrl) && (
            <ul className="demo__links">
              {status.bankUrl && <ChainLink href={status.bankUrl} label="Bank authority" />}
              {status.poolUrl && <ChainLink href={status.poolUrl} label="Loan pool" />}
              {status.reputationConfigUrl && <ChainLink href={status.reputationConfigUrl} label="Reputation config" />}
            </ul>
          )}
        </aside>
      )}
    </div>
  );
}

function ChainLink({ href, label }: { href: string; label: string }) {
  return (
    <li>
      <a href={href} target="_blank" rel="noopener noreferrer">
        {label} on Solana Explorer
        <ExternalLink aria-hidden="true" size={14} />
      </a>
    </li>
  );
}
