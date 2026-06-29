import { useNavigate } from 'react-router-dom';
import './landing.css';

/**
 * Public marketing landing page — a premium retail-bank presentation with header
 * navigation, a two-column hero, quick-service tiles, a security band, and a footer.
 */
export function Landing() {
  const navigate = useNavigate();
  const goLogin = () => navigate('/login', { state: { mode: 'login' } });
  const goRegister = () => navigate('/login', { state: { mode: 'register' } });

  return (
    <div className="lp">
      {/* Utility strip */}
      <div className="lp-utility">
        <div className="lp-container lp-utility-inner">
          <span>Helpline 16xxx &nbsp;·&nbsp; help@ledgerbank.com.bd</span>
          <span className="lp-util-links">
            <a href="#" onClick={(e) => e.preventDefault()}>বাংলা</a>
            <span className="sep">|</span>
            <a href="#" onClick={(e) => e.preventDefault()}>EN</a>
            <span className="sep">|</span>
            <a href="#" onClick={(e) => e.preventDefault()}>Branch &amp; ATM</a>
          </span>
        </div>
      </div>

      {/* Header */}
      <header className="lp-header">
        <div className="lp-container lp-header-inner">
          <img src="/logo.png" alt="Ledger Bank" className="lp-logo" />
          <nav className="lp-nav">
            <a href="#" onClick={(e) => e.preventDefault()}>Personal</a>
            <a href="#" onClick={(e) => e.preventDefault()}>Business</a>
            <a href="#" onClick={(e) => e.preventDefault()}>Cards</a>
            <a href="#" onClick={(e) => e.preventDefault()}>Loans</a>
            <a href="#" onClick={(e) => e.preventDefault()}>Rates</a>
          </nav>
          <button className="lp-btn lp-btn-primary" onClick={goLogin}>Internet Banking Login</button>
        </div>
      </header>

      {/* Hero */}
      <section className="lp-hero">
        <div className="lp-container lp-hero-inner">
          <div className="lp-hero-copy">
            <span className="lp-eyebrow">Banking that moves with you</span>
            <h1>Secure. Simple.<br /><span className="accent">Always with you.</span></h1>
            <p>
              Manage your accounts, transfer funds and track every transaction — anytime,
              anywhere — with Ledger Bank Internet Banking.
            </p>
            <div className="lp-cta">
              <button className="lp-btn lp-btn-primary lp-btn-lg" onClick={goLogin}>
                Login to Your Account →
              </button>
              <button className="lp-btn lp-btn-outline lp-btn-lg" onClick={goRegister}>
                Open an Account
              </button>
            </div>
            <ul className="lp-hero-points">
              <li><strong>Bank-grade security</strong><span>256-bit encryption</span></li>
              <li><strong>Real-time balances</strong><span>Instant ledger updates</span></li>
              <li><strong>24/7 support</strong><span>We're here anytime</span></li>
            </ul>
          </div>

          <div className="lp-hero-art">
            <div className="lp-hero-photo" role="img" aria-label="Customer using Ledger Bank internet banking" />
            <div className="lp-hero-frame" aria-hidden />
          </div>
        </div>
      </section>

      {/* Quick services */}
      <section className="lp-services">
        <div className="lp-container">
          <h2 className="lp-section-title">Everyday Banking, Made Effortless</h2>
          <div className="lp-tiles">
            {[
              { t: 'Fund Transfer', d: 'Move money between accounts instantly and securely.' },
              { t: 'Pay Bills', d: 'Settle utilities and cards from one dashboard.' },
              { t: 'Account Statements', d: 'Download statements with running balances.' },
              { t: 'Card Services', d: 'Manage limits, blocks and renewals on the go.' },
            ].map((s) => (
              <article key={s.t} className="lp-tile">
                <div className="lp-tile-bar" />
                <h3>{s.t}</h3>
                <p>{s.d}</p>
                <a href="#" onClick={(e) => { e.preventDefault(); goLogin(); }}>Get started →</a>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* Trust band */}
      <section className="lp-trust">
        <div className="lp-container lp-trust-inner">
          <div className="lp-trust-item">
            <strong>৳ Strong &amp; Stable</strong>
            <span>A balance sheet you can rely on</span>
          </div>
          <div className="lp-trust-item">
            <strong>Your Privacy Matters</strong>
            <span>We protect your data end to end</span>
          </div>
          <div className="lp-trust-item">
            <strong>Trusted by Thousands</strong>
            <span>Reliable banking, every single day</span>
          </div>
          <div className="lp-trust-item">
            <strong>Regulated</strong>
            <span>Operating under Bangladesh Bank scheme</span>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="lp-footer">
        <div className="lp-container lp-footer-grid">
          <div className="lp-foot-brand">
            <img src="/logo.png" alt="Ledger Bank" className="lp-foot-logo" />
            <p>Internet Banking for a modern Bangladesh. Secure, simple and always with you.</p>
          </div>
          <div className="lp-foot-col">
            <h4>Banking</h4>
            <a href="#" onClick={(e) => e.preventDefault()}>Accounts</a>
            <a href="#" onClick={(e) => e.preventDefault()}>Fund Transfer</a>
            <a href="#" onClick={(e) => e.preventDefault()}>Statements</a>
          </div>
          <div className="lp-foot-col">
            <h4>Support</h4>
            <a href="#" onClick={(e) => e.preventDefault()}>Help Centre</a>
            <a href="#" onClick={(e) => e.preventDefault()}>Branch &amp; ATM</a>
            <a href="#" onClick={(e) => e.preventDefault()}>Contact Us</a>
          </div>
          <div className="lp-foot-col">
            <h4>Get the App</h4>
            <a href="#" onClick={(e) => e.preventDefault()}>Android</a>
            <a href="#" onClick={(e) => e.preventDefault()}>iOS</a>
          </div>
        </div>
        <div className="lp-foot-bar">
          <div className="lp-container lp-foot-bar-inner">
            <span>© {new Date().getFullYear()} Ledger Bank Ltd. All rights reserved.</span>
            <span>Secured with 256-bit encryption</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
