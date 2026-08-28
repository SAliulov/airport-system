import 'package:flutter/material.dart';
import '../models/delay_warning.dart';
import '../models/flight_detail.dart';
import '../models/gate_assignment.dart';
import '../models/realtime_event.dart';
import '../core/di/app_scope.dart';
import '../core/errors/error_message.dart';
import '../utils/airport_time.dart';
import '../utils/flight_direction.dart';
import '../utils/flight_status_ui.dart';

/// Экран детализации рейса: статус, маршрут, гейт, список задержек.
class FlightDetailScreen extends StatefulWidget {
  final int flightId;

  const FlightDetailScreen({
    super.key,
    required this.flightId,
  });

  @override
  State<FlightDetailScreen> createState() => _FlightDetailScreenState();
}

class _FlightDetailScreenState extends State<FlightDetailScreen> {
  FlightDetail? _flight;
  bool _loading = true;
  String? _error;
  bool _listenerAttached = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_listenerAttached) {
      _listenerAttached = true;
      AppScope.of(context).stomp.addListener(_onRealtime);
      _load();
    }
  }

  @override
  void dispose() {
    if (_listenerAttached) {
      AppScope.of(context).stomp.removeListener(_onRealtime);
    }
    super.dispose();
  }

  void _onRealtime(RealtimeEvent e) {
    if (e.flightId != widget.flightId || e.payload == null || !mounted) return;

    // Handle DELETED — navigate back
    if (e.payload!['_eventType'] == 'DELETED') {
      Navigator.of(context).maybePop();
      return;
    }

    setState(() {
      if (e.type == EventType.gateChange) {
        final assignment = e.payload!['assignment'] as Map<String, dynamic>?;
        if (assignment != null && _flight != null) {
          _flight = _flight!.copyWith(
            currentGateAssignment: GateAssignment.fromJson(assignment),
          );
          return;
        }
      }
      if (e.type == EventType.delay) {
        final warningJson = e.payload!['warning'] as Map<String, dynamic>?;
        if (warningJson != null && _flight != null) {
          final warning = DelayWarning.fromJson(warningJson);
          final eventType = e.payload!['eventType'] as String?;
          final updated = List<DelayWarning>.from(_flight!.delayWarnings)
            ..removeWhere((w) => w.warningId == warning.warningId);
          if (eventType != 'DELETED') updated.add(warning);
          _flight = _flight!.copyWith(delayWarnings: updated);
          return;
        }
      }
      if (_flight != null) {
        _flight = _flight!.mergeFromRealtime(e.payload!);
      } else {
        _flight = FlightDetail.fromJson(e.payload!);
      }
    });
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final f = await AppScope.of(context).api.getFlightById(widget.flightId);
      if (mounted) setState(() => _flight = f);
    } catch (e) {
      if (mounted) setState(() => _error = userErrorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_flight != null ? _flight!.flightNumber : 'Рейс #${widget.flightId}'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _load),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)))
              : _buildBody(context, _flight!),
    );
  }

  Widget _buildBody(BuildContext ctx, FlightDetail f) {
    final theme = Theme.of(ctx);
    final config = AppScope.of(ctx).airportConfig;
    final tz = config.timezone;
    final direction = resolveFlightDirection(
      homeIata: config.homeIata,
      originAirport: f.originAirport,
      destinationAirport: f.destinationAirport,
    );
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Icon(Icons.circle, color: FlightStatusUi.colorFor(theme.colorScheme, f.status), size: 14),
                  const SizedBox(width: 8),
                  Text(
                    FlightStatusUi.label(f.status),
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: FlightStatusUi.colorFor(theme.colorScheme, f.status),
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const Spacer(),
                  Text(
                    '#${f.flightId}',
                    style: theme.textTheme.titleSmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Маршрут', style: theme.textTheme.labelLarge),
                  const SizedBox(height: 8),
                  _row(ctx, 'Рейс', f.flightNumber),
                  _row(ctx, 'Маршрут', f.route),
                  _row(ctx, 'Направление', directionLabel(direction)),
                  _row(ctx, 'Вылет (план)', AirportTime.formatDateTime(f.scheduledDeparture, timezone: tz, withYear: true)),
                  _row(ctx, 'Прибытие (план)', AirportTime.formatDateTime(f.scheduledArrival, timezone: tz, withYear: true)),
                  if (f.actualDeparture != null) _row(ctx, 'Вылет (факт)', AirportTime.formatDateTime(f.actualDeparture, timezone: tz, withYear: true)),
                  if (f.actualArrival != null) _row(ctx, 'Прибытие (факт)', AirportTime.formatDateTime(f.actualArrival, timezone: tz, withYear: true)),
                  if (f.airlineName != null) _row(ctx, 'Авиакомпания', f.airlineName!),
                  if (f.aircraftIcao != null) _row(ctx, 'Тип ВС', f.aircraftIcao!),
                  _row(ctx, 'Гейт', f.gateLabel),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          if (f.delayWarnings.isNotEmpty) ...[
            Text('Задержки', style: theme.textTheme.titleSmall),
            const SizedBox(height: 8),
            ...f.delayWarnings.map((w) => Card(
                  color: theme.colorScheme.errorContainer,
                  child: ListTile(
                    leading: Icon(Icons.warning_amber, color: theme.colorScheme.onErrorContainer),
                    title: Text('${w.delayMinutes} мин'),
                    subtitle: Text(w.reason ?? 'Причина не указана'),
                    trailing: w.createdAt != null
                        ? Text(AirportTime.formatDateTime(w.createdAt, timezone: tz, withYear: true), style: theme.textTheme.bodySmall)
                        : null,
                  ),
                )),
          ] else
            Center(
              child: Padding(
                padding: const EdgeInsets.only(top: 24),
                child: Text('Задержек нет', style: TextStyle(color: theme.colorScheme.onSurfaceVariant)),
              ),
            ),
        ],
      ),
    );
  }

  Widget _row(BuildContext ctx, String label, String value) {
    final muted = Theme.of(ctx).colorScheme.onSurfaceVariant;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          SizedBox(
            width: 140,
            child: Text(label, style: TextStyle(color: muted)),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }
}
