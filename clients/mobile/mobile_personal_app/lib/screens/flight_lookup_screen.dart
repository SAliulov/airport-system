import 'package:flutter/material.dart';
import '../models/flight_detail.dart';
import '../models/realtime_event.dart';
import '../services/airport_api.dart';
import '../services/stomp_service.dart';
import 'flight_detail_screen.dart';

/// Главный экран: список рейсов + лента realtime-событий (задержки, гейты).
class FlightLookupScreen extends StatefulWidget {
  final AirportApi api;
  final StompService stomp;
  final VoidCallback onLogout;

  const FlightLookupScreen({
    super.key,
    required this.api,
    required this.stomp,
    required this.onLogout,
  });

  @override
  State<FlightLookupScreen> createState() => _FlightLookupScreenState();
}

class _FlightLookupScreenState extends State<FlightLookupScreen> {
  List<FlightDetail> _flights = [];
  bool _loading = false;
  String? _error;

  final List<RealtimeEvent> _events = [];

  @override
  void initState() {
    super.initState();
    widget.stomp.addListener(_onEvent);
    widget.stomp.connect();
    _loadFlights();
  }

  @override
  void dispose() {
    widget.stomp.removeListener(_onEvent);
    super.dispose();
  }

  void _onEvent(RealtimeEvent e) {
    if (!mounted) return;
    setState(() => _events.insert(0, e));
  }

  Future<void> _loadFlights() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final list = await widget.api.getFlights();
      if (mounted) setState(() => _flights = list);
    } catch (e) {
      if (mounted) setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _openDetail(FlightDetail f) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => FlightDetailScreen(api: widget.api, flightId: f.flightId),
    ));
  }

  Color _statusColor(String status) {
    return switch (status) {
      'SCHEDULED' => Colors.blue,
      'DEPARTED' => Colors.orange,
      'ARRIVED' => Colors.green,
      'DELAYED' => Colors.red,
      'CANCELLED' => Colors.grey,
      _ => Colors.black54,
    };
  }

  IconData _eventIcon(EventType t) {
    return switch (t) {
      EventType.delay => Icons.warning_amber_rounded,
      EventType.gateChange => Icons.door_sliding_outlined,
      EventType.flightUpdate => Icons.flight,
    };
  }

  Color _eventColor(EventType t) {
    return switch (t) {
      EventType.delay => Colors.red,
      EventType.gateChange => Colors.teal,
      EventType.flightUpdate => Colors.blue,
    };
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Рейсы'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Обновить',
            onPressed: _loadFlights,
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: 'Выйти',
            onPressed: widget.onLogout,
          ),
        ],
      ),
      body: Column(
        children: [
          // --- Лента realtime-событий ---
          if (_events.isNotEmpty)
            SizedBox(
              height: 70,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
                itemCount: _events.length,
                separatorBuilder: (context, index) => const SizedBox(width: 8),
                itemBuilder: (_, i) {
                  final e = _events[i];
                  return ActionChip(
                    avatar: Icon(_eventIcon(e.type), size: 18, color: _eventColor(e.type)),
                    label: Text(e.title, overflow: TextOverflow.ellipsis),
                    tooltip: e.subtitle,
                    onPressed: () {
                      Navigator.of(context).push(MaterialPageRoute(
                        builder: (_) =>
                            FlightDetailScreen(api: widget.api, flightId: e.flightId),
                      ));
                    },
                  );
                },
              ),
            ),

          if (_events.isNotEmpty) const Divider(height: 1),

          // --- Список рейсов ---
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? Center(child: Text(_error!, style: const TextStyle(color: Colors.red)))
                    : _flights.isEmpty
                        ? const Center(child: Text('Рейсов не найдено'))
                        : RefreshIndicator(
                            onRefresh: _loadFlights,
                            child: ListView.builder(
                              itemCount: _flights.length,
                              itemBuilder: (_, i) {
                                final f = _flights[i];
                                final depTime = f.scheduledDeparture.length >= 16
                                    ? f.scheduledDeparture.substring(0, 16)
                                    : f.scheduledDeparture;
                                return ListTile(
                                  leading: CircleAvatar(
                                    backgroundColor: _statusColor(f.status).withAlpha(30),
                                    child: Icon(Icons.flight, color: _statusColor(f.status)),
                                  ),
                                  title: Text('${f.flightNumber}  ${f.route}'),
                                  subtitle: Text('${f.status}  •  $depTime'),
                                  trailing: const Icon(Icons.chevron_right),
                                  onTap: () => _openDetail(f),
                                );
                              },
                            ),
                          ),
          ),
        ],
      ),
    );
  }
}
