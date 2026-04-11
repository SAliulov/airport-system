import 'dart:convert';
import 'package:stomp_dart_client/stomp_dart_client.dart';
import '../config.dart';
import '../models/realtime_event.dart';

typedef EventCallback = void Function(RealtimeEvent event);

/// STOMP-подключение к backend; подписывается на /topic/delays и /topic/gate-changes.
class StompService {
  StompClient? _client;
  final List<EventCallback> _listeners = [];
  bool _connected = false;

  bool get isConnected => _connected;

  void addListener(EventCallback cb) => _listeners.add(cb);
  void removeListener(EventCallback cb) => _listeners.remove(cb);

  void connect() {
    if (_client != null) return;

    _client = StompClient(
      config: StompConfig.sockJS(
        url: '${AppConfig.apiBase}/ws',
        onConnect: _onConnect,
        onDisconnect: (_) => _connected = false,
        onWebSocketError: (_) => _connected = false,
        reconnectDelay: const Duration(seconds: 5),
      ),
    );
    _client!.activate();
  }

  void _onConnect(StompFrame frame) {
    _connected = true;

    _client!.subscribe(
      destination: '/topic/delays',
      callback: _onDelay,
    );
    _client!.subscribe(
      destination: '/topic/gate-changes',
      callback: _onGateChange,
    );
    _client!.subscribe(
      destination: '/topic/flights',
      callback: _onFlightUpdate,
    );
  }

  void _onDelay(StompFrame frame) {
    if (frame.body == null) return;
    final json = jsonDecode(frame.body!) as Map<String, dynamic>;
    final flightId = json['flightId'] as int? ?? 0;
    final w = json['warning'] as Map<String, dynamic>? ?? {};
    final minutes = w['delayMinutes'] ?? 0;
    final reason = w['reason']?.toString() ?? '';

    _emit(RealtimeEvent(
      type: EventType.delay,
      flightId: flightId,
      title: 'Задержка рейса #$flightId',
      subtitle: '$minutes мин${reason.isNotEmpty ? ': $reason' : ''}',
    ));
  }

  void _onGateChange(StompFrame frame) {
    if (frame.body == null) return;
    final json = jsonDecode(frame.body!) as Map<String, dynamic>;
    final flightId = json['flightId'] as int? ?? 0;
    final a = json['assignment'] as Map<String, dynamic>? ?? {};
    final gate = a['gate'] as Map<String, dynamic>?;
    final gateNum = gate?['gateNumber']?.toString() ?? '?';

    _emit(RealtimeEvent(
      type: EventType.gateChange,
      flightId: flightId,
      title: 'Смена гейта рейса #$flightId',
      subtitle: 'Новый гейт: $gateNum',
    ));
  }

  void _onFlightUpdate(StompFrame frame) {
    if (frame.body == null) return;
    final json = jsonDecode(frame.body!) as Map<String, dynamic>;
    final flightId = json['flightId'] as int? ?? 0;
    final status = json['status']?.toString() ?? '';

    _emit(RealtimeEvent(
      type: EventType.flightUpdate,
      flightId: flightId,
      title: 'Статус рейса #$flightId',
      subtitle: status,
    ));
  }

  void _emit(RealtimeEvent e) {
    for (final cb in _listeners) {
      cb(e);
    }
  }

  void disconnect() {
    _client?.deactivate();
    _client = null;
    _connected = false;
  }
}
