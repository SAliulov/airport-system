import { useEffect, useMemo, useState } from 'react';
import { getAirportTimezone } from '../utils/airportTime';

export default function DispatcherClock() {
  const [now, setNow] = useState(() => new Date());
  const timezone = getAirportTimezone();

  const airportFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat('ru-RU', {
        timeZone: timezone,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      }),
    [timezone],
  );

  const utcFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat('ru-RU', {
        timeZone: 'UTC',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      }),
    [],
  );

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);

  return (
    <span className="dispatcher-clock" aria-live="polite">
      {timezone}: {airportFormatter.format(now)} | UTC: {utcFormatter.format(now)}
    </span>
  );
}
