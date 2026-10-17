import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { X } from 'lucide-react';
import type { LiveNotification } from '../hooks/useLiveUpdates';
import { NotificationIcon } from './NotificationBell';

const LIFETIME_MS = 7000;

function Toast({ toast, onDismiss }: { toast: LiveNotification; onDismiss: (id: string) => void }) {
  const timer = useRef<number | undefined>(undefined);
  const start = () => {
    timer.current = window.setTimeout(() => onDismiss(toast.id), LIFETIME_MS);
  };
  const stop = () => window.clearTimeout(timer.current);

  useEffect(() => {
    start();
    return stop;
  }, []);

  return (
    <li className={`toast toast--${toast.type.toLowerCase()}`} onMouseEnter={stop} onMouseLeave={start} onFocus={stop} onBlur={start}>
      <NotificationIcon type={toast.type} />
      <div className="toast__text">
        <strong>{toast.title}</strong>
        <span>{toast.body}</span>
        {toast.link && (
          <Link to={toast.link} onClick={() => onDismiss(toast.id)}>
            View details
          </Link>
        )}
      </div>
      <button type="button" className="icon-button" aria-label="Dismiss notification" onClick={() => onDismiss(toast.id)}>
        <X aria-hidden="true" size={16} />
      </button>
    </li>
  );
}

export default function Toasts({ toasts, onDismiss }: { toasts: LiveNotification[]; onDismiss: (id: string) => void }) {
  return (
    <ol className="toasts" aria-live="polite" aria-label="Notifications">
      {toasts.map((t) => (
        <Toast key={t.id} toast={t} onDismiss={onDismiss} />
      ))}
    </ol>
  );
}
