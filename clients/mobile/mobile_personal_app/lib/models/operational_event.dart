/// Категория события операционной ленты (mirror backend OperationalEventCategory).
enum OperationalEventCategory {
  gate,
  status,
  aircraft,
  delay,
  schedule,
  flight,
}

OperationalEventCategory? parseOperationalCategory(String? raw) {
  if (raw == null) return null;
  switch (raw.toUpperCase()) {
    case 'GATE':
      return OperationalEventCategory.gate;
    case 'STATUS':
      return OperationalEventCategory.status;
    case 'AIRCRAFT':
      return OperationalEventCategory.aircraft;
    case 'DELAY':
      return OperationalEventCategory.delay;
    case 'SCHEDULE':
      return OperationalEventCategory.schedule;
    case 'FLIGHT':
      return OperationalEventCategory.flight;
    default:
      return null;
  }
}

/// Событие из {@code /topic/operational-events}.
class OperationalEvent {
  final String timestamp;
  final String user;
  final OperationalEventCategory? category;
  final String message;
  final String? details;
  final int? flightId;
  final String? flightNumber;
  final DateTime receivedAt;

  OperationalEvent({
    required this.timestamp,
    required this.user,
    required this.category,
    required this.message,
    this.details,
    this.flightId,
    this.flightNumber,
    DateTime? receivedAt,
  }) : receivedAt = receivedAt ?? DateTime.now();

  factory OperationalEvent.fromJson(Map<String, dynamic> json) {
    return OperationalEvent(
      timestamp: json['timestamp']?.toString() ?? '',
      user: json['user']?.toString() ?? '',
      category: parseOperationalCategory(json['category']?.toString()),
      message: json['message']?.toString() ?? '',
      details: json['details']?.toString(),
      flightId: json['flightId'] as int?,
      flightNumber: json['flightNumber']?.toString(),
    );
  }
}
