import React from 'react';

export const LandingPage: React.FC = () => {
  return (
    <div className="container">
      <header className="navbar">
        <div className="logo">
          <span>SettleZero</span>
          <span className="logo-badge">v1.0</span>
        </div>
        <div>
          <a
            href="https://github.com"
            target="_blank"
            rel="noopener noreferrer"
            className="btn-secondary"
          >
            GitHub
          </a>
        </div>
      </header>

      <section className="hero">
        <div className="hero-tag">Zero subscriptions • Zero ads • Zero drama</div>
        <h1>Split bills, settle to zero.<br />Keep the app free.</h1>
        <p>
          A deliberately boring, simple, accountless, local-first expense splitter
          for groups of people who just want to know who owes whom.
        </p>
        <div className="hero-cta">
          <a href="#download" className="btn-primary">
            Download Android APK
          </a>
          <a href="settlezero://join" className="btn-secondary">
            Open Installed App
          </a>
        </div>
      </section>

      <section className="features-section">
        <h2 className="section-title">Built with Invariants, Not Features</h2>
        <p className="section-subtitle">
          Engineered to never charge you, never track you, and work 100% offline in the middle of nowhere.
        </p>

        <div className="grid">
          <div className="feature-card">
            <div className="feature-icon">⚡</div>
            <h3>100% Local-First &amp; Offline</h3>
            <p>
              Reads and writes straight to your local SQLite database. Everything works without network:
              creating groups, adding members, splitting expenses, and simplifying debts.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-icon">🛡️</div>
            <h3>No Accounts, Zero Telemetry</h3>
            <p>
              No email, password, phone number, or social sign-in. Identities are hardware-backed and device-scoped.
              Zero third-party tracking SDKs.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-icon">🔢</div>
            <h3>Integer Minor-Unit Math</h3>
            <p>
              Strictly zero floating-point arithmetic. All monetary values are 64-bit integers with deterministic
              remainder distribution, preventing penny drift forever.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-icon">🔄</div>
            <h3>Deterministic Simplification</h3>
            <p>
              Greedy min-cash-flow algorithm with strict lexicographical tie-breaking ensures identical repayment
              paths across all distributed offline devices.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-icon">🔐</div>
            <h3>SAS Claim Verification</h3>
            <p>
              Existing-member claims utilize a 4-digit Short Authentication String (SAS code) verified
              out-of-band by group admins, preventing invite link takeovers.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-icon">📦</div>
            <h3>Open JSON Portability</h3>
            <p>
              You own your data. Export your full group history, members, expenses, and settlements into a canonical
              v1.0 JSON document anytime. Clone or backup with zero friction.
            </p>
          </div>
        </div>
      </section>

      <section id="download" className="hero" style={{ padding: '60px 0 80px' }}>
        <h2>Ready to ditch subscription bill-splitters?</h2>
        <p style={{ marginTop: '12px' }}>
          SettleZero is completely free and open source.
        </p>
        <div className="hero-cta" style={{ marginTop: '24px' }}>
          <a
            href="/settlezero-debug.apk"
            download
            className="btn-primary"
          >
            Download Latest APK
          </a>
        </div>
      </section>

      <footer>
        <p>SettleZero • Open Source Local-First Group Expense Splitter • MIT License</p>
      </footer>
    </div>
  );
};

