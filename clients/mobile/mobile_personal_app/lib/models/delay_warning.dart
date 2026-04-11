class DelayWarning {
  final int warningId;
  final int delayMinutes;
  final String? reason;
  final String? createdAt;

  DelayWarning({
    required this.warningId,
    required this.delayMinutes,
    this.reason,
    this.createdAt,
  });

  factory DelayWarning.fromJson(Map<String, dynamic> json) {
    return DelayWarning(
      warningId: json['warningId'] as int? ?? 0,
      delayMinutes: json['delayMinutes'] as int? ?? 0,
      reason: json['reason'] as String?,
      createdAt: json['createdAt']?.toString(),
    );
  }
}
