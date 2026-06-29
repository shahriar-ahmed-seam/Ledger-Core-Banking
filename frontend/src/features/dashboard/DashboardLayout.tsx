import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { authApi } from '../../lib/services';

/**
 * Authenticated internet-banking shell: utility strip, brand header, left navigation,
 * routed content, and footer.
 */
export function DashboardLayout({ onLogout }: { onLogout: () => void }) {
  const navigate = useNavigate();
  const location = useLocation();

  async function handleLogout() {
    await authApi.logout(); // Requirement 13.6
    onLogout();
    navigate('/', { replace: true }); // return to the public landing page
  }

  const today = new Date().toLocaleDateString('en-GB', {
    weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
  });
  const isHome = location.pathname === '/dashboard';

  return (
    <div className="app-shell">
      <div className="utility-bar">
        <div className="utility-inner">
          <span className="hide-sm">Last login: this session · {today}</span>
          <span>Helpline 16xxx · help@ledgerbank.com.bd</span>
        </div>
      </div>

      <header className="brandbar">
        <div className="brandbar-inner">
          <div className="brand">
            <span className="brand-mark">৳</span>
            <span className="brand-name">
              <strong>Ledger Bank</strong>
              <span>Internet Banking</span>
            </span>
          </div>
          <div className="brandbar-user">
            <div className="who">
              <strong>Welcome</strong>
              <span>Personal Banking</span>
            </div>
            <button className="btn btn-secondary btn-sm" onClick={handleLogout}>Log Out</button>
          </div>
        </div>
      </header>

      <div className="body-wrap">
        <nav className="sidebar">
          <div className="sidebar-group">
            <div className="sidebar-group-title">Main Menu</div>
            <a className={`nav-item ${isHome ? 'active' : ''}`} onClick={() => navigate('/dashboard')}>
              <span className="nav-ico">▤</span> Account Summary
            </a>
            <a className={`nav-item ${location.pathname.startsWith('/dashboard/accounts') ? 'active' : ''}`}
               onClick={() => navigate('/dashboard')}>
              <span className="nav-ico">⇄</span> Fund Transfer
            </a>
            <a className="nav-item" onClick={() => navigate('/dashboard')}>
              <span className="nav-ico">▦</span> Transaction History
            </a>
          </div>
          <div className="sidebar-group">
            <div className="sidebar-group-title">Support</div>
            <a className="nav-item" href="#" onClick={(e) => e.preventDefault()}>
              <span className="nav-ico">☎</span> Contact Bank
            </a>
          </div>
        </nav>

        <main className="content">
          <Outlet />
        </main>
      </div>

      <footer className="footer">
        <div className="footer-inner">
          <span>© {new Date().getFullYear()} Ledger Bank Ltd. All rights reserved.</span>
          <span>Secured with 256-bit encryption</span>
        </div>
      </footer>
    </div>
  );
}
