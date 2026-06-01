interface PageStatusProps {
  error: string | null;
  waitingForServer?: boolean;
}

export default function PageStatus({ error, waitingForServer }: PageStatusProps) {
  if (!error) return null;
  return (
    <>
      <p className="page-error" role="alert">{error}</p>
      {waitingForServer && (
        <p className="page-reconnect-hint">Ожидание сервера… данные подгрузятся автоматически.</p>
      )}
    </>
  );
}
