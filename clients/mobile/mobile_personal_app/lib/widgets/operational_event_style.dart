import 'package:flutter/material.dart';
import '../models/operational_event.dart';

/// MD3-семантика для категорий операционной ленты.
class OperationalEventStyle {
  OperationalEventStyle._();

  static IconData iconFor(OperationalEventCategory? category) {
    return switch (category) {
      OperationalEventCategory.gate => Icons.door_sliding_outlined,
      OperationalEventCategory.status => Icons.sync_alt,
      OperationalEventCategory.aircraft => Icons.airplanemode_active,
      OperationalEventCategory.delay => Icons.warning_amber_rounded,
      OperationalEventCategory.schedule => Icons.calendar_month_outlined,
      OperationalEventCategory.flight => Icons.flight_takeoff,
      null => Icons.info_outline,
    };
  }

  static Color accentColor(BuildContext context, OperationalEventCategory? category) {
    final scheme = Theme.of(context).colorScheme;
    return switch (category) {
      OperationalEventCategory.gate => scheme.tertiary,
      OperationalEventCategory.status => scheme.primary,
      OperationalEventCategory.aircraft => scheme.secondary,
      OperationalEventCategory.delay => scheme.error,
      OperationalEventCategory.schedule => scheme.outline,
      OperationalEventCategory.flight => scheme.primary,
      null => scheme.onSurfaceVariant,
    };
  }

  static Color cardColor(BuildContext context) {
    return Theme.of(context).colorScheme.surfaceContainerLow;
  }

  static ShapeBorder cardShape(BuildContext context) {
    return RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(12),
    );
  }
}
