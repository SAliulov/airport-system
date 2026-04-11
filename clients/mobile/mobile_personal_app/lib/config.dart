/// Конфигурация подключения к backend.
class AppConfig {
  /// HTTP base URL backend. На Android-эмуляторе `10.0.2.2` — хост-машина.
  /// В Chrome (web) — `localhost`. Переопределяется через `--dart-define=API_BASE=...`.
  static const String apiBase = String.fromEnvironment(
    'API_BASE',
    defaultValue: 'http://localhost:8080',
  );

  /// WebSocket URL для STOMP (SockJS raw fallback).
  static String get wsUrl {
    final uri = Uri.parse(apiBase);
    final scheme = uri.scheme == 'https' ? 'wss' : 'ws';
    return '$scheme://${uri.host}:${uri.port}/ws/websocket';
  }
}
