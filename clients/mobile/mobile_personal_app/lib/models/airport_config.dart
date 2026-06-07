/// Публичная конфигурация аэропорта (GET /api/v1/airport).
class AirportConfig {
  final String homeIata;
  final String timezone;
  final int gatePlanWindowHours;
  final int gatePostGraceMinutes;

  const AirportConfig({
    required this.homeIata,
    required this.timezone,
    this.gatePlanWindowHours = 12,
    this.gatePostGraceMinutes = 15,
  });

  factory AirportConfig.fromJson(Map<String, dynamic> json) {
    return AirportConfig(
      homeIata: (json['homeIata'] as String).trim().toUpperCase(),
      timezone: json['timezone'] as String,
      gatePlanWindowHours: (json['gatePlanWindowHours'] as num?)?.toInt() ?? 12,
      gatePostGraceMinutes: (json['gatePostGraceMinutes'] as num?)?.toInt() ?? 15,
    );
  }
}
