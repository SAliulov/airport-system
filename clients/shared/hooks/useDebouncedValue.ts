import { useEffect, useState } from 'react';

/** Задержка перед применением текстового фильтра (поиск), чтобы не дёргать API на каждый символ. */
export function useDebouncedValue<T>(value: T, delayMs = 400): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
