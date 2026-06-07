import { useEffect, useMemo, useState } from 'react';
import { getAirportTimezone, todayAirportDate } from '../utils/airportTime';

/** Часы табло + календарный день аэропорта (обновляется каждую секунду). */
export function useAirportClock() {
  const [now, setNow] = useState(() => new Date());
  const timezone = getAirportTimezone();

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);

  const airportToday = todayAirportDate(now);
  const timeLabel = useMemo(
    () =>
      now.toLocaleTimeString('ru-RU', {
        timeZone: timezone,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      }),
    [now, timezone],
  );
  return { now, airportToday, timeLabel, timezone };
}
