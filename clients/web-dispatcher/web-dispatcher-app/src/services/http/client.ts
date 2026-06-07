import axios from 'axios';
import { API_BASE, APP_BASENAME } from '../../config';
import { NETWORK_ERROR_MESSAGE } from '../../utils/apiError';
import { clearSession, getToken } from '../auth';

/** Извлекает текст ошибки из тела ответа backend. */
export function backendErrorText(data: unknown): string | null {
  if (!data) return null;
  if (typeof data === 'string') return data;
  if (typeof data === 'object') {
    const obj = data as Record<string, unknown>;
    const candidate = obj.message ?? obj.error ?? obj.detail;
    return typeof candidate === 'string' ? candidate : null;
  }
  return null;
}

/** Человекочитаемый текст по HTTP-статусу. */
export function statusText(status?: number): string {
  switch (status) {
    case 400: return 'Некорректные данные запроса';
    case 401: return 'Требуется авторизация';
    case 403: return 'Недостаточно прав для операции';
    case 404: return 'Ресурс не найден';
    case 409: return 'Конфликт данных (например, занятый гейт)';
    case 500: return 'Внутренняя ошибка сервера';
    default: return status ? `HTTP ${status}` : NETWORK_ERROR_MESSAGE;
  }
}

/** Заголовок Authorization для мутаций. */
export function authHeaders() {
  const t = getToken();
  return t ? { Authorization: `Bearer ${t}` } : {};
}

export const apiClient = axios.create({ baseURL: `${API_BASE}/api/v1` });

apiClient.interceptors.response.use(
  response => response,
  error => {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status;
      const url = error.config?.url ?? '';
      const isLoginRequest = url.includes('/auth/login');
      if (status === 401 && !isLoginRequest) {
        clearSession();
        if (!window.location.pathname.endsWith('/login')) {
          window.location.assign(`${APP_BASENAME}/login`);
        }
      }
      const backend = backendErrorText(error.response?.data);
      return Promise.reject(new Error(backend ?? statusText(status)));
    }
    return Promise.reject(error);
  },
);

/** Проверка доступности backend (без JWT). */
export function pingBackend(): Promise<void> {
  return apiClient.get('/airlines', { timeout: 3000 }).then(() => undefined);
}
