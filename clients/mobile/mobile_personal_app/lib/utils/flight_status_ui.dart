import 'package:flutter/material.dart';

/// Русские подписи и цвета статусов рейса (MD3 colorScheme).
class FlightStatusUi {
  FlightStatusUi._();

  static String label(String status) {
    switch (status) {
      case 'SCHEDULED':
        return 'По расписанию';
      case 'DEPARTED':
        return 'Вылетел';
      case 'ARRIVED':
        return 'Прибыл';
      case 'DELAYED':
        return 'Задержан';
      case 'CANCELLED':
        return 'Отменён';
      default:
        return status;
    }
  }

  static Color colorFor(ColorScheme scheme, String status) {
    switch (status) {
      case 'SCHEDULED':
        return scheme.primary;
      case 'DEPARTED':
        return scheme.tertiary;
      case 'ARRIVED':
        return scheme.secondary;
      case 'DELAYED':
        return scheme.error;
      case 'CANCELLED':
        return scheme.outline;
      default:
        return scheme.onSurface;
    }
  }
}
