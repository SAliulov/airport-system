import 'package:flutter/material.dart';
import '../core/di/app_scope.dart';
import 'flight_lookup_screen.dart';
import 'operational_log_screen.dart';

/// Оболочка с нижней навигацией: рейсы и журнал операций.
class HomeShell extends StatefulWidget {
  final VoidCallback onLogout;
  final bool isDarkMode;
  final VoidCallback onToggleTheme;

  const HomeShell({
    super.key,
    required this.onLogout,
    required this.isDarkMode,
    required this.onToggleTheme,
  });

  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;
  bool _stompStarted = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (!_stompStarted) {
      _stompStarted = true;
      final stomp = AppScope.of(context).stomp;
      stomp.connect();
      stomp.subscribeOperationalEvents();
    }
  }

  void _onTabSelected(int i) => setState(() => _index = i);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _index,
        children: [
          FlightLookupScreen(
            onLogout: widget.onLogout,
            isDarkMode: widget.isDarkMode,
            onToggleTheme: widget.onToggleTheme,
          ),
          const OperationalLogScreen(),
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
