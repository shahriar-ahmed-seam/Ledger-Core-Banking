import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Money, Panel } from '../../components/ui';
import { accountApi } from '../../lib/services';

/**
 * Account statement (Requirements 14.7, 14.8): ledger entries with a running balance,
 * 50 per page, in a dense statement table.
 */
export function AccountHistory() {
  const { accountId } = useParams<{ accountId: string }>();
  const navigate = useNavigate();

  const { data: account } = useQuery({
    queryKey: ['accounts'],
    queryFn: accountApi.list,
    select: (accounts) => accounts.find((a) => a.id === accountId),
  });

  const { data: statement, isLoading } = useQuery({
    queryKey: ['statement', accountId],
    queryFn: () => accountApi.statement(accountId!),
    enabled: !!accountId,
  });

  const currency = account?.currency ?? 'BDT';
  const acctNo = (accountId ?? '').replace(/[^0-9]/g, '').padEnd(12, '0').slice(0, 12);

  return (
    <div className="stack">
      <div>
        <div className="crumb">Home / Account Summary / Statement</div>
        <div className="page-title">
          <div>
            <h1>Account Statement</h1>
            <p className="tnum">A/C {acctNo.slice(0, 4)} {acctNo.slice(4, 8)} {acctNo.slice(8, 12)} · {currency}</p>
          </div>
          <Button variant="secondary" size="sm" onClick={() => navigate('/dashboard')}>← Back to Summary</Button>
        </div>
      </div>

      {account && (
        <Panel title="Account Details">
          <dl className="kv">
            <dt>Account Type</dt><dd>Savings Account</dd>
            <dt>Currency</dt><dd>{currency}</dd>
            <dt>Status</dt><dd>{account.status}</dd>
            <dt>Available Balance</dt>
            <dd><Money amount={account.availableBalance.amount} currency={currency} /></dd>
          </dl>
        </Panel>
      )}

      <Panel title="Transaction History" bodyPad={false}>
        {isLoading && <div style={{ padding: 'var(--space-5)' }} className="muted">Loading statement…</div>}
        {statement && statement.items.length === 0 && (
          <div className="empty">No transactions recorded for this account.</div>
        )}
        {statement && statement.items.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th style={{ width: 200 }}>Date &amp; Time</th>
                <th>Description</th>
                <th>Type</th>
                <th className="num">Amount</th>
                <th className="num">Balance</th>
              </tr>
            </thead>
            <tbody>
              {statement.items.map((line) => (
                <tr key={line.entryId}>
                  <td className="tnum">{new Date(line.postedAt).toLocaleString('en-GB')}</td>
                  <td className="muted tnum">Ref {line.transactionId.slice(0, 8).toUpperCase()}</td>
                  <td style={{ color: line.direction === 'CREDIT' ? 'var(--positive)' : 'var(--negative)', fontWeight: 600 }}>
                    {line.direction === 'CREDIT' ? 'Credit' : 'Debit'}
                  </td>
                  <td className="num">
                    <Money amount={line.amount} currency={currency} tone />
                  </td>
                  <td className="num tnum">
                    <Money amount={line.runningBalance} currency={currency} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  );
}
