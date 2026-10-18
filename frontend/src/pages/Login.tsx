import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { login } from '../auth';
import { Wordmark } from '../components/AppShell';
import Beanstalk from '../components/Beanstalk';
import { TIER_CAPS } from './Home';

export default function Login() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login">
      <section className="login__intro" aria-hidden="true">
        <Wordmark inverse />
        <p className="login__line">Proactive micro-loans that turn tight months into towering credit.</p>
        <div className="login__plant">
          <Beanstalk onTime={3} defaults={0} claimed={false} tier={1} tierCaps={TIER_CAPS} variant="preview" />
        </div>
      </section>
      <main className="login__form">
        <div className="login__powered" aria-label="Powered by Nessie">
          <span>Powered by</span>
          <img src="/brand/nessie.png" alt="Nessie" />
        </div>
        <div className="login__brand-mobile">
          <Wordmark />
        </div>
        <h1>Sign in</h1>
        <form onSubmit={onSubmit} className="stack">
          <label className="field">
            <span>Email</span>
            <input
              type="email"
              name="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="username"
              spellCheck={false}
            />
          </label>
          <label className="field">
            <span>Password</span>
            <input
              type="password"
              name="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
          </label>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="button button--block" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign In'}
          </button>
        </form>
        <p className="muted small">Demo environment: sandbox bank data and Solana devnet. No real money moves.</p>
      </main>
    </div>
  );
}
