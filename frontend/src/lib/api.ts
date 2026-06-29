import axios, { AxiosError, AxiosRequestConfig } from 'axios';
import { tokenStore } from './tokenStore';

/**
 * The API client. A request interceptor attaches the access token (Requirement 13.3); a
 * response interceptor transparently refreshes on 401 and retries once (Requirement 13.4),
 * and ends the session if the refresh token is rejected (Requirement 13.5).
 */
export const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30_000, // Requirement 14.7: 30s client timeout for transfers
});

export interface ApiErrorBody {
  error: { code: string; message: string; fields?: { field: string; issue: string }[] };
}

api.interceptors.request.use((config) => {
  const token = tokenStore.getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshing: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStore.getRefreshToken();
  if (!refreshToken) {
    throw new Error('no refresh token');
  }
  // Use a bare axios call to avoid recursive interceptors.
  const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken });
  const newAccess = data.data.accessToken as string;
  tokenStore.setAccessToken(newAccess);
  return newAccess;
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status;

    if (status === 401 && original && !original._retried && tokenStore.getRefreshToken()) {
      original._retried = true;
      try {
        refreshing = refreshing ?? refreshAccessToken();
        const newAccess = await refreshing;
        refreshing = null;
        original.headers = original.headers ?? {};
        (original.headers as Record<string, string>).Authorization = `Bearer ${newAccess}`;
        return api.request(original);
      } catch (refreshError) {
        refreshing = null;
        tokenStore.endSession(); // Requirement 13.5
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  },
);

/** Extracts the machine-readable error code from an Axios error, if present. */
export function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;
    if (body?.error?.code) return body.error.code;
    if (error.code === 'ECONNABORTED') return 'CLIENT_TIMEOUT';
  }
  return null;
}
