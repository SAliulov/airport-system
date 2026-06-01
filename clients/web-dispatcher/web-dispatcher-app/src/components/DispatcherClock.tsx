import { useEffect, useState } from 'react';

const mskFormatter = new Intl.DateTimeFormat('ru-RU', {
  timeZone: 'Europe/Moscow',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

const utcFormatter = new Intl.DateTimeFormat('ru-RU', {
  timeZone: 'UTC',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hour12: false,
});

export default function DispatcherClock() {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);

  return (
    <span className="dispatcher-clock" aria-live="polite">
      МСК: {mskFormatter.format(now)} | UTC: {utcFormatter.format(now)}
    </span>
  );
}
