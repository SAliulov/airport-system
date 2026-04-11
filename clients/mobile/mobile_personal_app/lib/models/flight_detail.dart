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
      flightId: json['flightId'] as int? ?? 0,
      status: json['status']?.toString() ?? '',
      actualDeparture: json['actualDeparture']?.toString(),
      actualArrival: json['actualArrival']?.toString(),
      flightNumber: schedule['flightNumber']?.toString() ?? '',
      originAirport: (schedule['originAirport']?.toString() ?? '').trim(),
      destinationAirport:
          (schedule['destinationAirport']?.toString() ?? '').trim(),
      scheduledDeparture: schedule['scheduledDeparture']?.toString() ?? '',
      scheduledArrival: schedule['scheduledArrival']?.toString() ?? '',
      airlineName: airline?['name']?.toString(),
      aircraftIcao: aircraft?['icaoCode']?.toString(),
      currentGateAssignment:
          gateJson != null ? GateAssignment.fromJson(gateJson) : null,
      delayWarnings: warningsJson
          .map((e) => DelayWarning.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}
