import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Money, Notice, Panel } from '../../components/ui';
import { Account, accountApi } from '../../lib/services';
import { TransferForm } from './TransferForm';

/**
 * Account summary (Requirement 14.1, 14.10): owned accounts with balances, an empty state,
 * and the fund-transfer panel.
 */
export function DashboardHome() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [newCurrency, setNewCurrency] = useState('BDT');

  const { data: accounts, isLoading } = useQuery({
    queryKey: ['accounts'],
    queryFn: accountApi.list,
  });

  const openAccount = useMutation({
    mutationFn: () => accountApi.open(newCurrency),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  });

  const total = (accounts ?? []).reduce((sum, a) => sum + Number(a.availableBalance.amount), 0);
  const primaryCurrency = accounts?.[0]?.currency ?? 'BDT';

  return (
    <div className="stack">
      <div>
        <div className="crumb">Home / Account Summary</div>
        <div className="page-title">
          <div>
            <h1>Account Summary</h1>
            <p>Balances are derived in real time from the bank ledger.</p>
          </div>
          <div className="row">
            <select className="select" style={{ width: 110 }} value={newCurrency}
              onChange={(e) => setNewCurrency(e.target.value)} aria-label="Currency for new account">
              {['BDT', 'USD', 'EUR', 'GBP'].map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
            <Button loading={openAccount.isPending} onClick={() => openAccount.mutate()}>
              + Open Account
            </Button>
          </div>
        </div>
      </div>

      <div className="home-grid">
        <div className="stack">
          <Panel
            title="My Accounts"
            actions={accounts && accounts.length > 0 ? (
              <span className="muted">{accounts.length} account{accounts.length > 1 ? 's' : ''}</span>
            ) : undefined}
          >
            {isLoading && <span className="muted">Loading accounts…</span>}

            {!isLoading && accounts && accounts.length === 0 && (
              <div className="empty">
                <h3>No accounts yet</h3>
                <p className="muted">Open your first account to start banking.</p>
              </div>
            )}

            {accounts && accounts.length > 0 && (
              <div className="acct-grid">
                {accounts.map((a) => (
                  <AccountCard key={a.id} account={a} onOpen={() => navigate(`/dashboard/accounts/${a.id}`)} />
                ))}
              </div>
            )}
          </Panel>

          {accounts && accounts.length > 1 && (
            <Notice kind="info">
              Total available across {accounts.length} accounts:&nbsp;
              <strong><Money amount={total.toFixed(2)} currency={primaryCurrency} /></strong>
              {accounts.some((a) => a.currency !== primaryCurrency) && ' (mixed currencies shown at face value)'}
            </Notice>
          )}
        </div>

        <TransferForm accounts={accounts ?? []} />
      </div>
    </div>
  );
}

function maskAccountNo(id: string): string {
  // Present the UUID as a bank-style account number.
  const digits = id.replace(/[^0-9]/g, '').padEnd(12, '0').slice(0, 12);
  return `${digits.slice(0, 4)} ${digits.slice(4, 8)} ${digits.slice(8, 12)}`;
}

function AccountCard({ account, onOpen }: { account: Account; onOpen: () => void }) {
  return (
    <div className="acct" role="button" tabIndex={0} onClick={onOpen}
      onKeyDown={(e) => e.key === 'Enter' && onOpen()}>
      <div className="acct-top">
        <div>
          <div className="acct-type">Savings · {account.currency}</div>
          <div className="acct-no tnum">A/C {maskAccountNo(account.id)}</div>
        </div>
        <span className={`badge ${account.status === 'ACTIVE' ? 'badge-active' : 'badge-closed'}`}>
          {account.status}
        </span>
      </div>
      <div>
        <div className="acct-bal-label">Available Balance</div>
        <div className="acct-bal">
          <Money amount={account.availableBalance.amount} currency={account.currency} />
        </div>
      </div>
      <div className="acct-foot">
        <span>Overdraft limit</span>
        <span className="tnum"><Money amount={account.overdraftLimit.amount} currency={account.currency} /></span>
      </div>
    </div>
  );
}
