import 'package:flutter/material.dart';
import '../core/di/app_scope.dart';
import '../core/errors/error_message.dart';
import '../models/flight_detail.dart';
import '../models/gate_assignment.dart';
import '../models/realtime_event.dart';
import '../widgets/flight_list_tile.dart';
import '../widgets/realtime_event_chip_bar.dart';
import '../widgets/theme_toggle_button.dart';
import 'flight_detail_screen.dart';

const _maxChipEvents = 20;

/// Главный экран: список рейсов + лента realtime-событий.
class FlightLookupScreen extends StatefulWidget {
  final VoidCallback onLogout;
  final bool isDarkMode;
  final VoidCallback onToggleTheme;

  const FlightLookupScreen({
    super.key,
    required this.onLogout,
    required this.isDarkMode,
    required this.onToggleTheme,
  });

  @override
  State<FlightLookupScreen> createState() => _FlightLookupScreenState();
}

class _FlightLookupScreenState extends State<FlightLookupScreen> {
  List<FlightDetail> _flights = [];
  bool _loading = false;
  String? _error;
  final List<RealtimeEvent> _events = [];
  bool _listenerAttached = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_listenerAttached) {
      _listenerAttached = true;
      AppScope.of(context).stomp.addListener(_onEvent);
      _loadFlights();
    }
  }

  @override
  void dispose() {
    if (_listenerAttached) {
      AppScope.of(context).stomp.removeListener(_onEvent);
    }
    super.dispose();
  }

  void _onEvent(RealtimeEvent e) {
    if (!mounted) return;

    if (e.type == EventType.delay) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('${e.title}: ${e.subtitle}')),
      );
      if (e.payload != null) {
        _patchFlight(e.payload!);
      } else {
        _setStatusDelayed(e.flightId);
      }
    } else if (e.type == EventType.gateChange && e.payload != null) {
      final assignment = e.payload!['assignment'] as Map<String, dynamic>?;
      if (assignment != null) {
        _patchGate(e.flightId, assignment);
      }
    } else if (e.payload != null) {
      _patchFlight(e.payload!);
    }

    setState(() {
      _events.insert(0, e);
      if (_events.length > _maxChipEvents) {
        _events.removeRange(_maxChipEvents, _events.length);
      }
    });
  }

  void _patchFlight(Map<String, dynamic> json) {
    final id = _parseFlightId(json['flightId']);
    if (id == null) return;
    final idx = _flights.indexWhere((f) => f.flightId == id);
    if (idx < 0) return;
    setState(() => _flights[idx] = _flights[idx].mergeFromRealtime(json));
  }

  void _patchGate(int flightId, Map<String, dynamic> assignment) {
    final idx = _flights.indexWhere((f) => f.flightId == flightId);
    if (idx < 0) return;
    setState(() {
      _flights[idx] = _flights[idx].copyWith(
        currentGateAssignment: GateAssignment.fromJson(assignment),
      );
    });
  }

  void _setStatusDelayed(int flightId) {
    final idx = _flights.indexWhere((f) => f.flightId == flightId);
    if (idx < 0) return;
    setState(() => _flights[idx] = _flights[idx].copyWith(status: 'DELAYED'));
  }

  int? _parseFlightId(Object? value) {
    if (value is int) return value;
    if (value is num) return value.toInt();
    return int.tryParse('$value');
  }

  Future<void> _loadFlights() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final list = await AppScope.of(context).api.getFlights();
      if (mounted) setState(() => _flights = list);
    } catch (e) {
      if (mounted) setState(() => _error = userErrorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _openDetail(FlightDetail f) {
    Navigator.of(context).push(MaterialPageRoute(
      builder: (_) => FlightDetailScreen(flightId: f.flightId),
    ));
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Рейсы'),
        actions: [
          ThemeToggleButton(isDarkMode: widget.isDarkMode, onToggle: widget.onToggleTheme),
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
          RealtimeEventChipBar(events: _events),
          if (_events.isNotEmpty) const Divider(height: 1),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                    ? Center(child: Text(_error!, style: TextStyle(color: scheme.error)))
                    : _flights.isEmpty
                        ? const Center(child: Text('Рейсов нет'))
                        : RefreshIndicator(
                            onRefresh: _loadFlights,
                            child: ListView.builder(
                              padding: const EdgeInsets.all(16),
                              itemCount: _flights.length,
                              itemBuilder: (_, i) => FlightListTile(
                                flight: _flights[i],
                                onTap: () => _openDetail(_flights[i]),
                              ),
                            ),
                          ),
          ),
        ],
      ),
    );
  }
}
