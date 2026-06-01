import axios from 'axios';
import { API_BASE } from '../config';
import type { LoginRs, UserProfileRs } from '../types';

const KEY = 'airport_jwt';

export function getToken(): string | null {
  return localStorage.getItem(KEY);
}

export function setToken(token: string) {
  localStorage.setItem(KEY, token);
}

export function clearToken() {
  localStorage.removeItem(KEY);
}

export function clearSession() {
  clearToken();
  localStorage.removeItem('airport_user');
}

export async function login(username: string, password: string): Promise<LoginRs> {
  const res = await axios.post<LoginRs>(`${API_BASE}/api/v1/auth/login`, {
    username,
    password,
    client: 'DISPATCHER',
  });
  return res.data;
}

export async function logout(token: string): Promise<void> {
  await axios.post(
    `${API_BASE}/api/v1/auth/logout`,
    {},
    { headers: { Authorization: `Bearer ${token}` } },
  );
}

export async function getProfile(token: string): Promise<UserProfileRs> {
  const res = await axios.get<UserProfileRs>(`${API_BASE}/api/v1/auth/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.data;
}
