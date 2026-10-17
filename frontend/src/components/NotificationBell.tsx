import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { gql, useMutation, useQuery } from '@apollo/client';
import { AlertTriangle, Bell, CircleCheck, HandCoins, Sprout, Tag } from 'lucide-react';
import { timeAgo } from '../lib/format';
import type { LiveNotification } from '../hooks/useLiveUpdates';

export const NOTIFICATIONS = gql`
  query Notifications {
    notifications(limit: 20) {
      id
      type
      title
      body
      link
      createdAt
      read
    }
    unreadNotifications
  }
`;

const MARK_READ = gql`
  mutation MarkRead($ids: [ID!]) {
    markNotificationsRead(ids: $ids)
  }
`;

export function NotificationIcon({ type }: { type: string }) {
  const props = { size: 18, 'aria-hidden': true as const, className: `note-icon note-icon--${type.toLowerCase()}` };
  switch (type) {
    case 'OFFER':
      return <Tag {...props} />;
    case 'LOAN_DISBURSED':
      return <HandCoins {...props} />;
    case 'LOAN_REPAID':
      return <CircleCheck {...props} />;
    case 'TIER_UP':
    case 'BEANSTALK_GREW':
      return <Sprout {...props} />;
    default:
      return <AlertTriangle {...props} />;
  }
}

export default function NotificationBell() {
  const { data } = useQuery<{ notifications: LiveNotification[]; unreadNotifications: number }>(NOTIFICATIONS, {
    pollInterval: 60000,
  });
  const [markRead] = useMutation(MARK_READ, { refetchQueries: [NOTIFICATIONS] });
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const unread = data?.unreadNotifications ?? 0;

  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        button.current?.focus();
      }
    };
    document.addEventListener('pointerdown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="bell" ref={root}>
      <button
        ref={button}
        type="button"
        className="icon-button icon-button--on-dark"
        aria-label={unread ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-expanded={open}
        aria-controls="notification-panel"
        onClick={() => setOpen((o) => !o)}
      >
        <Bell aria-hidden="true" size={20} />
        {unread > 0 && <span className="bell__count">{unread > 9 ? '9+' : unread}</span>}
      </button>
      {open && (
        <section id="notification-panel" className="bell__panel" aria-label="Notifications">
          <header>
            <h2>Notifications</h2>
            {unread > 0 && (
              <button type="button" className="text-button" onClick={() => void markRead({ variables: { ids: [] } })}>
                Mark All Read
              </button>
            )}
          </header>
          {!data?.notifications.length ? (
            <p className="bell__empty">You're all caught up. Offers, deposits, repayments and beanstalk growth show up here.</p>
          ) : (
            <ul>
              {data.notifications.map((n) => (
                <li key={n.id} className={n.read ? '' : 'is-unread'}>
                  <NotificationIcon type={n.type} />
                  <div>
                    {n.link ? (
                      <Link
                        to={n.link}
                        onClick={() => {
                          setOpen(false);
                          if (!n.read) void markRead({ variables: { ids: [n.id] } });
                        }}
                      >
                        {n.title}
                      </Link>
                    ) : (
                      <strong>{n.title}</strong>
                    )}
                    <p>{n.body}</p>
                    <time dateTime={n.createdAt}>{timeAgo(n.createdAt)}</time>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </div>
  );
}
