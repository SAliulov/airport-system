import 'package:flutter/material.dart';
import '../models/realtime_event.dart';

/// Горизонтальная лента последних realtime-событий.
class RealtimeEventChipBar extends StatelessWidget {
  final List<RealtimeEvent> events;

  const RealtimeEventChipBar({super.key, required this.events});

  @override
  Widget build(BuildContext context) {
    if (events.isEmpty) return const SizedBox.shrink();
    final scheme = Theme.of(context).colorScheme;
    return SizedBox(
      height: 36,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        itemCount: events.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (_, i) {
          final e = events[i];
          return Chip(
            avatar: Icon(_iconFor(e.type), size: 16, color: scheme.onSurfaceVariant),
            label: Text(e.title, style: const TextStyle(fontSize: 12)),
            visualDensity: VisualDensity.compact,
          );
        },
      ),
    );
  }

  IconData _iconFor(EventType type) {
    switch (type) {
      case EventType.delay:
        return Icons.warning_amber;
      case EventType.gateChange:
        return Icons.meeting_room;
      case EventType.flightUpdate:
        return Icons.flight;
    }
  }
}
