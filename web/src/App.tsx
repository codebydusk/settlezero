import React, { useEffect, useState } from 'react';
import { LandingPage } from './pages/LandingPage';
import { InviteResolverPage } from './pages/InviteResolverPage';

export const App: React.FC = () => {
  const [token, setToken] = useState<string | null>(null);

  useEffect(() => {
    // Check path for /join/:token or /invite/:token or query param ?token=
    const pathname = window.location.pathname;
    const searchParams = new URLSearchParams(window.location.search);
    const queryToken = searchParams.get('token');

    if (queryToken) {
      setToken(queryToken);
      return;
    }

    const match = pathname.match(/\/(join|invite)\/([a-zA-Z0-9_-]+)/);
    if (match && match[2]) {
      setToken(match[2]);
    }
  }, []);

  if (token) {
    return <InviteResolverPage token={token} />;
  }

  return <LandingPage />;
};

export default App;

