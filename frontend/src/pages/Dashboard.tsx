import { useState, type FormEvent } from 'react';
import { gql, useApolloClient, useMutation, useQuery } from '@apollo/client';
import { useNavigate } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { clearToken } from '../auth';
import LoansPanel from '../components/LoansPanel';

const DASHBOARD = gql`
  query Dashboard($months: Int) {
    me {
      email
    }
    forecast(months: $months) {
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
    recurringExpenses {
      id
      name
      amount
      category
      dayOfMonth
      startMonth
      durationMonths
      endMonth
    }
    categories
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

const CREATE_EXPENSE = gql`
  mutation CreateExpense($input: RecurringExpenseInput!) {
    createRecurringExpense(input: $input) {
      id
    }
  }
`;

const DELETE_EXPENSE = gql`
  mutation DeleteExpense($id: ID!) {
    deleteRecurringExpense(id: $id)
  }
`;

const DRAFT_EXPENSES = gql`
  mutation DraftExpenses($text: String!) {
    draftRecurringExpenses(text: $text) {
      name
      amount
      category
      dayOfMonth
      startMonth
      durationMonths
      source
    }
  }
`;

type ExpenseDraft = {
  name: string;
  amount: number;
  category: string;
  dayOfMonth: number;
  startMonth: string;
  durationMonths: number | null;
  source: string;
};

type Line = { label: string; category: string; amount: number; source: string };
type ForecastMonth = {
  month: string;
  income: number;
  expenses: number;
  net: number;
  endingBalance: number;
  shortfall: boolean;
  lines: Line[];
};
type RecurringExpense = {
  id: string;
  name: string;
  amount: number;
  category: string;
  dayOfMonth: number;
  startMonth: string;
  durationMonths: number | null;
  endMonth: string | null;
};
type DashboardData = {
  me: { email: string } | null;
  forecast: {
    currentMonth: string;
    startingBalance: number;
    balanceAsOf: string | null;
    discretionaryMonthly: number;
    explanation: { text: string; source: string };
    months: ForecastMonth[];
  } | null;
  recurringExpenses: RecurringExpense[];
  categories: string[];
};

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const RED = '#c0392b';
const GREEN = '#1e8449';

function monthLabel(ym: string) {
  const [y, m] = ym.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, 1)).toLocaleDateString('en-US', { month: 'short', year: '2-digit', timeZone: 'UTC' });
}

function nextMonth(ym: string) {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(Date.UTC(y, m, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

export default function Dashboard() {
  const navigate = useNavigate();
  const client = useApolloClient();
  const { data, loading, error, refetch } = useQuery<DashboardData>(DASHBOARD, { variables: { months: 12 } });
  const [sync, syncState] = useMutation<{ syncTransactions: { fetched: number; inserted: number } }>(SYNC);
  const [createExpense, createState] = useMutation(CREATE_EXPENSE);
  const [deleteExpense] = useMutation(DELETE_EXPENSE);
  const [selected, setSelected] = useState<string | null>(null);
  const [syncMessage, setSyncMessage] = useState<string | null>(null);

  async function logout() {
    clearToken();
    await client.clearStore();
    navigate('/login', { replace: true });
  }

  async function onSync() {
    const result = await sync();
    const r = result.data?.syncTransactions;
    setSyncMessage(r ? `Synced: ${r.inserted} new of ${r.fetched} transactions` : null);
    await refetch();
  }

  async function onDelete(id: string) {
    await deleteExpense({ variables: { id } });
    await refetch();
  }

  if (loading) return <main className="card">Loading…</main>;
  if (error || !data) return <main className="card error">Could not load your dashboard.</main>;

  const forecast = data.forecast;
  const months = forecast?.months ?? [];
  const selectedMonth = months.find((m) => m.month === selected) ?? null;
  const shortfalls = months.filter((m) => m.shortfall);

  return (
    <main className="dashboard">
      <header className="dash-header">
        <div>
          <h1>Cash-flow forecast</h1>
          <p className="muted">{data.me?.email}</p>
        </div>
        <div className="actions">
          <button onClick={onSync} disabled={syncState.loading}>
            {syncState.loading ? 'Syncing…' : 'Sync bank data'}
          </button>
          <button className="secondary" onClick={logout}>
            Sign out
          </button>
        </div>
      </header>
      {syncMessage && <p className="muted">{syncMessage}</p>}

      {forecast && (
        <section className="panel">
          <div className="stats">
            <div>
              <span className="muted">Current balance</span>
              <strong>{usd.format(forecast.startingBalance)}</strong>
              <span className="muted small">
                {forecast.balanceAsOf ? `as of ${new Date(forecast.balanceAsOf).toLocaleString()}` : 'not synced yet'}
              </span>
            </div>
            <div>
              <span className="muted">Months below $0</span>
              <strong className={shortfalls.length ? 'neg' : 'pos'}>{shortfalls.length}</strong>
              <span className="muted small">
                {shortfalls.length ? `first: ${monthLabel(shortfalls[0].month)}` : 'none in the next 12 months'}
              </span>
            </div>
            <div>
              <span className="muted">Everyday spending</span>
              <strong>{usd.format(forecast.discretionaryMonthly)}</strong>
              <span className="muted small">per month (median)</span>
            </div>
          </div>

          <div className="explanation">
            <span className="badge">{forecast.explanation.source === 'AI' ? 'AI summary' : 'Summary'}</span>
            <p>{forecast.explanation.text}</p>
          </div>

          <h2>Projected end-of-month balance</h2>
          <div className="chart">
            <ResponsiveContainer width="100%" height={280}>
              <BarChart
                data={months.map((m) => ({ ...m, label: monthLabel(m.month) }))}
                onClick={(e) => {
                  const idx = e?.activeTooltipIndex;
                  if (typeof idx === 'number' && months[idx]) setSelected(months[idx].month);
                }}
              >
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis dataKey="label" />
                <YAxis tickFormatter={(v: number) => usd.format(v).replace('.00', '')} width={80} />
                <Tooltip formatter={(v: number) => usd.format(v)} />
                <ReferenceLine y={0} stroke="#333" />
                <Bar dataKey="endingBalance" name="Ending balance" cursor="pointer">
                  {months.map((m) => (
                    <Cell key={m.month} fill={m.shortfall ? RED : GREEN} opacity={selected && selected !== m.month ? 0.5 : 1} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>

          <table>
            <thead>
              <tr>
                <th>Month</th>
                <th>Income</th>
                <th>Expenses</th>
                <th>Net</th>
                <th>Ending balance</th>
              </tr>
            </thead>
            <tbody>
              {months.map((m) => (
                <tr
                  key={m.month}
                  className={`${m.shortfall ? 'row-neg' : ''} ${selected === m.month ? 'row-selected' : ''}`}
                  onClick={() => setSelected(selected === m.month ? null : m.month)}
                >
                  <td>{monthLabel(m.month)}</td>
                  <td>{usd.format(m.income)}</td>
                  <td>{usd.format(m.expenses)}</td>
                  <td className={m.net < 0 ? 'neg' : 'pos'}>{usd.format(m.net)}</td>
                  <td className={m.shortfall ? 'neg' : ''}>{usd.format(m.endingBalance)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {selectedMonth && (
            <div className="breakdown">
              <h3>{monthLabel(selectedMonth.month)} breakdown</h3>
              <ul>
                {[...selectedMonth.lines]
                  .sort((a, b) => a.amount - b.amount)
                  .map((l, i) => (
                    <li key={`${l.label}-${i}`}>
                      <span>
                        {l.label} <span className="muted small">· {l.category} · {l.source.toLowerCase().replace('_', ' ')}</span>
                      </span>
                      <span className={l.amount < 0 ? 'neg' : 'pos'}>{usd.format(l.amount)}</span>
                    </li>
                  ))}
              </ul>
            </div>
          )}
        </section>
      )}

      <LoansPanel onChanged={() => void refetch()} />

      <section className="panel">
        <h2>Recurring expenses</h2>
        {data.recurringExpenses.length === 0 ? (
          <p className="muted">None yet. Add things like “Netflix for 12 months” or “Car loan for 36 months”.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>Amount</th>
                <th>Category</th>
                <th>Runs</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.recurringExpenses.map((e) => (
                <tr key={e.id}>
                  <td>{e.name}</td>
                  <td>{usd.format(e.amount)}</td>
                  <td>{e.category}</td>
                  <td>
                    {monthLabel(e.startMonth)} → {e.endMonth ? monthLabel(e.endMonth) : 'ongoing'} (day {e.dayOfMonth})
                  </td>
                  <td>
                    <button className="link" onClick={() => onDelete(e.id)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <DraftAssistant
          onConfirm={async (draft) => {
            const { source: _source, ...input } = draft;
            await createExpense({ variables: { input } });
            await refetch();
          }}
        />
        <ExpenseForm
          categories={data.categories}
          defaultStart={forecast ? nextMonth(forecast.currentMonth) : ''}
          submitting={createState.loading}
          error={createState.error?.message ?? null}
          onSubmit={async (input) => {
            await createExpense({ variables: { input } });
            await refetch();
          }}
        />
      </section>
    </main>
  );
}

function DraftAssistant({ onConfirm }: { onConfirm: (draft: ExpenseDraft) => Promise<void> }) {
  const [text, setText] = useState('');
  const [drafts, setDrafts] = useState<ExpenseDraft[]>([]);
  const [draft, draftState] = useMutation<{ draftRecurringExpenses: ExpenseDraft[] }>(DRAFT_EXPENSES);
  const [busy, setBusy] = useState<number | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      const result = await draft({ variables: { text } });
      setDrafts(result.data?.draftRecurringExpenses ?? []);
    } catch {
      // Error is surfaced via the mutation state.
    }
  }

  async function confirm(i: number) {
    setBusy(i);
    try {
      await onConfirm(drafts[i]);
      setDrafts((d) => d.filter((_, j) => j !== i));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="assistant">
      <form onSubmit={submit}>
        <label>
          Describe your recurring expenses
          <textarea
            rows={2}
            maxLength={500}
            placeholder="e.g. Netflix 15.49 for 12 months, car payment $385 on the 5th for 3 years"
            value={text}
            onChange={(e) => setText(e.target.value)}
            required
          />
        </label>
        <button type="submit" disabled={draftState.loading || !text.trim()}>
          {draftState.loading ? 'Reading…' : 'Suggest expenses'}
        </button>
      </form>
      {draftState.error && <p className="error">{draftState.error.message}</p>}
      {draftState.called && !draftState.loading && !draftState.error && drafts.length === 0 && (
        <p className="muted">No expenses with an amount were found.</p>
      )}
      {drafts.length > 0 && (
        <ul className="drafts">
          {drafts.map((d, i) => (
            <li key={`${d.name}-${i}`}>
              <span>
                <strong>{d.name}</strong> {usd.format(d.amount)}/mo · {d.category} · from {monthLabel(d.startMonth)}
                {d.durationMonths ? ` for ${d.durationMonths} months` : ', ongoing'} · day {d.dayOfMonth}{' '}
                <span className="badge">{d.source === 'AI' ? 'AI' : 'rules'}</span>
              </span>
              <span className="actions">
                <button onClick={() => confirm(i)} disabled={busy !== null}>
                  {busy === i ? 'Adding…' : 'Add'}
                </button>
                <button className="secondary" onClick={() => setDrafts((x) => x.filter((_, j) => j !== i))}>
                  Discard
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

type ExpenseInput = {
  name: string;
  amount: number;
  category: string;
  dayOfMonth: number;
  startMonth: string;
  durationMonths: number | null;
};

function ExpenseForm({
  categories,
  defaultStart,
  submitting,
  error,
  onSubmit,
}: {
  categories: string[];
  defaultStart: string;
  submitting: boolean;
  error: string | null;
  onSubmit: (input: ExpenseInput) => Promise<void>;
}) {
  const [name, setName] = useState('');
  const [amount, setAmount] = useState('');
  const [category, setCategory] = useState('Subscriptions');
  const [dayOfMonth, setDayOfMonth] = useState('1');
  const [startMonth, setStartMonth] = useState(defaultStart);
  const [duration, setDuration] = useState('');

  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      await onSubmit({
        name,
        amount: Number(amount),
        category,
        dayOfMonth: Number(dayOfMonth),
        startMonth,
        durationMonths: duration ? Number(duration) : null,
      });
      setName('');
      setAmount('');
      setDuration('');
    } catch {
      // Error is surfaced via the mutation state.
    }
  }

  return (
    <form className="expense-form" onSubmit={submit}>
      <input placeholder="Name (e.g. Netflix)" value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
      <input
        placeholder="Amount"
        type="number"
        min="0.01"
        step="0.01"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        required
      />
      <select value={category} onChange={(e) => setCategory(e.target.value)} required>
        {categories.map((c) => (
          <option key={c} value={c}>
            {c}
          </option>
        ))}
      </select>
      <label className="inline">
        Day
        <input type="number" min="1" max="28" value={dayOfMonth} onChange={(e) => setDayOfMonth(e.target.value)} required />
      </label>
      <label className="inline">
        Start
        <input type="month" value={startMonth} onChange={(e) => setStartMonth(e.target.value)} required />
      </label>
      <input
        placeholder="Months (blank = ongoing)"
        type="number"
        min="1"
        max="600"
        value={duration}
        onChange={(e) => setDuration(e.target.value)}
      />
      <button type="submit" disabled={submitting}>
        {submitting ? 'Adding…' : 'Add expense'}
      </button>
      {error && <p className="error">{error}</p>}
    </form>
  );
}
