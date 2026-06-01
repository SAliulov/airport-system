import 'package:flutter/material.dart';
import '../services/airport_api.dart';
import '../services/stomp_service.dart';
import 'flight_lookup_screen.dart';
import 'operational_log_screen.dart';

/// Оболочка с нижней навигацией: рейсы и журнал операций.
class HomeShell extends StatefulWidget {
  final AirportApi api;
  final StompService stomp;
  final VoidCallback onLogout;

  const HomeShell({
    super.key,
    required this.api,
    required this.stomp,
    required this.onLogout,
  });

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  @override
  void initState() {
    super.initState();
    widget.stomp.connect();
  }

  void _onTabSelected(int i) => setState(() => _index = i);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: [
          FlightLookupScreen(
            api: widget.api,
            stomp: widget.stomp,
            onLogout: widget.onLogout,
          ),
          OperationalLogScreen(
            api: widget.api,
            stomp: widget.stomp,
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: _onTabSelected,
        destinations: const [
          NavigationDestination(icon: Icon(Icons.flight), label: 'Рейсы'),
          NavigationDestination(icon: Icon(Icons.history), label: 'Журнал'),
        ],
      ),
    );
  }
}
