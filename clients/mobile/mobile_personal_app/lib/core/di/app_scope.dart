import 'package:flutter/material.dart';
import '../../models/airport_config.dart';
import '../../services/airport_api.dart';
import '../../services/auth_service.dart';
import '../../services/stomp_service.dart';

/// DI-контейнер приложения (InheritedWidget).
class AppScope extends InheritedWidget {
  final AuthService auth;
  final AirportApi api;
  final StompService stomp;
  final AirportConfig airportConfig;

  const AppScope({
    super.key,
    required this.auth,
    required this.api,
    required this.stomp,
    required this.airportConfig,
    required super.child,
  });

  static AppScope of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<AppScope>();
    assert(scope != null, 'AppScope not found');
    return scope!;
  }

  @override
  bool updateShouldNotify(AppScope oldWidget) =>
      auth != oldWidget.auth
      || api != oldWidget.api
      || stomp != oldWidget.stomp
      || airportConfig != oldWidget.airportConfig;
}
