import { api } from './api';
import { tokenStore } from './tokenStore';
import { newIdempotencyKey } from './idempotency';

export interface Account {
  id: string;
  ownerId: string;
  currency: string;
  availableBalance: { amount: string; currency: string };
  status: 'ACTIVE' | 'CLOSED';
  overdraftLimit: { amount: string; currency: string };
}

export interface LedgerEntry {
  id: number;
  transactionId: string;
  accountId: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: string;
  currency: string;
  postedAt: string;
}

export interface StatementLine {
  entryId: number;
  transactionId: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: string;
  postedAt: string;
  runningBalance: string;
}

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}

function unwrap<T>(data: { data: T }): T {
  return data.data;
}

export const authApi = {
  async login(email: string, password: string) {
    const { data } = await api.post('/auth/login', { email, password });
    const payload = unwrap<{ accessToken: string; refreshToken: string; userId: string; role: string }>(data);
    tokenStore.setTokens(payload.accessToken, payload.refreshToken);
    return payload;
  },
  async register(email: string, password: string) {
    const { data } = await api.post('/auth/register', { email, password });
    return unwrap(data);
  },
  async logout() {
    const refreshToken = tokenStore.getRefreshToken();
    try {
      if (refreshToken) await api.post('/auth/logout', { refreshToken });
    } finally {
      tokenStore.endSession();
    }
  },
};

export const accountApi = {
  async list(): Promise<Account[]> {
    const { data } = await api.get('/accounts');
    return unwrap<Account[]>(data);
  },
  async open(currency: string): Promise<Account> {
    const { data } = await api.post('/accounts', { currency });
    return unwrap<Account>(data);
  },
  async entries(accountId: string, cursor?: string): Promise<Page<LedgerEntry>> {
    const { data } = await api.get(`/accounts/${accountId}/entries`, {
      params: { size: 50, cursor },
    });
    return unwrap<Page<LedgerEntry>>(data);
  },
  async statement(accountId: string, cursor?: string): Promise<Page<StatementLine>> {
    const { data } = await api.get(`/accounts/${accountId}/statement`, {
      params: { size: 50, cursor },
    });
    return unwrap<Page<StatementLine>>(data);
  },
};

export const transferApi = {
  async transfer(sourceId: string, destinationId: string, amount: string, currency: string) {
    const { data } = await api.post(
      '/transfers',
      { sourceId, destinationId, amount, currency },
      { headers: { 'Idempotency-Key': newIdempotencyKey() } },
    );
    return unwrap<{ transactionId: string; replayed: boolean }>(data);
  },
};
