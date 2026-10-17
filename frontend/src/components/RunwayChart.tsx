import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { monthLong, monthShort, usd, usdWhole } from '../lib/format';

export type RunwayMonth = { month: string; endingBalance: number; shortfall: boolean };

const INK = '#1f4e79';
const RED = '#b3261e';
const AMBER = '#d97706';
const AMBER_RED = '#991b1b';

/** Projected end-of-month balance per month; shortfall months in red, optional what-if series. */
export default function RunwayChart({
  months,
  whatIfMonths,
  height = 240,
  selected,
  onSelect,
}: {
  months: RunwayMonth[];
  whatIfMonths?: RunwayMonth[] | null;
  height?: number;
  selected?: string | null;
  onSelect?: (month: string) => void;
}) {
  const whatIfMap = new Map(whatIfMonths?.map((m) => [m.month, m]));
  const hasWhatIf = Boolean(whatIfMonths && whatIfMonths.length > 0);

  const data = months.map((m) => {
    const wi = whatIfMap.get(m.month);
    return {
      ...m,
      label: monthShort(m.month),
      whatIfEndingBalance: wi ? wi.endingBalance : undefined,
      whatIfShortfall: wi ? wi.shortfall : false,
    };
  });

  return (
    <div className="runway" role="img" aria-label={summary(months)}>
      {hasWhatIf && (
        <div style={{ display: 'flex', gap: '16px', fontSize: '0.8rem', fontWeight: 600, marginBottom: '8px' }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: 10, height: 10, borderRadius: 2, background: INK, display: 'inline-block' }} />
            Current baseline
          </span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: 10, height: 10, borderRadius: 2, background: AMBER, display: 'inline-block' }} />
            With purchase
          </span>
        </div>
      )}
      <ResponsiveContainer width="100%" height={height}>
        <BarChart
          data={data}
          margin={{ top: 8, right: 4, bottom: 0, left: 0 }}
          onClick={(e) => {
            const idx = e?.activeTooltipIndex;
            if (onSelect && typeof idx === 'number' && months[idx]) onSelect(months[idx].month);
          }}
        >
          <CartesianGrid vertical={false} stroke="#dfe5eb" />
          <XAxis dataKey="label" tickLine={false} axisLine={false} tick={{ fill: '#44546a', fontSize: 12 }} interval="preserveStartEnd" minTickGap={6} />
          <YAxis
            tickFormatter={(v: number) => usdWhole.format(v)}
            width={64}
            tickLine={false}
            axisLine={false}
            tick={{ fill: '#44546a', fontSize: 12 }}
          />
          <Tooltip
            cursor={{ fill: 'rgba(14, 42, 71, 0.06)', radius: 6 } as object}
            contentStyle={{ borderRadius: 12, border: '1px solid #dbe3eb', boxShadow: '0 8px 24px rgba(14, 42, 71, 0.12)' }}
            formatter={(v: unknown, name: unknown) => [
              usd.format(Number(v)),
              name === 'whatIfEndingBalance' ? 'With purchase' : 'Current baseline',
            ]}
            labelFormatter={(_, payload) => (payload?.[0] ? monthLong(payload[0].payload.month) : '')}
          />
          <ReferenceLine y={0} stroke="#13212f" />
          <Bar dataKey="endingBalance" name="Current baseline" radius={[4, 4, 0, 0]} minPointSize={4} cursor={onSelect ? 'pointer' : undefined} isAnimationActive={false}>
            {data.map((m) => (
              <Cell
                key={m.month}
                fill={selected === m.month ? (m.shortfall ? '#8f1d17' : '#0e2a47') : m.shortfall ? RED : INK}
                stroke={selected === m.month ? '#13212f' : undefined}
                strokeWidth={selected === m.month ? 1.5 : 0}
              />
            ))}
          </Bar>
          {hasWhatIf && (
            <Bar dataKey="whatIfEndingBalance" name="With purchase" radius={[4, 4, 0, 0]} minPointSize={4} isAnimationActive={false}>
              {data.map((m) => (
                <Cell
                  key={`whatif-${m.month}`}
                  fill={m.whatIfShortfall ? AMBER_RED : AMBER}
                />
              ))}
            </Bar>
          )}
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function summary(months: RunwayMonth[]) {
  const shortfalls = months.filter((m) => m.shortfall);
  if (!months.length) return 'No forecast yet.';
  const base = `Projected end-of-month balance for ${monthLong(months[0].month)} through ${monthLong(months[months.length - 1].month)}.`;
  return shortfalls.length
    ? `${base} Below zero in ${shortfalls.map((m) => monthLong(m.month)).join(', ')}.`
    : `${base} Never below zero.`;
}
