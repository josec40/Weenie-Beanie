import type { CSSProperties, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight, type LucideIcon } from 'lucide-react';

const BEAN = 'M9 1.5c6-.8 10.2 3.8 9 9.8-1 5.4-6 7-9.1 5.5-2-1-3.4-.1-4.7 1.8C2.4 21.2-1.6 19.6.6 14.4 1.9 11.3 2.8 9.7 3 7.9 3.4 5.7 5.9 2 9 1.5Z';
const BIG_LEAF = 'M0 0 C 22 -36, 74 -44, 104 -16 C 74 8, 30 14, 0 0 Z';
const BIG_VEIN = 'M4 -1 C 34 -12, 64 -18, 92 -15';

function BeanGlyph() {
  return (
    <>
      <path className="bean__body" d={BEAN} />
      <circle className="bean__speck" cx={12} cy={7} r={1.2} />
      <circle className="bean__speck" cx={7} cy={12.5} r={1} />
    </>
  );
}

/** The brand mark: a bean on a white rounded tile, after an app icon. */
export function BeanTile({ size = 34 }: { size?: number }) {
  return (
    <span className="bean-tile" style={{ width: size, height: size }} aria-hidden="true">
      <svg viewBox="-3 -2 25 25" width={size * 0.72} height={size * 0.72}>
        <BeanGlyph />
      </svg>
    </span>
  );
}

type LeafSpec = { x: number; y: number; r: number; s: number; tone: 'leaf' | 'young' | 'deep'; d: number };

const HERO_LEAVES: LeafSpec[] = [
  { x: 150, y: 214, r: -112, s: 1.25, tone: 'deep', d: 0 },
  { x: 166, y: 214, r: -64, s: 1.05, tone: 'leaf', d: 90 },
  { x: 136, y: 214, r: -150, s: 0.95, tone: 'young', d: 180 },
  { x: 250, y: 214, r: -96, s: 0.85, tone: 'young', d: 260 },
  { x: 262, y: 214, r: -48, s: 0.7, tone: 'leaf', d: 340 },
];

/** Decorative leaves and a bean that grow up into the hero band from its lower edge. */
export function HeroLeaves() {
  return (
    <svg className="hero-leaves" viewBox="0 0 320 214" aria-hidden="true" focusable="false">
      {HERO_LEAVES.map((l, i) => (
        <g key={i} transform={`translate(${l.x} ${l.y}) rotate(${l.r}) scale(${l.s})`}>
          <g className={`sway sway--${l.tone}`} style={{ '--d': `${l.d}ms` } as CSSProperties}>
            <path className="hero-leaves__leaf" d={BIG_LEAF} />
            <path className="hero-leaves__vein" d={BIG_VEIN} />
          </g>
        </g>
      ))}
      <g transform="translate(196 180) rotate(-18) scale(1.5)">
        <g className="wobble">
          <BeanGlyph />
        </g>
      </g>
    </svg>
  );
}

/** Thin illustrated strip: soil with a bean waiting to be planted. */
export function BeanStrip() {
  return (
    <svg className="bean-strip" viewBox="0 0 400 64" preserveAspectRatio="xMidYMax slice" aria-hidden="true" focusable="false">
      <path className="bean-strip__soil" d="M-20 52 Q 200 36 420 52 L 420 64 L -20 64 Z" />
      <g transform="translate(316 40) rotate(-38) scale(0.42)">
        <path className="hero-leaves__leaf sway sway--leaf" d={BIG_LEAF} />
      </g>
      <g transform="translate(330 40) rotate(-140) scale(0.32)">
        <path className="hero-leaves__leaf sway sway--young" d={BIG_LEAF} />
      </g>
      <g transform="translate(190 20)">
        <circle className="bean-strip__ring" cx={9.5} cy={11} r={16} />
        <g className="wobble">
          <BeanGlyph />
        </g>
      </g>
    </svg>
  );
}

export type Tone = 'navy' | 'grow' | 'bean' | 'warn';

export function IconTile({ icon: Icon, tone = 'navy', children }: { icon?: LucideIcon; tone?: Tone; children?: ReactNode }) {
  return (
    <span className={`icon-tile icon-tile--${tone}`} aria-hidden="true">
      {Icon ? <Icon size={20} /> : children}
    </span>
  );
}

/** Acorns-style list row: icon tile, bold verb + quieter rest, optional amount, chevron. */
export function ActionRow({
  to,
  icon,
  tone,
  verb,
  rest,
  amount,
  tile,
}: {
  to: string;
  icon?: LucideIcon;
  tone?: Tone;
  verb: string;
  rest: string;
  amount?: ReactNode;
  tile?: ReactNode;
}) {
  return (
    <li>
      <Link to={to} className="action-row">
        <IconTile icon={icon} tone={tone}>
          {tile}
        </IconTile>
        <span className="action-row__text">
          <strong>{verb}</strong> {rest}
        </span>
        {amount !== undefined && <span className="action-row__amount">{amount}</span>}
        <ChevronRight className="action-row__chevron" aria-hidden="true" size={20} />
      </Link>
    </li>
  );
}

export function ActionList({ label, children }: { label: string; children: ReactNode }) {
  return (
    <nav className="action-list" aria-label={label}>
      <ul>{children}</ul>
    </nav>
  );
}

/** Pill progress track with a value bubble riding the end of the fill. */
export function ProgressTrack({
  value,
  max,
  bubble,
  start,
  end,
  label,
}: {
  value: number;
  max: number;
  bubble: string;
  start: string;
  end: string;
  label: string;
}) {
  const pct = max <= 0 ? 100 : Math.min(100, Math.max(0, (value / max) * 100));
  return (
    <div className="progress-track">
      <div
        className="progress-track__bar"
        role="progressbar"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={max}
        aria-valuenow={Math.min(value, max)}
        aria-valuetext={`${bubble}, ${end}`}
      >
        <span className="progress-track__fill" style={{ width: `${pct}%` }} />
        <span className="progress-track__bubble" style={{ left: `clamp(24px, ${pct}%, calc(100% - 24px))` }}>
          {bubble}
        </span>
      </div>
      <div className="progress-track__labels">
        <span>{start}</span>
        <span>{end}</span>
      </div>
    </div>
  );
}

export function BeanIcon({ size = 20 }: { size?: number }) {
  return (
    <svg viewBox="-3 -2 25 25" width={size} height={size} aria-hidden="true">
      <BeanGlyph />
    </svg>
  );
}
