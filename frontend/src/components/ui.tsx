import { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, forwardRef } from 'react';
import './ui.css';
import { formatMoney } from '../lib/money';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'danger';
  size?: 'md' | 'sm';
  loading?: boolean;
};

export function Button({ variant = 'primary', size = 'md', loading, children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      className={`btn btn-${variant}${size === 'sm' ? ' btn-sm' : ''}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading && <span className="spinner" aria-hidden />}
      {children}
    </button>
  );
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & { label?: string; error?: string };

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, id, ...rest },
  ref,
) {
  return (
    <div className="field">
      {label && <label htmlFor={id}>{label}</label>}
      <input ref={ref} id={id} className="input" aria-invalid={!!error} {...rest} />
      {error && <span className="field-error" role="alert">{error}</span>}
    </div>
  );
});

export function Panel({ title, actions, children, bodyPad = true }: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  bodyPad?: boolean;
}) {
  return (
    <section className="panel">
      {title && (
        <div className="panel-head">
          <h2>{title}</h2>
          {actions}
        </div>
      )}
      {bodyPad ? <div className="panel-body">{children}</div> : children}
    </section>
  );
}

/** Renders a monetary value with the currency symbol and precision (Requirement 14.9). */
export function Money({ amount, currency, tone }: { amount: string; currency: string; tone?: boolean }) {
  const value = Number(amount);
  const toneClass = tone ? (value > 0 ? 'money-positive' : value < 0 ? 'money-negative' : '') : '';
  return <span className={`money ${toneClass}`}>{formatMoney(amount, currency)}</span>;
}

export function Notice({ kind, children }: { kind: 'success' | 'error' | 'info'; children: ReactNode }) {
  return <div className={`notice notice-${kind}`} role="status">{children}</div>;
}
