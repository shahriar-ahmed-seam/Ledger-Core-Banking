/**
 * In-memory access token plus persisted refresh token. The access token is kept in memory
 * (not localStorage) to reduce XSS exposure; the refresh token persists so a page reload
 * can re-establish the session (Requirement 13).
 */
const REFRESH_KEY = 'ledger.refreshToken';

let accessToken: string | null = null;
let onSessionEnded: (() => void) | null = null;

export const tokenStore = {
  setTokens(access: string, refresh: string) {
    accessToken = access;
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  setAccessToken(access: string) {
    accessToken = access;
  },
  getAccessToken(): string | null {
    return accessToken;
  },
  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  },
  clear() {
    accessToken = null;
    localStorage.removeItem(REFRESH_KEY);
  },
  isAuthenticated(): boolean {
    return !!localStorage.getItem(REFRESH_KEY);
  },
  onSessionEnded(cb: () => void) {
    onSessionEnded = cb;
  },
  endSession() {
    this.clear();
    onSessionEnded?.();
  },
};
