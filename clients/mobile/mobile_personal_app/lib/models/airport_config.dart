/// Публичная конфигурация аэропорта (GET /api/v1/airport).
class AirportConfig {
  final String homeIata;
  final String timezone;
  final int gatePlanWindowHours;
  final int gatePostGraceMinutes;

  const AirportConfig({
    required this.homeIata,
    required this.timezone,
    required this.gatePlanWindowHours,
    required this.gatePostGraceMinutes,
  });
}
