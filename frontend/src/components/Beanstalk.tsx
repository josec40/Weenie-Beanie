import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties } from 'react';
import { usdWhole } from '../lib/format';

const W = 220;
const SOIL_Y = 300;
const STUB = 24;
const SEGMENT = 34;
const MAX_LEAVES = 7;
const TIER_AT = [2, 5];
const EASE = 'cubic-bezier(0.16, 1, 0.3, 1)';

function stalkX(y: number) {
  return W / 2 + 8 * Math.sin((SOIL_Y - y) / 38);
}

function nodeY(i: number) {
  return SOIL_Y - STUB - (i - 1) * SEGMENT - SEGMENT / 2;
}

function stalkTop(leaves: number) {
  return SOIL_Y - STUB - Math.min(leaves, MAX_LEAVES) * SEGMENT - 10;
}

function stalkPath(leaves: number) {
  const top = stalkTop(leaves);
  const points: string[] = [`M ${stalkX(SOIL_Y + 4).toFixed(1)} ${SOIL_Y + 4}`];
  for (let y = SOIL_Y; y > top; y -= 8) points.push(`L ${stalkX(y).toFixed(1)} ${y}`);
  points.push(`L ${stalkX(top).toFixed(1)} ${top}`);
  return points.join(' ');
}

const LEAF = 'M0 0 C 8 -13, 28 -17, 40 -7 C 29 3, 12 6, 0 0 Z';
const BEAN = 'M9 1.5c6-.8 10.2 3.8 9 9.8-1 5.4-6 7-9.1 5.5-2-1-3.4-.1-4.7 1.8C2.4 21.2-1.6 19.6.6 14.4 1.9 11.3 2.8 9.7 3 7.9 3.4 5.7 5.9 2 9 1.5Z';

const CLOUD_CENTER =
  'M -22 5 L 22 5 C 28 5, 30 1, 26 -4 C 27 -10, 18 -14, 12 -8 C 8 -17, -6 -17, -10 -8 C -16 -14, -25 -10, -22 -4 C -27 1, -25 5, -22 5 Z';
const CLOUD_RIGHT =
  'M -25 5 L 25 5 C 31 5, 33 1, 29 -4 C 30 -10, 21 -14, 15 -8 C 10 -17, -5 -17, -9 -8 C -14 -14, -23 -11, -22 -4 C -27 1, -25 5, -22 5 Z';
const CLOUD_WISP =
  'M -12 3 L 12 3 C 16 3, 17 0, 14 -2 C 15 -6, 9 -8, 5 -5 C 3 -9, -4 -9, -6 -5 C -9 -8, -14 -6, -12 -2 C -15 0, -14 3, -12 3 Z';

function prefersReducedMotion() {
  return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
}

export type BeanstalkProps = {
  onTime: number;
  defaults: number;
  /** An accepted, not yet repaid loan: the bean is claimed but not planted. */
  claimed: boolean;
  tierCaps: number[];
  tier: number;
  variant?: 'full' | 'preview';
};

/**
 * Repayment reputation as a beanstalk: each effective on-time repayment adds a segment and a leaf; tier marks sit at
 * 2 and 5 leaves; a default drops two leaves to the soil. Growth animates once per new leaf and sprouts from the ground up on mount.
 */
