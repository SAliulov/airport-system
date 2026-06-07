import { useCallback, useEffect, useRef, useState } from 'react';

const HIGHLIGHT_MS = 8000;

/** Подсветка строки рейса после WebSocket-события (8 секунд). */
export function useFlightHighlight() {
  const [highlightIds, setHighlightIds] = useState<Set<number>>(new Set());
  const highlightTimers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  useEffect(() => {
    const timers = highlightTimers.current;
    return () => {
      timers.forEach(t => clearTimeout(t));
      timers.clear();
    };
  }, []);

  const highlight = useCallback((flightId: number) => {
    setHighlightIds(prev => new Set(prev).add(flightId));
    const prev = highlightTimers.current.get(flightId);
    if (prev) clearTimeout(prev);
    const timer = setTimeout(() => {
      setHighlightIds(s => {
        const next = new Set(s);
        next.delete(flightId);
        return next;
      });
      highlightTimers.current.delete(flightId);
    }, HIGHLIGHT_MS);
    highlightTimers.current.set(flightId, timer);
  }, []);

  return { highlightIds, highlight };
}
