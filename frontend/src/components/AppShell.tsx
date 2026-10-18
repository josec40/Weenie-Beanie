import { useCallback, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { gql, useQuery } from '@apollo/client';
import { ArrowLeftRight, ChartColumn, House, LogOut, ReceiptText, ShoppingBag, Sprout } from 'lucide-react';
import { signOut } from '../apollo';
import { useLiveUpdates, type LiveNotification } from '../hooks/useLiveUpdates';
import DemoPanel from './DemoPanel';
import NotificationBell from './NotificationBell';
import Toasts from './Toasts';

const ME = gql`
  query ShellMe {
    me {
      email
    }
  }
`;

const NAV = [
  { to: '/', label: 'Home', icon: House, end: true },
  { to: '/forecast', label: 'Forecast', icon: ChartColumn, end: false },
  { to: '/loans', label: 'Loans', icon: ArrowLeftRight, end: false },
  { to: '/beanstalk', label: 'Beanstalk', icon: Sprout, end: false },
  { to: '/expenses', label: 'Expenses', icon: ReceiptText, end: false },
  { to: '/store', label: 'Store', icon: ShoppingBag, end: false },
];

export function Wordmark({ inverse = false }: { inverse?: boolean }) {
  return (
    <span className={`wordmark${inverse ? ' wordmark--inverse' : ''}`} translate="no">
      <img className="wordmark__logo" src="/brand/weenie-beanie.png" alt="" />
      Weenie Beanie
    </span>
  );
}

export default function AppShell() {
  const navigate = useNavigate();
  const { data } = useQuery<{ me: { email: string } | null }>(ME);
  const [toasts, setToasts] = useState<LiveNotification[]>([]);
  const onNotification = useCallback((n: LiveNotification) => {
    setToasts((t) => (t.some((x) => x.id === n.id) ? t : [...t.slice(-2), n]));
  }, []);
  const live = useLiveUpdates(onNotification);

  async function onSignOut() {
    await signOut();
    navigate('/login', { replace: true });
  }

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="shell__bar">
        <div className="shell__bar-inner">
          <NavLink to="/" className="shell__brand" aria-label="Weenie Beanie home">
            <Wordmark inverse />
          </NavLink>
          <nav className="shell__nav" aria-label="Primary">
            {NAV.map(({ to, label, end }) => (
              <NavLink key={to} to={to} end={end} className="shell__nav-link">
                {label}
              </NavLink>
            ))}
          </nav>
          <div className="shell__tools">
            {live === 'reconnecting' && (
              <span className="shell__live" role="status">
                Reconnecting…
              </span>
            )}
            <NotificationBell />
            <span className="shell__user" translate="no">
              {data?.me?.email}
            </span>
            <button type="button" className="icon-button icon-button--on-dark" aria-label="Sign out" onClick={() => void onSignOut()}>
              <LogOut aria-hidden="true" size={20} />
            </button>
          </div>
        </div>
      </header>

      <main id="main" className="shell__main" tabIndex={-1}>
        <Outlet />
      </main>

      <footer className="shell__footer">
        Built on Capital One services. Sandbox banking data and Solana devnet; no real money moves.
      </footer>

      <nav className="tabbar" aria-label="Primary">
        {NAV.map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end} className="tabbar__link">
            <Icon aria-hidden="true" size={22} />
            <span>{label}</span>
          </NavLink>
        ))}
      </nav>

      <DemoPanel />
      <Toasts toasts={toasts} onDismiss={(id) => setToasts((t) => t.filter((x) => x.id !== id))} />
    </div>
  );
}
