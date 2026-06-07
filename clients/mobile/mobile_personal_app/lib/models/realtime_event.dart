enum EventType { delay, gateChange, flightUpdate }

/// Обёртка для ленты realtime-событий в UI.
class RealtimeEvent {
  final EventType type;
  final int flightId;
  final String title;
  final String subtitle;
  final Map<String, dynamic>? payload;
  final DateTime receivedAt;

  RealtimeEvent({
    required this.type,
    required this.flightId,
    required this.title,
    required this.subtitle,
    this.payload,
    DateTime? receivedAt,
  }) : receivedAt = receivedAt ?? DateTime.now();
}
