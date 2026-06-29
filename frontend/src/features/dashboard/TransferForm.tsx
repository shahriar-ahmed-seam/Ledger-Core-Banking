import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Money, Notice, Panel } from '../../components/ui';
import { Account, transferApi } from '../../lib/services';
import { errorCodeOf } from '../../lib/api';

type Status =
  | { kind: 'idle' }
  | { kind: 'submitting' }
  | { kind: 'success'; transactionId: string }
  | { kind: 'error'; message: string };

/**
 * Fund transfer (Requirements 14.2–14.7): generates an idempotency key per submission,
 * disables submit while in flight, re-enables on completion, and surfaces insufficient-funds
 * and timeout messages without changing displayed balances.
 */
export function TransferForm({ accounts }: { accounts: Account[] }) {
  const queryClient = useQueryClient();
  const active = accounts.filter((a) => a.status === 'ACTIVE');
  const [sourceId, setSourceId] = useState('');
  const [destinationId, setDestinationId] = useState('');
  const [amount, setAmount] = useState('');
  const [status, setStatus] = useState<Status>({ kind: 'idle' });

  const source = active.find((a) => a.id === sourceId);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!source || !destinationId || !amount) return;
    setStatus({ kind: 'submitting' });
    try {
      const result = await transferApi.transfer(sourceId, destinationId, amount, source.currency);
      await queryClient.invalidateQueries({ queryKey: ['accounts'] });
      setStatus({ kind: 'success', transactionId: result.transactionId });
      setAmount('');
    } catch (err) {
      const code = errorCodeOf(err);
      let message = 'The transfer could not be completed. Please try again.';
      if (code === 'INSUFFICIENT_FUNDS') {
        message = 'Insufficient funds. The available balance is not enough for this amount.';
      } else if (code === 'CLIENT_TIMEOUT') {
        message = 'The request timed out. Your balance is unchanged; please try again.';
      } else if (code === 'RETRYABLE_CONFLICT') {
        message = 'The account is currently busy. Your balance is unchanged; please retry.';
      } else if (code === 'VALIDATION_ERROR') {
        message = 'Please check the amount and selected accounts.';
      }
      setStatus({ kind: 'error', message });
    }
  }

  const submitting = status.kind === 'submitting';

  return (
    <Panel title="Fund Transfer">
      <form className="stack" onSubmit={submit} style={{ gap: 'var(--space-4)' }}>
        <div className="field">
          <label htmlFor="src">Debit Account (From)</label>
          <select id="src" className="select" value={sourceId}
            onChange={(e) => setSourceId(e.target.value)} required>
            <option value="" disabled>Select source account</option>
            {active.map((a) => (
              <option key={a.id} value={a.id}>{a.currency} · A/C ending {a.id.replace(/[^0-9]/g, '').slice(-4) || '0000'}</option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor="dst">Credit Account (To)</label>
          <select id="dst" className="select" value={destinationId}
            onChange={(e) => setDestinationId(e.target.value)} required>
            <option value="" disabled>Select destination account</option>
            {accounts.filter((a) => a.id !== sourceId).map((a) => (
              <option key={a.id} value={a.id}>{a.currency} · A/C ending {a.id.replace(/[^0-9]/g, '').slice(-4) || '0000'}</option>
            ))}
          </select>
        </div>

        <div className="field">
          <label htmlFor="amount">Amount{source ? ` (${source.currency})` : ''}</label>
          <input id="amount" className="input" inputMode="decimal" placeholder="0.00"
            value={amount} onChange={(e) => setAmount(e.target.value)} required />
        </div>

        {source && (
          <div className="transfer-summary">
            <div className="line"><span className="muted">Available balance</span>
              <span className="tnum"><Money amount={source.availableBalance.amount} currency={source.currency} /></span></div>
          </div>
        )}

        <Button type="submit" loading={submitting} disabled={submitting} style={{ width: '100%' }}>
          {submitting ? 'Processing…' : 'Confirm Transfer'}
        </Button>

        {status.kind === 'success' && (
          <Notice kind="success">
            Transfer successful. Reference <strong className="tnum">{status.transactionId.slice(0, 8).toUpperCase()}</strong>.
          </Notice>
        )}
        {status.kind === 'error' && <Notice kind="error">{status.message}</Notice>}
      </form>
    </Panel>
  );
}
