import '../models/flight_detail.dart';

enum FlightDirection { inbound, outbound }

/// Определяет направление рейса относительно домашнего аэропорта.
FlightDirection? resolveFlightDirection({
  required String homeIata,
  required String originAirport,
  required String destinationAirport,
}) {
  final home = homeIata.trim().toUpperCase();
  final origin = originAirport.trim().toUpperCase();
  final destination = destinationAirport.trim().toUpperCase();
  if (origin == home && destination != home) return FlightDirection.outbound;
  if (destination == home && origin != home) return FlightDirection.inbound;
  return null;
}

String directionLabel(FlightDirection? direction) {
  return switch (direction) {
    FlightDirection.outbound => 'Вылет',
    FlightDirection.inbound => 'Прилёт',
    null => '—',
  };
}

/// Основное плановое время для списка: вылет для outbound, прилёт для inbound.
String primaryScheduledIso(FlightDetail flight, FlightDirection? direction) {
  return switch (direction) {
    FlightDirection.outbound => flight.scheduledDeparture,
    FlightDirection.inbound => flight.scheduledArrival,
    null => flight.scheduledDeparture,
  };
}
