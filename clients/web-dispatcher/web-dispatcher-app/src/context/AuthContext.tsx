import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { clearToken, getToken, login as apiLogin, logout as apiLogout, setToken } from '../services/auth';

interface AuthState {
  token: string | null;
  role: string | null;
  username: string | null;
}

interface AuthCtx extends AuthState {
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const Ctx = createContext<AuthCtx | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(() => ({
    token: getToken(),
    role: null,
    username: null,
  }));

  // restore username/role from localStorage on mount
  useEffect(() => {
    const stored = localStorage.getItem('airport_user');
    if (stored) {
      try {
        const u = JSON.parse(stored) as { username: string; role: string };
        setState(s => ({ ...s, username: u.username, role: u.role }));
      } catch {
        /* ignore */
      }
    }
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const rs = await apiLogin(username, password);
    setToken(rs.accessToken);
    localStorage.setItem('airport_user', JSON.stringify({ username, role: rs.role }));
    setState({ token: rs.accessToken, role: rs.role, username });
  }, []);

  const logout = useCallback(async () => {
    if (state.token) await apiLogout(state.token);
    clearToken();
    localStorage.removeItem('airport_user');
    setState({ token: null, role: null, username: null });
  }, [state.token]);

  return <Ctx.Provider value={{ ...state, login, logout }}>{children}</Ctx.Provider>;
}

export function useAuth() {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
