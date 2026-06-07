/// Форматирование времён аэропорта.
///
/// API отдаёт naive wall strings в [timezone]; отображение без смещения UTC.
class AirportTime {
  AirportTime._();

  static String formatDateTime(
    String? iso, {
    required String timezone,
    bool withYear = false,
  }) {
    assert(timezone.isNotEmpty);
    if (iso == null || iso.isEmpty) return '—';
    final t = iso.trim();
    final match = RegExp(r'^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})').firstMatch(t);
    if (match != null) {
      final y = match.group(1)!;
      final m = match.group(2)!;
      final d = match.group(3)!;
      final hh = match.group(4)!;
      final mm = match.group(5)!;
      return withYear ? '$d.$m.$y $hh:$mm' : '$d.$m $hh:$mm';
    }
    return t.length >= 16 ? t.substring(0, 16).replaceFirst('T', ' ') : t;
  }
}
