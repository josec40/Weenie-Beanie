import { useSearchParams } from 'react-router-dom';
import { gql, useMutation, useQuery } from '@apollo/client';
import { RefreshCw, Sparkles } from 'lucide-react';
import RunwayChart from '../components/RunwayChart';
import { dateTime, monthLong, monthShort, usd } from '../lib/format';
import { PageSkeleton } from './Home';

const FORECAST = gql`
  query ForecastPage {
    forecast(months: 12) {
      currentMonth
      startingBalance
      balanceAsOf
      discretionaryMonthly
      explanation {
        text
        source
      }
      months {
        month
        income
        expenses
        net
        endingBalance
        shortfall
        lines {
          label
          category
          amount
          source
        }
      }
    }
  }
`;

const SYNC = gql`
  mutation Sync {
    syncTransactions {
      fetched
      inserted
    }
  }
`;

type Line = { label: string; category: string; amount: number; source: string };
type Month = {
  month: string;
  income: number;
  expenses: number;
  net: number;
  endingBalance: number;
  shortfall: boolean;
  lines: Line[];
};
type ForecastData = {
  forecast: {
    currentMonth: string;
    startingBalance: number;
    balanceAsOf: string | null;
    discretionaryMonthly: number;
    explanation: { text: string; source: string };
    months: Month[];
  } | null;
};

const SOURCE_LABEL: Record<string, string> = {
  MONTHLY: 'Monthly pattern',
  BIWEEKLY: 'Every two weeks',
  WEEKLY: 'Weekly pattern',
  ANNUAL: 'Once a year',
  RECURRING_EXPENSE: 'Your recurring expense',
  DISCRETIONARY: 'Everyday spending',
  LOAN: 'Micro-loan',
};

export default function Forecast() {
  const { data, loading, error, refetch } = useQuery<ForecastData>(FORECAST, { pollInterval: 30000 });
  const [sync, syncState] = useMutation<{ syncTransactions: { fetched: number; inserted: number } }>(SYNC);
  const [params, setParams] = useSearchParams();
  const selected = params.get('month');

  if (loading && !data) return <PageSkeleton />;
  if (error || !data) {
    return (
      <div className="page">
        <p className="form-error" role="alert">
          We couldn't load your forecast. Try again in a moment.
        </p>
      </div>
    );
  }

  const forecast = data.forecast;
  const select = (month: string | null) => {
    const next = new URLSearchParams(params);
    if (month && month !== selected) next.set('month', month);
    else next.delete('month');
    setParams(next, { replace: true });
  };

  async function onSync() {
    await sync();
    await refetch();
  }

  const synced = syncState.data?.syncTransactions;
  const months = forecast?.months ?? [];
  const selectedMonth = months.find((m) => m.month === selected) ?? null;

  return (
    <div className="page forecast">
      <div className="page__head">
        <h1>Forecast</h1>
        <button type="button" className="button button--secondary" onClick={() => void onSync()} disabled={syncState.loading}>
          <RefreshCw aria-hidden="true" size={16} className={syncState.loading ? 'spin' : undefined} />
          {syncState.loading ? 'Syncing…' : 'Sync Bank Data'}
        </button>
      </div>
      <p className="muted" role="status">
        {synced
          ? `Synced: ${synced.inserted} new of ${synced.fetched} transactions.`
          : forecast?.balanceAsOf
            ? `Balance ${usd.format(forecast.startingBalance)} as of ${dateTime.format(new Date(forecast.balanceAsOf))}.${forecast.discretionaryMonthly > 0 ? ` Everyday spending about ${usd.format(forecast.discretionaryMonthly)} a month.` : ''}`
            : 'Not synced yet.'}
      </p>

      {!forecast ? (
        <p>Sync your bank data to build a forecast.</p>
      ) : (
        <>
          <section className="summary" aria-labelledby="summary-title">
            <h2 id="summary-title" className="summary__title">
              <Sparkles aria-hidden="true" size={16} />
              {forecast.explanation.source === 'AI' ? 'Summary written by AI from your figures' : 'Summary'}
            </h2>
            <p>{forecast.explanation.text}</p>
          </section>

          <figure className="panel">
            <figcaption>Projected end-of-month balance. Select a month for its breakdown.</figcaption>
            <RunwayChart months={months} height={300} selected={selected} onSelect={(m) => select(m)} />
          </figure>

          <div className="forecast__grid">
            <div className="panel table-wrap">
              <table className="data-table">
                <caption className="visually-hidden">Monthly forecast</caption>
                <thead>
                  <tr>
                    <th scope="col">Month</th>
                    <th scope="col" className="num hide-sm">Income</th>
                    <th scope="col" className="num hide-sm">Expenses</th>
                    <th scope="col" className="num">Net</th>
                    <th scope="col" className="num">Ending balance</th>
                  </tr>
                </thead>
                <tbody>
                  {months.map((m) => (
                    <tr key={m.month} className={`${m.shortfall ? 'is-short' : ''} ${selected === m.month ? 'is-selected' : ''}`}>
                      <th scope="row">
                        <button type="button" className="row-button" aria-pressed={selected === m.month} onClick={() => select(m.month)}>
                          {monthShort(m.month)}
                        </button>
                      </th>
                      <td className="num hide-sm">{usd.format(m.income)}</td>
                      <td className="num hide-sm">{usd.format(m.expenses)}</td>
                      <td className={`num ${m.net < 0 ? 'neg' : ''}`}>{usd.format(m.net)}</td>
                      <td className={`num ${m.shortfall ? 'neg' : ''}`}>{usd.format(m.endingBalance)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <section className="panel breakdown" aria-live="polite" aria-labelledby="breakdown-title">
              {selectedMonth ? (
                <>
                  <h2 id="breakdown-title">{monthLong(selectedMonth.month)}</h2>
                  <ul>
                    {[...selectedMonth.lines]
                      .sort((a, b) => a.amount - b.amount)
                      .map((l, i) => (
                        <li key={`${l.label}-${i}`}>
                          <span className="breakdown__label">
                            {l.label}
                            <span className="muted">
                              {l.category} · {SOURCE_LABEL[l.source] ?? l.source.toLowerCase()}
                            </span>
                          </span>
                          <span className={`figure ${l.amount < 0 ? 'neg' : 'pos'}`}>{usd.format(l.amount)}</span>
                        </li>
                      ))}
                  </ul>
                </>
              ) : (
                <>
                  <h2 id="breakdown-title">Month breakdown</h2>
                  <p className="muted">Select a month in the chart or table to see what goes in and out.</p>
                </>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}
