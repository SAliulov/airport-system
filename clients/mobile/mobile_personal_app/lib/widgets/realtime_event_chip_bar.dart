import 'package:flutter/material.dart';
import '../models/realtime_event.dart';

/// Горизонтальная лента последних realtime-событий.
class RealtimeEventChipBar extends StatelessWidget {
  final List<RealtimeEvent> events;
  final void Function(RealtimeEvent event)? onChipTap;

  const RealtimeEventChipBar({super.key, required this.events, this.onChipTap});

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
          return GestureDetector(
            onTap: onChipTap != null ? () => onChipTap!(e) : null,
            child: Chip(
              avatar: Icon(_iconFor(e.type), size: 16, color: _iconColor(scheme, e.type)),
              label: Text(e.title, style: const TextStyle(fontSize: 12)),
              visualDensity: VisualDensity.compact,
            ),
          );
        },
      ),
    );
  }

  Color _iconColor(ColorScheme scheme, EventType type) {
    switch (type) {
      case EventType.delay:
        return scheme.error;
      case EventType.gateChange:
        return scheme.tertiary;
      case EventType.flightUpdate:
        return scheme.primary;
    }
  }

  IconData _iconFor(EventType type) {
    switch (type) {
      case EventType.delay:
        return Icons.warning_amber_rounded;
      case EventType.gateChange:
        return Icons.door_sliding;
      case EventType.flightUpdate:
        return Icons.flight_takeoff;
    }
  }
}
