import 'package:flutter/material.dart';
import 'services/auth_service.dart';
import 'services/airport_api.dart';
import 'services/stomp_service.dart';
import 'screens/login_screen.dart';
import 'screens/flight_lookup_screen.dart';

void main() {
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
      title: 'Airport Ground Staff',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorSchemeSeed: Colors.indigo,
        useMaterial3: true,
        brightness: Brightness.light,
      ),
      home: _auth.isLoggedIn
          ? FlightLookupScreen(
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
