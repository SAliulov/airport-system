import 'dart:convert';
import 'package:http/http.dart' as http;
import '../config.dart';
import '../models/login_response.dart';

/// Хранит JWT-токен в памяти (тонкий клиент).
class AuthService {
  String? _accessToken;
  String? _role;

  String? get accessToken => _accessToken;
  String? get role => _role;
  bool get isLoggedIn => _accessToken != null;

  Map<String, String> get authHeaders => {
        if (_accessToken != null) 'Authorization': 'Bearer $_accessToken',
        'Content-Type': 'application/json',
      };

  Future<LoginResponse> login(String username, String password) async {
    final uri = Uri.parse('${AppConfig.apiBase}/api/v1/auth/login');
    final response = await http.post(
      uri,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'username': username,
        'password': password,
        'client': 'MOBILE',
      }),
    );

    if (response.statusCode == 200) {
      final lr = LoginResponse.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
      _accessToken = lr.accessToken;
      _role = lr.role;
      return lr;
    }
    throw AuthException(_parseError(response));
  }

  Future<void> logout() async {
    if (_accessToken == null) return;
    try {
      final uri = Uri.parse('${AppConfig.apiBase}/api/v1/auth/logout');
      await http.post(uri, headers: authHeaders);
    } finally {
      _accessToken = null;
      _role = null;
    }
  }

  String _parseError(http.Response r) {
    try {
      final body = jsonDecode(r.body) as Map<String, dynamic>;
      return body['message']?.toString() ?? 'HTTP ${r.statusCode}';
    } catch (_) {
      return 'HTTP ${r.statusCode}';
    }
  }
}

class AuthException implements Exception {
  final String message;
  AuthException(this.message);

  @override
  String toString() => message;
}
