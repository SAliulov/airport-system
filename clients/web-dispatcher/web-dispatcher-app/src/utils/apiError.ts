/** То же сообщение, что отдаёт axios interceptor при ERR_NETWORK / без ответа. */
export const NETWORK_ERROR_MESSAGE = 'Ошибка сети';

export function formatApiError(e: unknown): string {
  if (e instanceof Error) return e.message;
  return String(e);
}

/** Сообщение из axios interceptor или нативной сетевой ошибки. */
export function isNetworkErrorMessage(message: string | null | undefined): boolean {
  if (!message) return false;
  const m = message.toLowerCase();
  return m.includes('ошибка сети') || m.includes('network error') || m === 'network error';
}

export function isNetworkError(e: unknown): boolean {
  return isNetworkErrorMessage(formatApiError(e));
}
