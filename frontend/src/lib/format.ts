export const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
export const usdWhole = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });

function ymDate(ym: string) {
  const [y, m] = ym.slice(0, 7).split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, 1));
}

/** "February 2027" */
export function monthLong(ym: string) {
  return ymDate(ym).toLocaleDateString('en-US', { month: 'long', year: 'numeric', timeZone: 'UTC' });
}

/** "Feb ’27" style short label for axes and tables. */
export function monthShort(ym: string) {
  return ymDate(ym).toLocaleDateString('en-US', { month: 'short', year: '2-digit', timeZone: 'UTC' });
}

/** "February" */
export function monthName(ym: string) {
  return ymDate(ym).toLocaleDateString('en-US', { month: 'long', timeZone: 'UTC' });
}

export function addMonths(ym: string, n: number) {
  const d = ymDate(ym);
  d.setUTCMonth(d.getUTCMonth() + n);
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

const relative = new Intl.RelativeTimeFormat('en-US', { numeric: 'auto' });

export function timeAgo(iso: string) {
  const seconds = Math.round((new Date(iso).getTime() - Date.now()) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 60) return relative.format(seconds, 'second');
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute');
  if (abs < 86400) return relative.format(Math.round(seconds / 3600), 'hour');
  return relative.format(Math.round(seconds / 86400), 'day');
}

export const dateTime = new Intl.DateTimeFormat('en-US', { dateStyle: 'medium', timeStyle: 'short' });
