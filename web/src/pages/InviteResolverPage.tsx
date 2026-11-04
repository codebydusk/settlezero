import React, { useEffect, useState } from 'react';

interface InviteMetadata {
  groupId: string;
  groupName: string;
  currencyCode: string;
  currencyScale: number;
  createdAt: number;
}

interface InviteResolverProps {
  token: string;
}

export const InviteResolverPage: React.FC<InviteResolverProps> = ({ token }) => {
  const [metadata, setMetadata] = useState<InviteMetadata | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [copied, setCopied] = useState<boolean>(false);

  useEffect(() => {
    // Attempt to load privacy-preserving invite metadata from sync server
    const fetchMetadata = async () => {
      try {
        const res = await fetch(`http://localhost:8080/api/v1/invites/${token}`);
        if (res.ok) {
          const data = await res.json();
          setMetadata(data);
        }
      } catch {
        // Local simulation / offline fallback
      } finally {
        setLoading(false);
      }
    };

    fetchMetadata();
  }, [token]);

  const deepLinkUrl = `settlezero://invite/${token}`;

  const handleCopy = () => {
    navigator.clipboard.writeText(token);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="container">
      <header className="navbar">
        <a href="/" className="logo">
          <span>SettleZero</span>
          <span className="logo-badge">Invite</span>
        </a>
      </header>

      <div className="invite-container">
        <div className="invite-card">
          <div className="group-avatar">
            {metadata ? metadata.groupName.charAt(0).toUpperCase() : '0'}
          </div>

          {loading ? (
            <div>
              <h2>Looking up invitation...</h2>
              <p style={{ color: 'var(--text-secondary)', marginTop: '8px' }}>
                Contacting SettleZero network
              </p>
            </div>
          ) : metadata ? (
            <div>
              <p style={{ color: 'var(--accent-green)', fontWeight: 600, fontSize: '0.9rem', marginBottom: '8px' }}>
                YOU HAVE BEEN INVITED TO JOIN
              </p>
              <h1 style={{ fontSize: '2rem', marginBottom: '8px' }}>{metadata.groupName}</h1>
              <p style={{ color: 'var(--text-secondary)', marginBottom: '24px' }}>
                Default Currency: <strong>{metadata.currencyCode}</strong>
              </p>
            </div>
          ) : (
            <div>
              <p style={{ color: 'var(--accent-green)', fontWeight: 600, fontSize: '0.9rem', marginBottom: '8px' }}>
                GROUP INVITATION
              </p>
              <h1 style={{ fontSize: '1.8rem', marginBottom: '8px' }}>Join Group</h1>
              <p style={{ color: 'var(--text-secondary)', marginBottom: '24px' }}>
                Token: <code style={{ backgroundColor: 'var(--border)', padding: '2px 6px', borderRadius: '4px' }}>{token}</code>
              </p>
            </div>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', marginTop: '16px' }}>
            <a href={deepLinkUrl} className="btn-primary" style={{ justifyContent: 'center' }}>
              Open in SettleZero App
            </a>

            <button
              onClick={handleCopy}
              className="btn-secondary"
              style={{ justifyContent: 'center' }}
            >
              {copied ? '✓ Token Copied' : 'Copy Invite Token'}
            </button>

            <a
              href="/settlezero-debug.apk"
              download
              className="btn-secondary"
              style={{ justifyContent: 'center', opacity: 0.8 }}
            >
              Download Android APK
            </a>
          </div>

          <p style={{ color: 'var(--text-secondary)', fontSize: '0.8rem', marginTop: '24px' }}>
            SettleZero is an accountless, zero-tracking app. Opening this link launches the app directly on your device.
          </p>
        </div>
      </div>
    </div>
  );
};

