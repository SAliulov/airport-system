import { useCallback, useEffect, useState, type ReactNode } from 'react';
import {
  clearSession,
  getProfile,
  getToken,
  login as apiLogin,
  logout as apiLogout,
  setToken,
} from '../services/auth';
import { AuthContext, readStoredUser, type AuthState } from './auth-context';

function persistUser(username: string, role: string) {
  localStorage.setItem('airport_user', JSON.stringify({ username, role }));
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(() => !getToken());
  const [state, setState] = useState<AuthState>(() => {
    const stored = readStoredUser();
    return {
      token: getToken(),
      role: stored?.role ?? null,
      username: stored?.username ?? null,
    };
  });

  useEffect(() => {
    const token = getToken();
    if (!token) {
      setReady(true);
      return;
    }

    let cancelled = false;
    getProfile(token)
      .then(profile => {
        if (cancelled) return;
        persistUser(profile.username, profile.role);
        setState({ token, role: profile.role, username: profile.username });
      })
      .catch(() => {
        if (cancelled) return;
        clearSession();
        setState({ token: null, role: null, username: null });
      })
      .finally(() => {
        if (!cancelled) setReady(true);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const rs = await apiLogin(username, password);
    setToken(rs.accessToken);
    persistUser(username, rs.role);
    setState({ token: rs.accessToken, role: rs.role, username });
  }, []);

  const logout = useCallback(async () => {
    const token = getToken();
    try {
      if (token) await apiLogout(token);
    } finally {
      clearSession();
      setState({ token: null, role: null, username: null });
    }
  }, []);

  if (!ready) {
    return null;
  }

  return (
    <AuthContext.Provider value={{ ...state, ready, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
