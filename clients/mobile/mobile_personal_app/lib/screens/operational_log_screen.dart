import 'package:flutter/material.dart';
import '../core/di/app_scope.dart';
import '../models/operational_event.dart';
import '../widgets/operational_event_style.dart';
import 'flight_detail_screen.dart';

/// Журнал диспетчерских действий (сессия, append-only, локальный поиск).
class OperationalLogScreen extends StatefulWidget {
  const OperationalLogScreen({super.key});

  @override
  State<OperationalLogScreen> createState() => _OperationalLogScreenState();
}

class _OperationalLogScreenState extends State<OperationalLogScreen> {
  final List<OperationalEvent> _sessionLogs = [];
  final _searchController = TextEditingController();
  String _query = '';
  List<OperationalEvent> _filteredLogs = const [];
  bool _listenerAttached = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_listenerAttached) {
      _listenerAttached = true;
      AppScope.of(context).stomp.addOperationalListener(_onOperationalEvent);
    }
  }

  @override
  void dispose() {
    if (_listenerAttached) {
      AppScope.of(context).stomp.removeOperationalListener(_onOperationalEvent);
    }
    _searchController.removeListener(_onSearchChanged);
    _searchController.dispose();
    super.dispose();
  }

  @override
  void initState() {
    super.initState();
    _searchController.addListener(_onSearchChanged);
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
      if (_sessionLogs.length > 200) {
        _sessionLogs.removeRange(200, _sessionLogs.length);
      }
      _recomputeFilteredLogs();
    });
  }

  Future<void> _confirmClear() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Очистить журнал?'),
        content: const Text('Записи текущей сессии будут удалены с устройства.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Отмена')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Очистить')),
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

  String _formatTime(OperationalEvent event) {
    final t = event.timestamp;
    if (t.length >= 16) return t.substring(11, 16);
    return t;
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final logs = _filteredLogs;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Журнал'),
        actions: [
          IconButton(icon: const Icon(Icons.delete_outline), onPressed: _confirmClear, tooltip: 'Очистить'),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Поиск по сообщению или пользователю',
                prefixIcon: const Icon(Icons.search),
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
  final String Function(OperationalEvent) formatTime;

  const _OperationalLogTile({
    required this.event,
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
                    builder: (_) => FlightDetailScreen(flightId: event.flightId!),
                  ),
                );
              }
            : null,
      ),
    );
  }
}
