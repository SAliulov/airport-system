import { useEffect, useRef, useState } from 'react';
import { pingBackend } from '../services/api';
import { isNetworkErrorMessage, NETWORK_ERROR_MESSAGE } from '../utils/apiError';

const RETRY_MS = 5000;
const WATCH_MS = 10000;

/**
 * 1) Пока данные на экране и ошибки нет — периодически пингует backend;
 *    при падении выставляет «Ошибка сети» (устаревшие данные остаются до успешного reload).
 * 2) Пока на экране «Ошибка сети» — пингует и вызывает onRetry при восстановлении.
 */
export function useRetryWhenBackendUp(
  pageError: string | null,
  setPageError: (error: string | null) => void,
  onRetry: () => void,
): boolean {
  const [waiting, setWaiting] = useState(false);
  const onRetryRef = useRef(onRetry);
  const setPageErrorRef = useRef(setPageError);

  useEffect(() => {
    onRetryRef.current = onRetry;
    setPageErrorRef.current = setPageError;
  }, [onRetry, setPageError]);

  const isNetwork = isNetworkErrorMessage(pageError);

  useEffect(() => {
    if (isNetwork) return;

    let cancelled = false;

    async function checkStillUp() {
      if (cancelled || document.visibilityState === 'hidden') return;
      try {
        await pingBackend();
      } catch {
        if (!cancelled) {
          setPageErrorRef.current(NETWORK_ERROR_MESSAGE);
        }
      }
    }

    void checkStillUp();
    const id = window.setInterval(() => void checkStillUp(), WATCH_MS);
    const onVisible = () => void checkStillUp();
    document.addEventListener('visibilitychange', onVisible);

    return () => {
      cancelled = true;
      window.clearInterval(id);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [isNetwork]);

  useEffect(() => {
    if (!isNetwork) {
      setWaiting(false);
      return;
    }

    setWaiting(true);
    let cancelled = false;

    async function tryReconnect() {
      if (cancelled || document.visibilityState === 'hidden') return;
      try {
        await pingBackend();
        if (!cancelled) {
          setWaiting(false);
          onRetryRef.current();
        }
      } catch {
        /* still down */
      }
    }

    void tryReconnect();
    const id = window.setInterval(() => void tryReconnect(), RETRY_MS);
    const onOnline = () => void tryReconnect();
    window.addEventListener('online', onOnline);

    return () => {
      cancelled = true;
      window.clearInterval(id);
      window.removeEventListener('online', onOnline);
      setWaiting(false);
    };
  }, [isNetwork]);

  return waiting && isNetwork;
}
