import { useState, type FormEvent } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { gql, useMutation, useQuery } from '@apollo/client';
import { Sparkles, Trash2, ReceiptText, ShoppingBag, Search } from 'lucide-react';
import { addMonths, monthLong, monthShort, usd } from '../lib/format';
import { PageSkeleton } from './Home';

const EXPENSES = gql`
  query ExpensesPage {
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
    forecast(months: 1) {
      currentMonth
      startingBalance
    }
    transactions(from: "2026-01-01", to: "2027-12-31") {
      id
      date
      kind
      amount
      category
      description
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

type Expense = {
  id: string;
  name: string;
  amount: number;
  category: string;
  dayOfMonth: number;
  startMonth: string;
  durationMonths: number | null;
  endMonth: string | null;
};
type ExpenseInput = Omit<Expense, 'id' | 'endMonth'>;
type ExpenseDraft = ExpenseInput & { source: string };

type Transaction = {
  id: string;
  date: string;
  kind: string;
  amount: number;
  category: string;
  description: string | null;
};

export default function Expenses() {
  const [searchParams, setSearchParams] = useSearchParams();
  const currentTab = searchParams.get('tab') === 'recurring' ? 'recurring' : 'purchases';
  const [filterKind, setFilterKind] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');

  const { data, loading, error, refetch } = useQuery<{
    recurringExpenses: Expense[];
    categories: string[];
    forecast: { currentMonth: string; startingBalance: number } | null;
    transactions: Transaction[];
  }>(EXPENSES);
  const [createExpense, createState] = useMutation(CREATE_EXPENSE);
  const [deleteExpense] = useMutation(DELETE_EXPENSE);
  const [removing, setRemoving] = useState<string | null>(null);

  if (loading && !data) return <PageSkeleton />;
  if (error || !data) {
    return (
      <div className="page">
        <p className="form-error" role="alert">
          We couldn't load your expenses. Try again in a moment.
        </p>
      </div>
    );
  }

  async function add(input: ExpenseInput) {
    await createExpense({ variables: { input } });
    await refetch();
  }

  async function remove(id: string) {
    setRemoving(id);
    try {
      await deleteExpense({ variables: { id } });
      await refetch();
    } finally {
      setRemoving(null);
    }
  }

  const defaultStart = data.forecast ? addMonths(data.forecast.currentMonth, 1) : '';
  const balance = data.forecast?.startingBalance ?? 0;

  const rawTxns = data.transactions ?? [];
  const filteredTxns = rawTxns.filter((t) => {
    if (filterKind === 'PURCHASES' && t.kind !== 'PURCHASE') return false;
    if (filterKind === 'DEPOSITS' && t.kind !== 'DEPOSIT') return false;
    if (filterKind === 'WITHDRAWALS' && t.kind !== 'WITHDRAWAL') return false;
    if (searchTerm) {
      const term = searchTerm.toLowerCase();
      const descMatch = (t.description ?? '').toLowerCase().includes(term);
      const catMatch = (t.category ?? '').toLowerCase().includes(term);
      return descMatch || catMatch;
    }
    return true;
  });

  return (
    <div className="page expenses">
      <div className="page__head" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '14px' }}>
        <div>
          <h1 style={{ margin: 0 }}>Activity & Ledger</h1>
          <p className="muted" style={{ margin: '4px 0 0 0' }}>
            Track purchases, loan disbursements, repayments, and planned recurring expenses.
          </p>
        </div>

        <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
          <div
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '10px',
              padding: '8px 16px',
              borderRadius: 'var(--radius-sm)',
              background: '#ffffff',
              border: '1.5px solid var(--border)',
              boxShadow: 'var(--shadow-sm)',
            }}
          >
            <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--ink-2)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Checking Balance
            </span>
            <span style={{ fontSize: '1.25rem', fontWeight: 800, color: '#0e2a47' }}>
              {usd.format(balance)}
            </span>
          </div>
          <Link to="/store" className="button" style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
            <ShoppingBag size={16} />
            Bean Mart
          </Link>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '12px', borderBottom: '2px solid var(--border)', marginBottom: '20px' }}>
        <button
          type="button"
          onClick={() => setSearchParams({ tab: 'purchases' })}
          style={{
            padding: '10px 18px',
            fontWeight: 700,
            fontSize: '0.95rem',
            background: 'none',
            border: 'none',
            borderBottom: currentTab === 'purchases' ? '3px solid #0e2a47' : '3px solid transparent',
            color: currentTab === 'purchases' ? '#0e2a47' : 'var(--ink-2)',
            cursor: 'pointer',
            marginBottom: '-2px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <ReceiptText size={18} />
          Purchases & Ledger ({rawTxns.length})
        </button>
        <button
          type="button"
          onClick={() => setSearchParams({ tab: 'recurring' })}
          style={{
            padding: '10px 18px',
            fontWeight: 700,
            fontSize: '0.95rem',
            background: 'none',
            border: 'none',
            borderBottom: currentTab === 'recurring' ? '3px solid #0e2a47' : '3px solid transparent',
            color: currentTab === 'recurring' ? '#0e2a47' : 'var(--ink-2)',
            cursor: 'pointer',
            marginBottom: '-2px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          Recurring Expenses ({data.recurringExpenses.length})
        </button>
      </div>

      {currentTab === 'purchases' ? (
        <section className="panel" aria-labelledby="ledger-title">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', flexWrap: 'wrap', gap: '12px' }}>
            <div>
              <h2 id="ledger-title" style={{ margin: 0 }}>All Purchases & Account Transactions</h2>
              <p className="muted small" style={{ margin: '4px 0 0 0' }}>
                Complete audit trail of posted purchases, loans, and cash movements.
              </p>
            </div>

            <div style={{ display: 'flex', gap: '8px', alignItems: 'center', flexWrap: 'wrap' }}>
              <div style={{ position: 'relative', display: 'inline-flex', alignItems: 'center' }}>
                <Search size={16} color="var(--ink-2)" style={{ position: 'absolute', left: '10px' }} />
                <input
                  type="text"
                  placeholder="Search purchases…"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  style={{
                    padding: '6px 12px 6px 32px',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border)',
                    fontSize: '0.85rem',
                  }}
                />
              </div>

              <div style={{ display: 'inline-flex', borderRadius: 'var(--radius-sm)', border: '1px solid var(--border)', overflow: 'hidden' }}>
                {['ALL', 'PURCHASES', 'DEPOSITS', 'WITHDRAWALS'].map((k) => (
                  <button
                    key={k}
                    type="button"
                    onClick={() => setFilterKind(k)}
                    style={{
                      padding: '6px 12px',
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      background: filterKind === k ? '#0e2a47' : '#ffffff',
                      color: filterKind === k ? '#ffffff' : 'var(--ink-2)',
                      border: 'none',
                      cursor: 'pointer',
                    }}
                  >
                    {k === 'ALL' ? 'All' : k === 'PURCHASES' ? 'Purchases' : k === 'DEPOSITS' ? 'Deposits' : 'Withdrawals'}
                  </button>
                ))}
              </div>
            </div>
          </div>

          {filteredTxns.length === 0 ? (
            <div style={{ padding: '32px', textAlign: 'center', color: 'var(--ink-2)' }}>
              <p style={{ margin: 0, fontWeight: 500 }}>No transactions found matching your filter.</p>
            </div>
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th scope="col">Date</th>
                    <th scope="col">Description</th>
                    <th scope="col">Category</th>
                    <th scope="col">Kind</th>
                    <th scope="col" className="num">Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredTxns.map((t) => {
                    const isCredit = t.amount > 0;
                    return (
                      <tr key={t.id}>
                        <td className="nowrap" style={{ fontWeight: 500 }}>
                          {t.date}
                        </td>
                        <th scope="row" style={{ fontWeight: 600 }}>
                          {t.description || 'Transaction'}
                        </th>
                        <td>
                          <span className="status status--calm" style={{ fontSize: '0.8rem', padding: '2px 8px' }}>
                            {t.category}
                          </span>
                        </td>
                        <td>
                          <span
                            className={`status status--${t.kind === 'PURCHASE' ? 'warn' : t.kind === 'DEPOSIT' ? 'good' : 'calm'}`}
                            style={{ fontSize: '0.75rem', padding: '2px 8px', fontWeight: 700 }}
                          >
                            {t.kind}
                          </span>
                        </td>
                        <td className="num" style={{ fontWeight: 700, color: isCredit ? 'var(--green-700)' : '#991b1b' }}>
                          {isCredit ? `+${usd.format(t.amount)}` : `-${usd.format(Math.abs(t.amount))}`}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      ) : (
        <>
          <section className="panel" aria-labelledby="list-title">
            <h2 id="list-title">Your expenses</h2>
            {data.recurringExpenses.length === 0 ? (
              <p className="muted">None yet. Describe them below or add one by hand.</p>
            ) : (
              <div className="table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">Name</th>
                      <th scope="col" className="num">Amount</th>
                      <th scope="col" className="hide-sm">Category</th>
                      <th scope="col">Runs</th>
                      <th scope="col">
                        <span className="visually-hidden">Actions</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recurringExpenses.map((e) => (
                      <tr key={e.id}>
                        <th scope="row" className="truncate">
                          {e.name}
                        </th>
                        <td className="num">{usd.format(e.amount)}</td>
                        <td className="hide-sm">{e.category}</td>
                        <td className="nowrap">
                          {monthShort(e.startMonth)} – {e.endMonth ? monthShort(e.endMonth) : 'ongoing'}
                          <span className="muted small"> · day {e.dayOfMonth}</span>
                        </td>
                        <td className="num">
                          <button
                            type="button"
                            className="icon-button"
                            aria-label={`Remove ${e.name}`}
                            disabled={removing === e.id}
                            onClick={() => void remove(e.id)}
                          >
                            <Trash2 aria-hidden="true" size={18} />
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <DraftAssistant onConfirm={add} />

          <ExpenseForm
            categories={data.categories}
            defaultStart={defaultStart}
            submitting={createState.loading}
            error={createState.error?.message ?? null}
            onSubmit={add}
          />
        </>
      )}
    </div>
  );
}

function DraftAssistant({ onConfirm }: { onConfirm: (input: ExpenseInput) => Promise<void> }) {
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
      // Shown from the mutation's error state.
    }
  }

  async function confirm(i: number) {
    setBusy(i);
    try {
      const { source: _source, ...input } = drafts[i];
      await onConfirm(input);
      setDrafts((d) => d.filter((_, j) => j !== i));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="panel assistant" aria-labelledby="assistant-title">
      <h2 id="assistant-title">Describe them in your own words</h2>
      <form onSubmit={submit} className="stack">
        <label htmlFor="expense-text">What do you pay regularly, and for how long?</label>
        <textarea
          id="expense-text"
          name="expense-text"
          rows={3}
          maxLength={500}
          autoComplete="off"
          placeholder="e.g. Netflix 15.49 for 12 months, car payment $385 on the 5th for 3 years…"
          value={text}
          onChange={(e) => setText(e.target.value)}
          required
        />
        <div className="button-row">
          <button type="submit" className="button" disabled={draftState.loading || !text.trim()}>
            <Sparkles aria-hidden="true" size={16} />
            {draftState.loading ? 'Reading…' : 'Suggest Expenses'}
          </button>
          <span className="muted small">Nothing is saved until you add it.</span>
        </div>
      </form>
      {draftState.error && (
        <p className="form-error" role="alert">
          {draftState.error.message}
        </p>
      )}
      {draftState.called && !draftState.loading && !draftState.error && drafts.length === 0 && (
        <p className="muted">No expenses with an amount were found. Try including how much and how often.</p>
      )}
      {drafts.length > 0 && (
        <ul className="drafts" aria-live="polite">
          {drafts.map((d, i) => (
            <li key={`${d.name}-${i}`}>
              <div>
                <strong>{d.name}</strong> <span className="figure">{usd.format(d.amount)}</span> a month
                <span className="muted">
                  {' '}
                  · {d.category} · from {monthLong(d.startMonth)}
                  {d.durationMonths ? ` for ${d.durationMonths} months` : ', ongoing'} · day {d.dayOfMonth} ·{' '}
                  {d.source === 'AI' ? 'read by AI' : 'read by rules'}
                </span>
              </div>
              <div className="button-row">
                <button type="button" className="button button--small" onClick={() => void confirm(i)} disabled={busy !== null}>
                  {busy === i ? 'Adding…' : 'Add'}
                </button>
                <button
                  type="button"
                  className="button button--secondary button--small"
                  onClick={() => setDrafts((x) => x.filter((_, j) => j !== i))}
                >
                  Discard
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

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
  const [category, setCategory] = useState(categories.includes('Subscriptions') ? 'Subscriptions' : categories[0] ?? '');
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
      // Shown from the mutation's error state.
    }
  }

  return (
    <section className="panel" aria-labelledby="manual-title">
      <h2 id="manual-title">Add one by hand</h2>
      <form className="expense-form" onSubmit={submit}>
        <label className="field field--wide">
          <span>Name</span>
          <input name="expense-name" autoComplete="off" placeholder="e.g. Netflix…" value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
        </label>
        <label className="field">
          <span>Amount</span>
          <input name="expense-amount" type="number" inputMode="decimal" min="0.01" step="0.01" autoComplete="off" value={amount} onChange={(e) => setAmount(e.target.value)} required />
        </label>
        <label className="field">
          <span>Category</span>
          <select name="expense-category" value={category} onChange={(e) => setCategory(e.target.value)} required>
            {categories.map((c) => (
              <option key={c} value={c}>
                {c}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>Day of month</span>
          <input name="expense-day" type="number" inputMode="numeric" min="1" max="28" value={dayOfMonth} onChange={(e) => setDayOfMonth(e.target.value)} required />
        </label>
        <label className="field">
          <span>First month</span>
          <input name="expense-start" type="month" value={startMonth} onChange={(e) => setStartMonth(e.target.value)} required />
        </label>
        <label className="field">
          <span>Number of months</span>
          <input name="expense-months" type="number" inputMode="numeric" min="1" max="600" placeholder="Blank = ongoing" value={duration} onChange={(e) => setDuration(e.target.value)} />
        </label>
        <div className="field field--action">
          <button type="submit" className="button" disabled={submitting}>
            {submitting ? 'Adding…' : 'Add Expense'}
          </button>
        </div>
        {error && (
          <p className="form-error field--wide" role="alert">
            {error}
          </p>
        )}
      </form>
    </section>
  );
}
