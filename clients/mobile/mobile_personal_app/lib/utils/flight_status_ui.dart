import 'package:flutter/material.dart';

/// Русские подписи, цвета и иконки статусов рейса (высококонтрастные MD3).
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

  static IconData iconFor(String status) {
    switch (status) {
      case 'SCHEDULED':
        return Icons.schedule;
      case 'DEPARTED':
        return Icons.flight_takeoff;
      case 'ARRIVED':
        return Icons.flight_land;
      case 'DELAYED':
        return Icons.warning_rounded;
      case 'CANCELLED':
        return Icons.cancel_rounded;
      default:
        return Icons.help_outline;
    }
  }

  static Color colorFor(ColorScheme scheme, String status) {
    switch (status) {
      case 'SCHEDULED':
        return const Color(0xFF1565C0);
      case 'DEPARTED':
        return const Color(0xFF2E7D32);
      case 'ARRIVED':
        return const Color(0xFF1B5E20);
      case 'DELAYED':
        return const Color(0xFFE65100);
      case 'CANCELLED':
        return const Color(0xFFC62828);
      default:
        return scheme.onSurface;
    }
  }
}
