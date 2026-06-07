import 'package:shared_preferences/shared_preferences.dart';

const _themeModeKey = 'theme_mode';

enum AppThemePreference { light, dark }

class ThemePreferences {
  static Future<AppThemePreference> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_themeModeKey);
    if (raw == 'dark') return AppThemePreference.dark;
    return AppThemePreference.light;
  }

  static Future<void> save(AppThemePreference preference) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _themeModeKey,
      preference == AppThemePreference.dark ? 'dark' : 'light',
    );
  }
}
