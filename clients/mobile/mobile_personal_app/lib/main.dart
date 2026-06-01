import 'package:flutter/material.dart';
import 'config.dart';
import 'core/theme/app_theme.dart';
import 'services/auth_service.dart';
import 'services/airport_api.dart';
import 'services/stomp_service.dart';
import 'screens/login_screen.dart';
import 'screens/home_shell.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AirportApp());
}

class AirportApp extends StatefulWidget {
  const AirportApp({super.key});

  @override
  State<AirportApp> createState() => _AirportAppState();
}

class _AirportAppState extends State<AirportApp> {
  final _auth = AuthService();
  late final _api = AirportApi(_auth);
  final _stomp = StompService();

  @override
  void initState() {
    super.initState();
    AppConfig.ensureLoaded();
  }

  @override
  void dispose() {
    _stomp.disconnect();
    super.dispose();
  }

  void _onLoginSuccess() => setState(() {});

  void _onLogout() async {
    _stomp.disconnect();
    await _auth.logout();
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'АСУРР',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      home: _auth.isLoggedIn
          ? HomeShell(
              api: _api,
              stomp: _stomp,
              onLogout: _onLogout,
            )
          : LoginScreen(
              auth: _auth,
              onLoginSuccess: _onLoginSuccess,
            ),
    );
  }
}
