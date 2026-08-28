import { useEffect, useState } from 'react';

interface ConnectionStatusBannerProps {
  connected: boolean;
  message?: string;
}

/** Баннер о разрыве WS-соединения. Показывается с задержкой, чтобы не мигать при обычном подключении. */
export function ConnectionStatusBanner({ connected, message }: ConnectionStatusBannerProps) {
  const [showWarning, setShowWarning] = useState(false);

  useEffect(() => {
    if (connected) {
      setShowWarning(false);
      return;
    }
    const timer = setTimeout(() => setShowWarning(true), 4000);
    return () => clearTimeout(timer);
  }, [connected]);

  if (!showWarning) return null;

  return (
    <div className="connection-status-banner" role="status">
      {message ?? 'Нет связи с сервером — данные могут быть устаревшими, обновляются вручную.'}
    </div>
  );
}
