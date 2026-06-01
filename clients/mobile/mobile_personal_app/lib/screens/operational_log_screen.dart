import 'package:flutter/material.dart';
import '../models/operational_event.dart';
import '../services/airport_api.dart';
import '../services/stomp_service.dart';
import '../widgets/operational_event_style.dart';
import 'flight_detail_screen.dart';

/// Журнал диспетчерских действий (сессия, append-only, локальный поиск).
class OperationalLogScreen extends StatefulWidget {
  final AirportApi api;
  final StompService stomp;

  const OperationalLogScreen({
    super.key,
    required this.api,
    required this.stomp,
  });

  @override
  State<OperationalLogScreen> createState() => _OperationalLogScreenState();
}

class _OperationalLogScreenState extends State<OperationalLogScreen> {
  final List<OperationalEvent> _sessionLogs = [];
  final _searchController = TextEditingController();
  String _query = '';
  List<OperationalEvent> _filteredLogs = const [];

  @override
  void initState() {
    super.initState();
    widget.stomp.addOperationalListener(_onOperationalEvent);
    widget.stomp.subscribeOperationalEvents();
    _searchController.addListener(_onSearchChanged);
  }

  @override
  void dispose() {
    widget.stomp.removeOperationalListener(_onOperationalEvent);
    _searchController.removeListener(_onSearchChanged);
    _searchController.dispose();
    super.dispose();
  }

  void _onSearchChanged() {
    final nextQuery = _searchController.text.trim().toLowerCase();
    if (nextQuery == _query) return;
    setState(() {
      _query = nextQuery;
      _recomputeFilteredLogs();
    });
  }

  void _recomputeFilteredLogs() {
    if (_query.isEmpty) {
      _filteredLogs = List<OperationalEvent>.unmodifiable(_sessionLogs);
      return;
    }
    _filteredLogs = _sessionLogs
        .where(
          (e) =>
              e.message.toLowerCase().contains(_query) ||
              e.user.toLowerCase().contains(_query),
        )
        .toList(growable: false);
  }

  void _onOperationalEvent(OperationalEvent event) {
    if (!mounted) return;
    setState(() {
      _sessionLogs.insert(0, event);
      _recomputeFilteredLogs();
    });
  }

  Future<void> _confirmClear() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Очистить журнал'),
        content: const Text('Очистить журнал операций?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Отмена')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Очистить')),
        ],
      ),
    );
    if (ok == true && mounted) {
      setState(() {
        _sessionLogs.clear();
        _recomputeFilteredLogs();
      });
    }
  }

  String _formatTime(OperationalEvent e) {
    final ts = e.timestamp;
    if (ts.length >= 16) return ts.substring(11, 16);
    return ts;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final logs = _filteredLogs;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Журнал операций'),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_sweep_outlined),
            tooltip: 'Очистить логи',
            onPressed: _sessionLogs.isEmpty ? null : _confirmClear,
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                prefixIcon: const Icon(Icons.search),
                hintText: 'Поиск по событию или диспетчеру…',
                border: const OutlineInputBorder(),
                isDense: true,
                filled: true,
                fillColor: colorScheme.surfaceContainerHighest,
              ),
            ),
          ),
          Expanded(
            child: logs.isEmpty
                ? Center(
                    child: Text(
                      _sessionLogs.isEmpty
                          ? 'Ожидание действий диспетчера…'
                          : 'Ничего не найдено',
                      style: TextStyle(color: colorScheme.onSurfaceVariant),
                    ),
                  )
                : ListView.separated(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                    itemCount: logs.length,
                    separatorBuilder: (_, index) => const SizedBox(height: 12),
                    itemBuilder: (_, i) => _OperationalLogTile(
                      event: logs[i],
                      api: widget.api,
                      formatTime: _formatTime,
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}

class _OperationalLogTile extends StatelessWidget {
  final OperationalEvent event;
  final AirportApi api;
  final String Function(OperationalEvent) formatTime;

  const _OperationalLogTile({
    required this.event,
    required this.api,
    required this.formatTime,
  });

  @override
  Widget build(BuildContext context) {
    final accent = OperationalEventStyle.accentColor(context, event.category);
    final scheme = Theme.of(context).colorScheme;

    return Card(
      elevation: 0,
      color: OperationalEventStyle.cardColor(context),
      shape: OperationalEventStyle.cardShape(context),
      clipBehavior: Clip.antiAlias,
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: accent.withValues(alpha: 0.15),
          child: Icon(
            OperationalEventStyle.iconFor(event.category),
            color: accent,
            size: 20,
          ),
        ),
        title: Text(event.message),
        subtitle: Text(
          '${event.user} • ${formatTime(event)}',
          style: TextStyle(color: scheme.onSurfaceVariant),
        ),
        isThreeLine: event.details != null && event.details!.isNotEmpty,
        trailing: event.flightId != null ? const Icon(Icons.chevron_right) : null,
        onTap: event.flightId != null
            ? () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => FlightDetailScreen(
                      api: api,
                      flightId: event.flightId!,
                    ),
                  ),
                );
              }
            : null,
      ),
    );
  }
}
