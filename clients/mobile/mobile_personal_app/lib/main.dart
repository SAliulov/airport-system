import 'package:flutter/material.dart';
import 'config.dart';
import 'core/di/app_scope.dart';
import 'core/theme/app_theme.dart';
import 'core/theme/theme_preferences.dart';
import 'models/airport_config.dart';
import 'services/auth_service.dart';
import 'services/airport_api.dart';
import 'services/stomp_service.dart';
import 'screens/login_screen.dart';
import 'screens/home_shell.dart';
import 'widgets/airport_config_gate.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    AirportConfigGate(
      builder: (airportConfig) => AirportApp(airportConfig: airportConfig),
    ),
  );
}

class AirportApp extends StatefulWidget {
  final AirportConfig airportConfig;

  const AirportApp({super.key, required this.airportConfig});

  @override
  State<AirportApp> createState() => _AirportAppState();
}

class _AirportAppState extends State<AirportApp> {
  final _scope = AppServicesHolder();

  @override
  void initState() {
    super.initState();
    AppConfig.ensureLoaded();
    ThemePreferences.load().then((pref) {
      if (!mounted) return;
      setState(() {
        _scope.themeMode = pref == AppThemePreference.dark
            ? ThemeMode.dark
            : ThemeMode.light;
      });
    });
  }

  @override
  void dispose() {
    _scope.stomp.disconnect();
    super.dispose();
  }

  void _onLoginSuccess() => setState(() {});

  Future<void> _onLogout() async {
    _scope.stomp.disconnect();
    await _scope.auth.logout();
    if (mounted) setState(() {});
  }

  Future<void> _toggleTheme() async {
    final next = _scope.themeMode == ThemeMode.dark
        ? ThemeMode.light
        : ThemeMode.dark;
    setState(() => _scope.themeMode = next);
    await ThemePreferences.save(
      next == ThemeMode.dark
          ? AppThemePreference.dark
          : AppThemePreference.light,
    );
  }

  @override
  Widget build(BuildContext context) {
    return AppScope(
      auth: _scope.auth,
      api: _scope.api,
      stomp: _scope.stomp,
      airportConfig: widget.airportConfig,
      child: MaterialApp(
        title: 'АСУРР',
        debugShowCheckedModeBanner: false,
        theme: buildAppTheme(Brightness.light),
        darkTheme: buildAppTheme(Brightness.dark),
        themeMode: _scope.themeMode,
        home: _scope.auth.isLoggedIn
            ? HomeShell(
                onLogout: _onLogout,
                isDarkMode: _scope.themeMode == ThemeMode.dark,
                onToggleTheme: _toggleTheme,
              )
            : LoginScreen(
                onLoginSuccess: _onLoginSuccess,
                isDarkMode: _scope.themeMode == ThemeMode.dark,
                onToggleTheme: _toggleTheme,
              ),
      ),
    );
  }
}

/// Holds service singletons for [AppScope].
class AppServicesHolder {
  final auth = AuthService();
  late final api = AirportApi(auth);
  final stomp = StompService();
  ThemeMode themeMode = ThemeMode.light;
}
