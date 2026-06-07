import 'package:flutter/material.dart';
import '../core/di/app_scope.dart';
import '../models/flight_detail.dart';
import '../utils/airport_time.dart';
import '../utils/flight_direction.dart';
import '../utils/flight_status_ui.dart';

/// Строка списка рейсов на главном экране.
class FlightListTile extends StatelessWidget {
  final FlightDetail flight;
  final VoidCallback onTap;

  const FlightListTile({super.key, required this.flight, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final config = AppScope.of(context).airportConfig;
    final direction = resolveFlightDirection(
      homeIata: config.homeIata,
      originAirport: flight.originAirport,
      destinationAirport: flight.destinationAirport,
    );
    final scheduledIso = primaryScheduledIso(flight, direction);
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 4),
      child: ListTile(
        onTap: onTap,
        title: Text(flight.flightNumber, style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(
          '${flight.route}\n${directionLabel(direction)}: ${AirportTime.formatDateTime(scheduledIso, timezone: config.timezone)}',
        ),
        trailing: Chip(
          label: Text(
            FlightStatusUi.label(flight.status),
            style: TextStyle(color: scheme.onPrimaryContainer, fontSize: 12),
          ),
          backgroundColor: FlightStatusUi.colorFor(scheme, flight.status).withValues(alpha: 0.2),
          side: BorderSide(color: FlightStatusUi.colorFor(scheme, flight.status)),
        ),
      ),
    );
  }
}
