import 'package:shared_preferences/shared_preferences.dart';

/// Конфигурация подключения к backend (runtime + compile-time override).
class AppConfig {
  static const String _prefsKey = 'api_base';
  static const String defaultApiBase = 'https://45.133.74.67';

  static String _apiBase = defaultApiBase;
  static bool _loaded = false;

  static String get apiBase => _apiBase;

  static String get wsUrl {
    final uri = Uri.parse(_apiBase);
    final scheme = uri.scheme == 'https' ? 'wss' : 'ws';
    final port = uri.hasPort ? uri.port : (uri.scheme == 'https' ? 443 : 80);
    return '$scheme://${uri.host}:$port/ws/raw';
  }

  /// Загрузить сохранённый адрес или `--dart-define=API_BASE=...`.
  static Future<void> ensureLoaded() async {
    if (_loaded) return;
    const fromEnv = String.fromEnvironment('API_BASE');
    if (fromEnv.isNotEmpty) {
      _apiBase = fromEnv;
      _loaded = true;
      return;
    }
    final prefs = await SharedPreferences.getInstance();
    _apiBase = prefs.getString(_prefsKey) ?? defaultApiBase;
    _loaded = true;
  }

  static Future<void> setApiBase(String value) async {
    final trimmed = value.trim().replaceAll(RegExp(r'/+$'), '');
    _apiBase = trimmed.isEmpty ? defaultApiBase : trimmed;
    _loaded = true;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefsKey, _apiBase);
  }
}
