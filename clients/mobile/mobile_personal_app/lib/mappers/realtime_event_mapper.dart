import '../models/realtime_event.dart';

/// Маппинг STOMP JSON → [RealtimeEvent] (UI strings здесь, не в transport-слое).
class RealtimeEventMapper {
  RealtimeEventMapper._();

  static RealtimeEvent? fromDelayJson(Map<String, dynamic> json) {
    final flightId = _parseInt(json['flightId']);
    if (flightId == null) return null;
    final w = json['warning'] as Map<String, dynamic>? ?? {};
    final minutes = w['delayMinutes'] ?? 0;
    final reason = w['reason']?.toString() ?? '';
    return RealtimeEvent(
      type: EventType.delay,
      flightId: flightId,
      title: 'Задержка #$flightId',
      subtitle: '$minutes мин${reason.isNotEmpty ? ': $reason' : ''}',
      payload: json,
    );
  }

  static RealtimeEvent? fromGateChangeJson(Map<String, dynamic> json) {
    final flightId = _parseInt(json['flightId']);
    if (flightId == null) return null;
    final a = json['assignment'] as Map<String, dynamic>? ?? {};
    final gate = a['gate'] as Map<String, dynamic>?;
    final gateNum = gate?['gateNumber']?.toString() ?? '?';
    return RealtimeEvent(
      type: EventType.gateChange,
      flightId: flightId,
      title: 'Смена гейта #$flightId',
      subtitle: 'Новый гейт: $gateNum',
      payload: json,
    );
  }

  static RealtimeEvent? fromFlightUpdateJson(Map<String, dynamic> json) {
    final flightId = _parseInt(json['flightId']);
    if (flightId == null) return null;
    final status = json['status']?.toString() ?? '';
    final schedule = json['schedule'] as Map<String, dynamic>?;
    final flightNumber = schedule?['flightNumber']?.toString() ?? '';
    final label = flightNumber.isNotEmpty ? flightNumber : '#$flightId';
    final aircraft = json['aircraftType'] as Map<String, dynamic>?;
    final icao = aircraft?['icaoCode']?.toString();
    return RealtimeEvent(
      type: EventType.flightUpdate,
      flightId: flightId,
      title: icao != null ? 'Тип ВС $label' : 'Статус $label',
      subtitle: icao ?? status,
      payload: json,
    );
  }

  static int? _parseInt(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse('$value');
  }
}
