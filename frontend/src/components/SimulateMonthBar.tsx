import { useState } from 'react';
import { gql, useMutation, useQuery, useApolloClient } from '@apollo/client';
import { Calendar, FastForward, Sparkles, CheckCircle2 } from 'lucide-react';
import { monthLong } from '../lib/format';

export const DEMO_CLOCK = gql`
  query GetDemoClock {
    demoStatus {
      isDemoUser
      clock {
        currentMonth
        offset
        enabled
      }
    }
  }
`;

export const ADVANCE_MONTH = gql`
  mutation AdvanceMonth($months: Int) {
    advanceMonth(months: $months) {
      currentMonth
      offset
      enabled
    }
  }
`;

type ClockData = {
  demoStatus: {
    isDemoUser: boolean;
    clock: {
      currentMonth: string;
      offset: number;
      enabled: boolean;
    };
  };
};

type SimulateMonthBarProps = {
  onSuccess?: (message: string) => void;
};

export default function SimulateMonthBar({ onSuccess }: SimulateMonthBarProps) {
  const client = useApolloClient();
  const { data, loading: clockLoading } = useQuery<ClockData>(DEMO_CLOCK);
  const [advance, { loading: advancing }] = useMutation(ADVANCE_MONTH);
  const [simulatedFeedback, setSimulatedFeedback] = useState<string | null>(null);

  const clock = data?.demoStatus?.clock;
  if (!data?.demoStatus?.isDemoUser && !clock) return null;

  async function handleSimulate(months: number) {
    try {
      setSimulatedFeedback(null);
      const res = await advance({ variables: { months } });
      const newMonth = res.data?.advanceMonth?.currentMonth;

      // Immediately refetch all active queries on screen (Home, Forecast, Loans, Expenses, Beanstalk)
      await client.refetchQueries({ include: 'active' });

      const msg = months > 1
        ? `Advanced ${months} months to ${monthLong(newMonth ?? '')}! Aggregates realized and due loans settled.`
        : `Advanced 1 month to ${monthLong(newMonth ?? '')}! Aggregates realized and ledger updated.`;

      setSimulatedFeedback(msg);
      if (onSuccess) {
        onSuccess(msg);
      }
      setTimeout(() => setSimulatedFeedback(null), 5000);
    } catch (err) {
      console.error('Failed to simulate month:', err);
    }
  }

  return (
    <aside className="simulate-bar" aria-label="Demo Month Simulation Controls">
      <div className="simulate-bar__inner">
        <div className="simulate-bar__info">
          <span className="simulate-bar__badge">
            <Calendar size={14} aria-hidden="true" />
            <span>Simulated:</span>
            <strong>{clock ? monthLong(clock.currentMonth) : clockLoading ? 'Loading…' : 'Active'}</strong>
          </span>
          <span className="simulate-bar__caption hide-sm">
            TimescaleDB aggregates & loan settlement auto-execute on simulation
          </span>
        </div>

        {simulatedFeedback && (
          <div className="simulate-bar__feedback" role="status">
            <CheckCircle2 size={15} color="#2e7d4f" aria-hidden="true" />
            <span>{simulatedFeedback}</span>
          </div>
        )}

        <div className="simulate-bar__actions">
          <button
            type="button"
            className="simulate-bar__btn simulate-bar__btn--primary"
            disabled={advancing || clockLoading}
            onClick={() => void handleSimulate(1)}
            title="Simulate 1 month: posts income, updates TimescaleDB aggregates, and settles maturing loans"
          >
            <Sparkles size={14} aria-hidden="true" />
            {advancing ? 'Simulating…' : 'Simulate Month (+1)'}
          </button>

          <button
            type="button"
            className="simulate-bar__btn simulate-bar__btn--secondary"
            disabled={advancing || clockLoading}
            onClick={() => void handleSimulate(2)}
            title="Advance 2 months: fulfills 2-month loans and plants bean leaf"
          >
            <FastForward size={14} aria-hidden="true" />
            +2 Months
          </button>
        </div>
      </div>
    </aside>
  );
}
