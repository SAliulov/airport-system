import 'package:flutter/material.dart';
import '../models/airport_config.dart';
import '../services/airport_config_service.dart';

/// Блокирует приложение до загрузки конфигурации аэропорта.
class AirportConfigGate extends StatefulWidget {
  final Widget Function(AirportConfig config) builder;

  const AirportConfigGate({super.key, required this.builder});

  @override
  State<AirportConfigGate> createState() => _AirportConfigGateState();
}

class _AirportConfigGateState extends State<AirportConfigGate> {
  late Future<AirportConfig> _future;

  @override
  void initState() {
    super.initState();
    _future = fetchAirportConfig();
  }

  void _retry() {
    setState(() {
      _future = fetchAirportConfig();
    });
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<AirportConfig>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const MaterialApp(
            home: Scaffold(
              body: Center(child: CircularProgressIndicator()),
            ),
          );
        }
        if (snapshot.hasError || !snapshot.hasData) {
          return MaterialApp(
            home: Scaffold(
              body: Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Text('Не удалось загрузить конфигурацию аэропорта'),
                      if (snapshot.hasError)
                        Padding(
                          padding: const EdgeInsets.only(top: 8),
                          child: Text('${snapshot.error}'),
                        ),
                      const SizedBox(height: 16),
                      FilledButton(
                        onPressed: _retry,
                        child: const Text('Повторить'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        }
        return widget.builder(snapshot.data!);
      },
    );
  }
}
