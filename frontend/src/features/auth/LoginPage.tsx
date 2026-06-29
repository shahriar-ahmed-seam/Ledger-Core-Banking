import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { Button, Input, Notice, Panel } from '../../components/ui';
import { authApi } from '../../lib/services';

interface FormValues {
  email: string;
  password: string;
}

/**
 * Internet-banking sign-in (Requirement 13). On success tokens are stored and the user
 * transitions to the dashboard; on failure a single generic error is shown (Requirement 13.2).
 */
export function LoginPage({ onAuthed }: { onAuthed: () => void }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { register, handleSubmit, formState } = useForm<FormValues>();
  const [error, setError] = useState<string | null>(null);
  const initialMode = (location.state as { mode?: 'login' | 'register' } | null)?.mode ?? 'login';
  const [mode, setMode] = useState<'login' | 'register'>(initialMode);

  async function onSubmit(values: FormValues) {
    setError(null);
    try {
      if (mode === 'register') {
        await authApi.register(values.email, values.password);
      }
      await authApi.login(values.email, values.password);
      onAuthed();
      navigate('/dashboard', { replace: true });
    } catch {
      setError(
        mode === 'login'
          ? 'We could not sign you in. Please check your details and try again.'
          : 'Registration failed. Use a valid email and a password of at least 12 characters with a letter and a digit.',
      );
    }
  }

  return (
    <div className="login-wrap">
      <div className="login-main">
        <div className="login-card">
          <div className="login-logo">
            <img src="/logo.png" alt="Ledger Bank — Internet Banking" />
          </div>
          <Panel title={mode === 'login' ? 'Account Sign In' : 'Open an Account'}>
            <form className="stack" onSubmit={handleSubmit(onSubmit)} style={{ gap: 'var(--space-4)' }}>
              <p className="login-hero">
                {mode === 'login'
                  ? 'Secure access to your accounts, balances and fund transfers.'
                  : 'Create your retail banking profile to get started.'}
              </p>

              <Input
                id="email"
                label="User ID / Email"
                type="email"
                autoComplete="email"
                placeholder="name@example.com"
                {...register('email', { required: true })}
              />
              <Input
                id="password"
                label="Password"
                type="password"
                autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
                placeholder="Enter your password"
                {...register('password', { required: true })}
              />

              {error && <Notice kind="error">{error}</Notice>}

              <Button type="submit" loading={formState.isSubmitting} style={{ width: '100%' }}>
                {mode === 'login' ? 'Sign In' : 'Create Account'}
              </Button>

              <div className="row" style={{ justifyContent: 'space-between', borderTop: '1px solid var(--border)', paddingTop: 'var(--space-4)' }}>
                <span className="muted">
                  {mode === 'login' ? 'Not registered yet?' : 'Already have an account?'}
                </span>
                <button
                  type="button"
                  className="btn btn-secondary btn-sm"
                  onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(null); }}
                >
                  {mode === 'login' ? 'Open an Account' : 'Back to Sign In'}
                </button>
              </div>
            </form>
          </Panel>
          <p className="muted" style={{ textAlign: 'center', marginTop: 'var(--space-5)' }}>
            For your security, never share your password. Ledger Bank will never ask for it by phone or email.
          </p>
        </div>
      </div>

      <footer className="footer">
        <div className="footer-inner">
          <span>© {new Date().getFullYear()} Ledger Bank Ltd. All rights reserved.</span>
          <span>Member, Bangladesh Bank scheme</span>
        </div>
      </footer>
    </div>
  );
}
