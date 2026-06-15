import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { fetchAirportConfig } from '../api/fetchAirportConfig';
import type { AirportConfigRs } from '../types/airportConfig';
import { clearAirportRuntime, setAirportRuntime } from '../utils/airportRuntime';

export type AirportConfigStatus = 'loading' | 'resolved' | 'error';

type AirportConfigContextValue = {
  config: AirportConfigRs;
  status: AirportConfigStatus;
  error: string | null;
  retry: () => void;
};

const AirportConfigContext = createContext<AirportConfigContextValue | null>(null);

export type DevAirportFallback = Partial<AirportConfigRs>;

export interface AirportConfigProviderProps {
  apiBase: string;
  children: ReactNode;
  /** Только dev: fallback если API недоступен и заданы оба поля. */
  devFallback?: DevAirportFallback;
  loadingLabel?: string;
}

function resolveDevFallback(fallback?: DevAirportFallback): AirportConfigRs | null {
  if (!fallback?.homeIata || !fallback?.timezone) return null;
  return {
    homeIata: fallback.homeIata.trim().toUpperCase(),
    timezone: fallback.timezone,
    gatePlanWindowHours: fallback.gatePlanWindowHours ?? 2,
    gatePostGraceMinutes: fallback.gatePostGraceMinutes ?? 5,
  };
}

/**
 * Загружает GET /api/v1/airport и блокирует children до resolved.
 * После resolve синхронизирует {@link setAirportRuntime} для утилит вне React.
 */
export function AirportConfigProvider({
  apiBase,
  children,
  devFallback,
  loadingLabel = 'Загрузка конфигурации аэропорта…',
}: AirportConfigProviderProps) {
  const [config, setConfig] = useState<AirportConfigRs | null>(null);
  const [status, setStatus] = useState<AirportConfigStatus>('loading');
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  const retry = useCallback(() => {
    clearAirportRuntime();
    setConfig(null);
    setStatus('loading');
    setError(null);
    setAttempt(n => n + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setStatus('loading');
    setError(null);

    fetchAirportConfig(apiBase)
      .then(result => {
        if (cancelled) return;
        setAirportRuntime(result);
        setConfig(result);
        setStatus('resolved');
      })
      .catch(e => {
        if (cancelled) return;
        const dev = resolveDevFallback(devFallback);
        if (dev) {
          setAirportRuntime(dev);
          setConfig(dev);
          setStatus('resolved');
          return;
        }
        setStatus('error');
        setError(e instanceof Error ? e.message : String(e));
      });

    return () => {
      cancelled = true;
    };
  }, [apiBase, devFallback, attempt]);

  const value = useMemo<AirportConfigContextValue | null>(() => {
    if (!config || status !== 'resolved') return null;
    return { config, status, error, retry };
  }, [config, status, error, retry]);

  if (status === 'loading') {
    return (
      <div className="airport-config-gate airport-config-gate--loading" role="status">
        {loadingLabel}
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className="airport-config-gate airport-config-gate--error" role="alert">
        <p>Не удалось загрузить конфигурацию аэропорта.</p>
        {error && <p className="airport-config-gate__detail">{error}</p>}
        <button type="button" onClick={retry}>
          Повторить
        </button>
      </div>
    );
  }

  return (
    <AirportConfigContext.Provider value={value!}>
      {children}
    </AirportConfigContext.Provider>
  );
}

export function useAirportConfig(): AirportConfigRs {
  const ctx = useContext(AirportConfigContext);
  if (!ctx) {
    throw new Error('useAirportConfig must be used within AirportConfigProvider');
  }
  return ctx.config;
}
