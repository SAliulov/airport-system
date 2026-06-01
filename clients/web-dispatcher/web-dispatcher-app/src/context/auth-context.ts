import { createContext } from 'react';

export interface AuthState {
  token: string | null;
  role: string | null;
  username: string | null;
}

export interface AuthCtx extends AuthState {
  ready: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthCtx | null>(null);

export function readStoredUser(): { username: string; role: string } | null {
  const stored = localStorage.getItem('airport_user');
  if (!stored) return null;
  try {
    return JSON.parse(stored) as { username: string; role: string };
  } catch {
    return null;
  }
}
