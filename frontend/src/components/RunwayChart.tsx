import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { monthLong, monthShort, usd, usdWhole } from '../lib/format';

export type RunwayMonth = { month: string; endingBalance: number; shortfall: boolean };

const INK = '#1f4e79';
const RED = '#b3261e';

/** Projected end-of-month balance per month; shortfall months in red, the selected month emphasized. */
export default function RunwayChart({
  months,
  height = 240,
  selected,
  onSelect,
}: {
  months: RunwayMonth[];
  height?: number;
  selected?: string | null;
  onSelect?: (month: string) => void;
}) {
  const data = months.map((m) => ({ ...m, label: monthShort(m.month) }));
  return (
    <div className="runway" role="img" aria-label={summary(months)}>
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
            cursor={{ fill: 'rgba(14, 42, 71, 0.06)' }}
            formatter={(v: number) => [usd.format(v), 'Ending balance']}
            labelFormatter={(_, payload) => (payload?.[0] ? monthLong(payload[0].payload.month) : '')}
          />
          <ReferenceLine y={0} stroke="#13212f" />
          <Bar dataKey="endingBalance" radius={[4, 4, 0, 0]} minPointSize={4} cursor={onSelect ? 'pointer' : undefined} isAnimationActive={false}>
            {data.map((m) => (
              <Cell
                key={m.month}
                fill={selected === m.month ? (m.shortfall ? '#8f1d17' : '#0e2a47') : m.shortfall ? RED : INK}
                stroke={selected === m.month ? '#13212f' : undefined}
                strokeWidth={selected === m.month ? 1.5 : 0}
              />
            ))}
          </Bar>
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
