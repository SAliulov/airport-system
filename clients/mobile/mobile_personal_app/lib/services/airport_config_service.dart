import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config.dart';
import '../models/airport_config.dart';

/// Загрузка GET /api/v1/airport (anon).
Future<AirportConfig> fetchAirportConfig() async {
  final uri = Uri.parse('${AppConfig.apiBase}/api/v1/airport');
  final resp = await http.get(uri);
  if (resp.statusCode != 200) {
    throw Exception('HTTP ${resp.statusCode}');
  }
  return AirportConfig.fromJson(
    jsonDecode(resp.body) as Map<String, dynamic>,
  );
}
