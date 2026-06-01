import 'dart:convert';
import 'package:stomp_dart_client/stomp_dart_client.dart';
import '../config.dart';
import '../models/operational_event.dart';
import '../models/realtime_event.dart';

typedef EventCallback = void Function(RealtimeEvent event);
typedef OperationalEventCallback = void Function(OperationalEvent event);

/// STOMP-подключение к backend.
/// Рейсы: /topic/flights, /topic/delays, /topic/gate-changes.
/// Журнал: /topic/operational-events — session-scoped (накопление в RAM, без отписки при смене вкладки).
class StompService {
  StompClient? _client;
  final List<EventCallback> _listeners = [];
  final List<OperationalEventCallback> _operationalListeners = [];
  bool _connected = false;
  bool _operationalSubscriptionDesired = false;
  StompUnsubscribe? _operationalUnsubscribe;

  bool get isConnected => _connected;

  void addListener(EventCallback cb) => _listeners.add(cb);
  void removeListener(EventCallback cb) => _listeners.remove(cb);

  void addOperationalListener(OperationalEventCallback cb) =>
      _operationalListeners.add(cb);
  void removeOperationalListener(OperationalEventCallback cb) =>
      _operationalListeners.remove(cb);

  void connect() {
    if (_client != null) return;

    _client = StompClient(
      config: StompConfig.sockJS(
        url: '${AppConfig.apiBase}/ws',
        onConnect: _onConnect,
        onDisconnect: (_) {
          _connected = false;
          _operationalUnsubscribe = null;
        },
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

    if (_operationalSubscriptionDesired || _operationalListeners.isNotEmpty) {
      _subscribeOperational();
    }
  }

  /// Однократная подписка на журнал; остаётся активной до [disconnect].
  void subscribeOperationalEvents() {
    _operationalSubscriptionDesired = true;
    if (_operationalUnsubscribe != null) return;
    if (_connected && _client != null) {
      _subscribeOperational();
    }
  }

  void unsubscribeOperationalEvents() {
    _operationalSubscriptionDesired = false;
    _operationalUnsubscribe?.call();
    _operationalUnsubscribe = null;
  }

  void _subscribeOperational() {
    if (_client == null || !_connected) return;
    _operationalUnsubscribe?.call();
    _operationalUnsubscribe = _client!.subscribe(
      destination: '/topic/operational-events',
      callback: _onOperationalEvent,
    );
  }

  void _onOperationalEvent(StompFrame frame) {
    if (frame.body == null) return;
    try {
      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      final event = OperationalEvent.fromJson(json);
      for (final cb in _operationalListeners) {
        cb(event);
      }
    } catch (_) {
      // ignore malformed payload
    }
  }

  void _onDelay(StompFrame frame) {
    if (frame.body == null) return;
    try {
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
    } catch (_) {
      // ignore malformed payload
    }
  }

  void _onGateChange(StompFrame frame) {
    if (frame.body == null) return;
    try {
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
    } catch (_) {
      // ignore malformed payload
    }
  }

  void _onFlightUpdate(StompFrame frame) {
    if (frame.body == null) return;
    try {
      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      final flightId = json['flightId'] as int? ?? 0;
      final status = json['status']?.toString() ?? '';

      _emit(RealtimeEvent(
        type: EventType.flightUpdate,
        flightId: flightId,
        title: 'Статус рейса #$flightId',
        subtitle: status,
      ));
    } catch (_) {
      // ignore malformed payload
    }
  }

  void _emit(RealtimeEvent e) {
    for (final cb in _listeners) {
      cb(e);
    }
  }

  void disconnect() {
    unsubscribeOperationalEvents();
    _client?.deactivate();
    _client = null;
    _connected = false;
  }
}