export default function Beanstalk({ onTime, defaults, claimed, tierCaps, tier, variant = 'full' }: BeanstalkProps) {
  const leaves = Math.max(0, onTime - 2 * defaults);
  const shown = Math.min(leaves, MAX_LEAVES);
  const titleId = useId();
  const descId = useId();
  const stalkRef = useRef<SVGPathElement>(null);
  const newLeafRef = useRef<SVGGElement>(null);
  const leafRefs = useRef<(SVGGElement | null)[]>([]);
  const plantingRef = useRef<SVGGElement>(null);
  const previous = useRef<{ leaves: number; claimed: boolean } | null>(null);
  const [planting, setPlanting] = useState(false);
  const [cloudsDispersed, setCloudsDispersed] = useState<Record<number, boolean>>(() => {
    if (prefersReducedMotion()) {
      const initial: Record<number, boolean> = {};
      TIER_AT.forEach((at) => {
        if (leaves >= at) initial[at] = true;
      });
      return initial;
    }
    return {};
  });

  useLayoutEffect(() => {
    const before = previous.current;
    previous.current = { leaves, claimed };

    if (prefersReducedMotion()) {
      TIER_AT.forEach((at) => {
        if (leaves >= at) {
          setCloudsDispersed((prev) => ({ ...prev, [at]: true }));
        }
      });
      return;
    }

    const stalk = stalkRef.current;
    if (!stalk || leaves === 0) return;

    const timeouts: number[] = [];

    if (!before) {
      // Sprout from the ground up on initial mount!
      stalk.animate(
        [{ strokeDashoffset: 100 }, { strokeDashoffset: 0 }],
        { duration: 1050, easing: EASE, fill: 'backwards' }
      );

      // Stagger leaf appearance as stalk shoots up
      for (let k = 0; k < shown; k++) {
        const i = k + 1;
        const progress = Math.max(0, Math.min(1, (SOIL_Y - nodeY(i)) / (SOIL_Y - stalkTop(leaves))));
        const leafDelay = Math.round(180 + progress * 620);
        leafRefs.current[k]?.animate(
          [
            { transform: 'scale(0)', opacity: 0 },
            { transform: 'scale(1.22)', opacity: 1, offset: 0.68 },
            { transform: 'scale(0.96)', opacity: 1, offset: 0.88 },
            { transform: 'scale(1)', opacity: 1 },
          ],
          {
            duration: 520,
            delay: leafDelay,
            easing: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
            fill: 'backwards',
          }
        );
      }

      // Disperse clouds for reached tiers when the stalk reaches each tier line
      TIER_AT.forEach((at) => {
        if (leaves >= at) {
          const tierY = nodeY(at) - SEGMENT / 2 + 4;
          const progress = Math.max(0, Math.min(1, (SOIL_Y - tierY) / (SOIL_Y - stalkTop(leaves))));
          const tierDelay = Math.round(150 + progress * 650);
          const tId = window.setTimeout(() => {
            setCloudsDispersed((prev) => ({ ...prev, [at]: true }));
          }, tierDelay);
          timeouts.push(tId);
        }
      });
    } else if (leaves > before.leaves) {
      const grownFrom = Math.max(0, (SOIL_Y - stalkTop(before.leaves)) / (SOIL_Y - stalkTop(leaves)));
      const delay = before.claimed ? 450 : 0;
      if (before.claimed) setPlanting(true);
      stalk.animate(
        [{ strokeDashoffset: 100 * (1 - grownFrom) }, { strokeDashoffset: 0 }],
        { duration: 900, delay, easing: EASE, fill: 'backwards' }
      );
      newLeafRef.current?.animate(
        [
          { transform: 'scale(0)', opacity: 0 },
          { transform: 'scale(1.22)', opacity: 1, offset: 0.68 },
          { transform: 'scale(1)', opacity: 1 },
        ],
        { duration: 600, delay: delay + 650, easing: EASE, fill: 'backwards' }
      );

      TIER_AT.forEach((at) => {
        if (before.leaves < at && leaves >= at) {
          const tId = window.setTimeout(() => {
            setCloudsDispersed((prev) => ({ ...prev, [at]: true }));
          }, delay + 600);
          timeouts.push(tId);
        }
      });
    }

    return () => {
      timeouts.forEach((id) => clearTimeout(id));
    };
  }, [leaves, claimed, shown]);

  useEffect(() => {
    if (!planting) return;
    const bean = plantingRef.current;
    const anim = bean?.animate(
      [{ transform: 'translateY(-46px)', opacity: 1 }, { transform: 'translateY(0)', opacity: 1, offset: 0.7 }, { transform: 'translateY(8px)', opacity: 0 }],
      { duration: 650, easing: 'cubic-bezier(0.5, 0, 0.75, 0)' },
    );
    const done = () => setPlanting(false);
    if (anim) anim.onfinish = done;
    else done();
    return () => anim?.cancel();
  }, [planting]);

  const nextTier = TIER_AT.find((t) => leaves < t);
  const description =
    leaves === 0
      ? 'Nothing planted yet.'
      : `A beanstalk with ${leaves} ${leaves === 1 ? 'leaf' : 'leaves'}, one per on-time repayment.`;
  const tierText = nextTier
    ? ` ${nextTier - leaves} more to reach Tier ${TIER_AT.indexOf(nextTier) + 1}.`
    : ' The highest tier is reached.';
  // The preview crops the empty sky above the Tier 2 mark so the plant fills its card.
  const top = variant === 'preview' ? Math.max(0, Math.min(nodeY(TIER_AT[1]) - SEGMENT / 2 - 18, stalkTop(leaves) - 12)) : 0;

  return (
    <svg
      className={`beanstalk beanstalk--${variant}`}
      viewBox={`0 ${top} ${W} ${SOIL_Y + 36 - top}`}
      role="img"
      aria-labelledby={`${titleId} ${descId}`}
    >
      <title id={titleId}>{`Your beanstalk, Tier ${tier}`}</title>
      <desc id={descId}>{`${description}${tierText}${claimed ? ' A claimed bean is waiting to be planted.' : ''}${defaults ? ` ${defaults} default${defaults === 1 ? '' : 's'} dropped leaves.` : ''}`}</desc>

      {TIER_AT.map((at, i) => {
        const y = nodeY(at) - SEGMENT / 2 + 4;
        const reached = leaves >= at;
        const isDispersed = cloudsDispersed[at] ?? false;

        return (
          <g key={at} className={`beanstalk__tier${reached ? ' is-reached' : ''}`}>
            <line x1={8} x2={W - 8} y1={y} y2={y} />
            <g
              className={`beanstalk__clouds ${isDispersed ? 'beanstalk__clouds--dispersed' : 'beanstalk__clouds--dense'}`}
              transform={`translate(0, ${y})`}
            >
              <g transform="translate(124, -1)">
                <g className="beanstalk__cloud-puff beanstalk__cloud-puff--center">
                  <path className="beanstalk__cloud" d={CLOUD_CENTER} />
                </g>
              </g>
              <g transform="translate(160, 2)">
                <g className="beanstalk__cloud-puff beanstalk__cloud-puff--right">
                  <path className="beanstalk__cloud" d={CLOUD_RIGHT} />
                </g>
              </g>
              <g transform="translate(196, -3)">
                <g className="beanstalk__cloud-puff beanstalk__cloud-puff--wisp">
                  <path className="beanstalk__cloud" d={CLOUD_WISP} />
                </g>
              </g>
            </g>
            <text x={8} y={y - 6}>{`Tier ${i + 1} · ${usdWhole.format(tierCaps[i + 1] ?? 0)}`}</text>
          </g>
        );
      })}

      {leaves > 0 && (
        <path ref={stalkRef} className="beanstalk__stalk" d={stalkPath(leaves)} pathLength={100} strokeDasharray={100} />
      )}

      {Array.from({ length: shown }, (_, k) => {
        const i = k + 1;
        const y = nodeY(i);
        const left = i % 2 === 0;
        const isNewest = i === shown;
        return (
          <g key={i} transform={`translate(${stalkX(y).toFixed(1)} ${y})${left ? ' scale(-1 1)' : ''}`}>
            <g
              className="sway sway--stalk"
              style={{
                '--d': `${(k * 180) % 900}ms`,
                '--dur': `${2400 + (k % 3) * 400}ms`,
              } as CSSProperties}
            >
              <g
                ref={(el) => {
                  leafRefs.current[k] = el;
                  if (isNewest) newLeafRef.current = el;
                }}
                className="beanstalk__leaf"
              >
                <path d={LEAF} />
                <path className="beanstalk__vein" d="M2 0 C 14 -5, 26 -7, 36 -6" />
              </g>
            </g>
          </g>
        );
      })}

      <path className="beanstalk__soil" d={`M-400 ${SOIL_Y + 8} L 4 ${SOIL_Y + 4} Q ${W / 2} ${SOIL_Y - 12} ${W - 4} ${SOIL_Y + 4} L ${W + 400} ${SOIL_Y + 8} L ${W + 400} ${SOIL_Y + 36} L -400 ${SOIL_Y + 36} Z`} />

      {Array.from({ length: Math.min(defaults * 2, 6) }, (_, k) => (
        <path
          key={`fallen-${k}`}
          className="beanstalk__fallen"
          d={LEAF}
          transform={`translate(${30 + k * 26} ${SOIL_Y + 14}) rotate(${k % 2 ? 160 : 12}) scale(0.55)`}
        />
      ))}

      {claimed && (
        <g className="beanstalk__claimed" transform={`translate(${W / 2 + 34} ${SOIL_Y - 30})`}>
          <circle cx={9.5} cy={11} r={17} />
          <g className="wobble">
            <path d={BEAN} />
            <circle className="beanstalk__speck" cx={12} cy={7} r={1.2} />
            <circle className="beanstalk__speck" cx={7} cy={12.5} r={1} />
          </g>
        </g>
      )}

      {planting && (
        <g transform={`translate(${W / 2 - 9} ${SOIL_Y - 14})`}>
          <g ref={plantingRef} className="beanstalk__planting">
            <path d={BEAN} />
          </g>
        </g>
      )}
    </svg>
  );
}

export function BeanToken({ size = 28 }: { size?: number }) {
  return (
    <svg className="bean-token" width={size} height={size} viewBox="-2 -1 24 24" aria-hidden="true">
      <path d={BEAN} />
      <circle className="beanstalk__speck" cx={12} cy={7} r={1.2} />
      <circle className="beanstalk__speck" cx={7} cy={12.5} r={1} />
    </svg>
  );
}
