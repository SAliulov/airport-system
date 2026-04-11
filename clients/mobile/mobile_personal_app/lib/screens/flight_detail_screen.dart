import 'package:flutter/material.dart';
import '../models/flight_detail.dart';
import '../services/airport_api.dart';

/// Экран детализации рейса: статус, маршрут, гейт, список задержек.
class FlightDetailScreen extends StatefulWidget {
  final AirportApi api;
  final int flightId;

  const FlightDetailScreen({
    super.key,
    required this.api,
    required this.flightId,
  });

  @override
  State<FlightDetailScreen> createState() => _FlightDetailScreenState();
}

class _FlightDetailScreenState extends State<FlightDetailScreen> {
  FlightDetail? _flight;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final f = await widget.api.getFlightById(widget.flightId);
      if (mounted) setState(() => _flight = f);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Color _statusColor(String s) {
    return switch (s) {
      'SCHEDULED' => Colors.blue,
      'DEPARTED' => Colors.orange,
      'ARRIVED' => Colors.green,
      'DELAYED' => Colors.red,
      'CANCELLED' => Colors.grey,
      _ => Colors.black54,
    };
  }

  String _statusLabel(String s) {
    return switch (s) {
      'SCHEDULED' => 'По расписанию',
      'DEPARTED' => 'Вылетел',
      'ARRIVED' => 'Прибыл',
      'DELAYED' => 'Задержан',
      'CANCELLED' => 'Отменён',
      _ => s,
    };
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
              ? Center(child: Text(_error!, style: const TextStyle(color: Colors.red)))
              : _buildBody(context, _flight!),
    );
  }

  Widget _buildBody(BuildContext ctx, FlightDetail f) {
    final theme = Theme.of(ctx);
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // --- Статус ---
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Icon(Icons.circle, color: _statusColor(f.status), size: 14),
                  const SizedBox(width: 8),
                  Text(
                    _statusLabel(f.status),
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: _statusColor(f.status),
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 12),

          // --- Маршрут ---
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Маршрут', style: theme.textTheme.labelLarge),
                  const SizedBox(height: 8),
                  _row('Рейс', f.flightNumber),
                  _row('Маршрут', f.route),
                  _row('Вылет (план)', _fmt(f.scheduledDeparture)),
                  _row('Прибытие (план)', _fmt(f.scheduledArrival)),
                  if (f.actualDeparture != null) _row('Вылет (факт)', _fmt(f.actualDeparture!)),
                  if (f.actualArrival != null) _row('Прибытие (факт)', _fmt(f.actualArrival!)),
                  if (f.airlineName != null) _row('Авиакомпания', f.airlineName!),
                  if (f.aircraftIcao != null) _row('Тип ВС', f.aircraftIcao!),
                  _row('Гейт', f.gateLabel),
                ],
              ),
            ),
          ),

          const SizedBox(height: 12),

          // --- Задержки ---
          if (f.delayWarnings.isNotEmpty) ...[
            Text('Задержки', style: theme.textTheme.titleSmall),
            const SizedBox(height: 8),
            ...f.delayWarnings.map((w) => Card(
                  color: Colors.red.shade50,
                  child: ListTile(
                    leading: const Icon(Icons.warning_amber, color: Colors.red),
                    title: Text('${w.delayMinutes} мин'),
                    subtitle: Text(w.reason ?? 'Причина не указана'),
                    trailing: w.createdAt != null
                        ? Text(_fmt(w.createdAt!), style: theme.textTheme.bodySmall)
                        : null,
                  ),
                )),
          ] else
            const Center(
              child: Padding(
                padding: EdgeInsets.only(top: 24),
                child: Text('Задержек нет', style: TextStyle(color: Colors.grey)),
              ),
            ),
        ],
      ),
    );
  }

  Widget _row(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        children: [
          SizedBox(
            width: 140,
            child: Text(label, style: const TextStyle(color: Colors.grey)),
          ),
          Expanded(child: Text(value)),
        ],
      ),
    );
  }

  String _fmt(String iso) {
    try {
      final dt = DateTime.parse(iso);
      return '${dt.day.toString().padLeft(2, '0')}.'
          '${dt.month.toString().padLeft(2, '0')}.'
          '${dt.year} '
          '${dt.hour.toString().padLeft(2, '0')}:'
          '${dt.minute.toString().padLeft(2, '0')}';
    } catch (_) {
      return iso;
    }
  }
}
