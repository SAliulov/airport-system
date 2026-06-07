import 'delay_warning.dart';
import 'gate_assignment.dart';

class FlightDetail {
  final int flightId;
  final String status;
  final String? actualDeparture;
  final String? actualArrival;
  final String flightNumber;
  final String originAirport;
  final String destinationAirport;
  final String scheduledDeparture;
  final String scheduledArrival;
  final String? airlineName;
  final String? aircraftIcao;
  final GateAssignment? currentGateAssignment;
  final List<DelayWarning> delayWarnings;

  FlightDetail({
    required this.flightId,
    required this.status,
    this.actualDeparture,
    this.actualArrival,
    required this.flightNumber,
    required this.originAirport,
    required this.destinationAirport,
    required this.scheduledDeparture,
    required this.scheduledArrival,
    this.airlineName,
    this.aircraftIcao,
    this.currentGateAssignment,
    this.delayWarnings = const [],
  });

  String get route => '$originAirport \u2192 $destinationAirport';

  String get gateLabel {
    final g = currentGateAssignment?.gate;
    if (g == null) return '\u2014';
    final t = g.terminal != null ? ' (${g.terminal})' : '';
    return '${g.gateNumber}$t';
  }

  factory FlightDetail.fromJson(Map<String, dynamic> json) {
    final schedule = json['schedule'] as Map<String, dynamic>? ?? {};
    final airline = schedule['airline'] as Map<String, dynamic>?;
    final aircraft = json['aircraftType'] as Map<String, dynamic>?;
    final gateJson = json['currentGateAssignment'] as Map<String, dynamic>?;
    final warningsJson = json['delayWarnings'] as List<dynamic>? ?? [];

    return FlightDetail(
      flightId: json['flightId'] is num
          ? (json['flightId'] as num).toInt()
          : int.tryParse('${json['flightId']}') ?? 0,
      status: json['status']?.toString() ?? '',
      actualDeparture: json['actualDeparture']?.toString(),
      actualArrival: json['actualArrival']?.toString(),
      flightNumber: schedule['flightNumber']?.toString() ?? '',
      originAirport: (schedule['originAirport']?.toString() ?? '').trim(),
      destinationAirport:
          (schedule['destinationAirport']?.toString() ?? '').trim(),
      scheduledDeparture: json['scheduledDeparture']?.toString()
          ?? schedule['scheduledDeparture']?.toString()
          ?? '',
      scheduledArrival: json['scheduledArrival']?.toString()
          ?? schedule['scheduledArrival']?.toString()
          ?? '',
      airlineName: airline?['name']?.toString(),
      aircraftIcao: aircraft?['icaoCode']?.toString(),
      currentGateAssignment:
          gateJson != null ? GateAssignment.fromJson(gateJson) : null,
      delayWarnings: warningsJson
          .map((e) => DelayWarning.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  /// Копия с частичной заменой полей (realtime patch).
  FlightDetail copyWith({
    int? flightId,
    String? status,
    String? actualDeparture,
    String? actualArrival,
    String? flightNumber,
    String? originAirport,
    String? destinationAirport,
    String? scheduledDeparture,
    String? scheduledArrival,
    String? airlineName,
    String? aircraftIcao,
    GateAssignment? currentGateAssignment,
    List<DelayWarning>? delayWarnings,
  }) {
    return FlightDetail(
      flightId: flightId ?? this.flightId,
      status: status ?? this.status,
      actualDeparture: actualDeparture ?? this.actualDeparture,
      actualArrival: actualArrival ?? this.actualArrival,
      flightNumber: flightNumber ?? this.flightNumber,
      originAirport: originAirport ?? this.originAirport,
      destinationAirport: destinationAirport ?? this.destinationAirport,
      scheduledDeparture: scheduledDeparture ?? this.scheduledDeparture,
      scheduledArrival: scheduledArrival ?? this.scheduledArrival,
      airlineName: airlineName ?? this.airlineName,
      aircraftIcao: aircraftIcao ?? this.aircraftIcao,
      currentGateAssignment: currentGateAssignment ?? this.currentGateAssignment,
      delayWarnings: delayWarnings ?? this.delayWarnings,
    );
  }

  /// Патчит существующий рейс данными WebSocket; сохраняет поля, отсутствующие в payload.
  FlightDetail mergeFromRealtime(Map<String, dynamic> json) {
    final parsed = FlightDetail.fromJson(json);
    return copyWith(
      status: json.containsKey('status') ? parsed.status : status,
      actualDeparture: json.containsKey('actualDeparture') ? parsed.actualDeparture : actualDeparture,
      actualArrival: json.containsKey('actualArrival') ? parsed.actualArrival : actualArrival,
      flightNumber: parsed.flightNumber.isNotEmpty ? parsed.flightNumber : flightNumber,
      originAirport: parsed.originAirport.isNotEmpty ? parsed.originAirport : originAirport,
      destinationAirport: parsed.destinationAirport.isNotEmpty ? parsed.destinationAirport : destinationAirport,
      scheduledDeparture: parsed.scheduledDeparture.isNotEmpty ? parsed.scheduledDeparture : scheduledDeparture,
      scheduledArrival: parsed.scheduledArrival.isNotEmpty ? parsed.scheduledArrival : scheduledArrival,
      airlineName: parsed.airlineName ?? airlineName,
      aircraftIcao: parsed.aircraftIcao ?? aircraftIcao,
      currentGateAssignment: json.containsKey('currentGateAssignment')
          ? parsed.currentGateAssignment
          : currentGateAssignment,
      delayWarnings: parsed.delayWarnings.isNotEmpty ? parsed.delayWarnings : delayWarnings,
    );
  }
}
