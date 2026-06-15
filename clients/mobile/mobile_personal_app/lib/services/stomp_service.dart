import 'dart:convert';
import 'package:stomp_dart_client/stomp_dart_client.dart';
import '../config.dart';
import '../mappers/realtime_event_mapper.dart';
import '../models/operational_event.dart';
import '../models/realtime_event.dart';

typedef EventCallback = void Function(RealtimeEvent event);
typedef OperationalEventCallback = void Function(OperationalEvent event);
typedef ConnectionStateCallback = void Function(bool connected);

/// STOMP-подключение к backend через raw WebSocket.
/// Рейсы: /topic/flights, /topic/delays, /topic/gate-changes.
/// Журнал: /topic/operational-events — session-scoped.
class StompService {
  StompClient? _client;
  final List<EventCallback> _listeners = [];
  final List<OperationalEventCallback> _operationalListeners = [];
  final List<ConnectionStateCallback> _connectionListeners = [];
  bool _connected = false;
  bool _operationalSubscriptionDesired = false;
  StompUnsubscribe? _operationalUnsubscribe;
  bool _pendingReconnect = false;

  bool get isConnected => _connected;

  void addListener(EventCallback cb) => _listeners.add(cb);
  void removeListener(EventCallback cb) => _listeners.remove(cb);

void addOperationalListener(OperationalEventCallback cb) {
    _operationalListeners.add(cb);
    if (_connected && _operationalUnsubscribe == null) {
      _subscribeOperational();
    }
  }

  void removeOperationalListener(OperationalEventCallback cb) =>
      _operationalListeners.remove(cb);

  void addConnectionListener(ConnectionStateCallback cb) =>
      _connectionListeners.add(cb);
  void removeConnectionListener(ConnectionStateCallback cb) =>
      _connectionListeners.remove(cb);

  void _notifyConnectionState(bool connected) {
    for (final cb in _connectionListeners) {
      cb(connected);
    }
  }

  void connect() {
    if (_client != null) return;

    final uri = Uri.parse(AppConfig.apiBase);
    final wsScheme = uri.scheme == 'https' ? 'wss' : 'ws';
    final wsPort = uri.hasPort ? uri.port : (uri.scheme == 'https' ? 443 : 80);
    final wsUrl = '$wsScheme://${uri.host}:$wsPort/ws/raw';

    _client = StompClient(
      config: StompConfig(
        url: wsUrl,
        onConnect: _onConnect,
        onDisconnect: (_) {
          _connected = false;
          _operationalUnsubscribe = null;
          _notifyConnectionState(false);
        },
        onStompError: (frame) {
          _connected = false;
          _notifyConnectionState(false);
        },
        onWebSocketError: (error) {
          _connected = false;
          _notifyConnectionState(false);
          if (!_pendingReconnect) {
            _pendingReconnect = true;
            Future.delayed(const Duration(seconds: 5), () {
              _pendingReconnect = false;
              if (_client != null && !_connected) {
                _client!.activate();
              }
            });
          }
        },
        reconnectDelay: const Duration(seconds: 5),
      ),
    );
    _client!.activate();
  }

  void _onConnect(StompFrame frame) {
    _connected = true;
    _pendingReconnect = false;
    _notifyConnectionState(true);

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
    if (frame.body == null || frame.body!.isEmpty) return;
    try {
      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      final event = OperationalEvent.fromJson(json);
      for (final cb in _operationalListeners) {
        cb(event);
      }
    } catch (e) {
      // ignore malformed payloads in release
    }
  }

  void _onDelay(StompFrame frame) {
    if (frame.body == null) return;
    try {
      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      final event = RealtimeEventMapper.fromDelayJson(json);
      if (event != null) _emit(event);
    } catch (_) {}
  }

  void _onGateChange(StompFrame frame) {
    if (frame.body == null) return;
    try {
      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      final event = RealtimeEventMapper.fromGateChangeJson(json);
      if (event != null) _emit(event);
    } catch (_) {}
  }

  void _onFlightUpdate(StompFrame frame) {
    if (frame.body == null) return;
    try {
      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      final event = RealtimeEventMapper.fromFlightUpdateJson(json);
      if (event != null) _emit(event);
    } catch (_) {}
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
    _notifyConnectionState(false);
  }
}