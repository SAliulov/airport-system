import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config.dart';
import '../models/flight_detail.dart';
import '../models/operational_event.dart';
import 'auth_service.dart';

/// REST-клиент к /api/v1.
class AirportApi {
  final AuthService auth;

  AirportApi(this.auth);

  /// Список рейсов (анонимный GET).
  Future<List<FlightDetail>> getFlights() async {
    final uri = Uri.parse('${AppConfig.apiBase}/api/v1/flights?size=100');

    final resp = await http.get(uri, headers: auth.authHeaders);
    if (resp.statusCode != 200) throw ApiException('HTTP ${resp.statusCode}');

    final decoded = jsonDecode(resp.body);
    final List<dynamic> list;
    if (decoded is Map<String, dynamic> && decoded['content'] is List) {
      list = decoded['content'] as List<dynamic>;
    } else if (decoded is List) {
      list = decoded;
    } else {
      throw ApiException('Unexpected flights response');
    }
    return list
        .map((e) => FlightDetail.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Один рейс с деталями.
  Future<FlightDetail> getFlightById(int flightId) async {
    final uri =
        Uri.parse('${AppConfig.apiBase}/api/v1/flights/$flightId');
    final resp = await http.get(uri, headers: auth.authHeaders);
    if (resp.statusCode == 404) throw ApiException('Рейс не найден');
    if (resp.statusCode != 200) throw ApiException('HTTP ${resp.statusCode}');

    return FlightDetail.fromJson(
      jsonDecode(resp.body) as Map<String, dynamic>,
    );
  }

  /// История операционных событий (журнал диспетчера) — первичная загрузка/refresh.
  Future<List<OperationalEvent>> getOperationalEvents({
    int page = 0,
    int size = 50,
  }) async {
    final uri = Uri.parse(
      '${AppConfig.apiBase}/api/v1/operational-events?page=$page&size=$size',
    );
    final resp = await http.get(uri, headers: auth.authHeaders);
    if (resp.statusCode != 200) throw ApiException('HTTP ${resp.statusCode}');

    final decoded = jsonDecode(resp.body) as Map<String, dynamic>;
    final list = decoded['content'] as List<dynamic>;
    return list
        .map((e) => OperationalEvent.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}

class ApiException implements Exception {
  final String message;
  ApiException(this.message);

  @override
  String toString() => message;
}
