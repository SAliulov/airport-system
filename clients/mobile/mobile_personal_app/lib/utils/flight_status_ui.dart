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
        return const Color(0xFF1565C0); // blue
      case 'DEPARTED':
        return const Color(0xFFFF8F00); // amber
      case 'ARRIVED':
        return const Color(0xFF1B5E20); // dark green
      case 'DELAYED':
        return const Color(0xFFD32F2F); // red
      case 'CANCELLED':
        return const Color(0xFF757575); // grey
      default:
        return scheme.onSurface;
    }
  }
}
