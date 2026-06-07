import 'package:flutter/material.dart';

/// Переключатель светлой/тёмной темы в AppBar.
class ThemeToggleButton extends StatelessWidget {
  final bool isDarkMode;
  final VoidCallback onToggle;

  const ThemeToggleButton({super.key, required this.isDarkMode, required this.onToggle});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      icon: Icon(isDarkMode ? Icons.light_mode : Icons.dark_mode),
      tooltip: isDarkMode ? 'Светлая тема' : 'Тёмная тема',
      onPressed: onToggle,
    );
  }
}
