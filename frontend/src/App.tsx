import { useEffect, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import './app.css';
import { tokenStore } from './lib/tokenStore';
import { Landing } from './features/landing/Landing';
import { LoginPage } from './features/auth/LoginPage';
import { DashboardLayout } from './features/dashboard/DashboardLayout';
import { DashboardHome } from './features/dashboard/DashboardHome';
import { AccountHistory } from './features/dashboard/AccountHistory';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

function RequireAuth({ children }: { children: JSX.Element }) {
  const navigate = useNavigate();
  useEffect(() => {
    // Requirement 13.5: when the session ends (refresh failed), return to login.
    tokenStore.onSessionEnded(() => navigate('/login', { replace: true }));
  }, [navigate]);
  return tokenStore.isAuthenticated() ? children : <Navigate to="/login" replace />;
}

export function App() {
  const [authed, setAuthed] = useState(tokenStore.isAuthenticated());

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route
            path="/"
            element={authed ? <Navigate to="/dashboard" replace /> : <Landing />}
          />
          <Route path="/login" element={<LoginPage onAuthed={() => setAuthed(true)} />} />
          <Route
            path="/dashboard"
            element={
              <RequireAuth>
                <DashboardLayout onLogout={() => setAuthed(false)} />
              </RequireAuth>
            }
          >
            <Route index element={<DashboardHome />} />
            <Route path="accounts/:accountId" element={<AccountHistory />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
