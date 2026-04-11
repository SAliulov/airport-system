class GateSummary {
  final int gateId;
  final String gateNumber;
  final String? terminal;

  GateSummary({required this.gateId, required this.gateNumber, this.terminal});

  factory GateSummary.fromJson(Map<String, dynamic> json) {
    return GateSummary(
      gateId: json['gateId'] as int? ?? 0,
      gateNumber: json['gateNumber'] as String? ?? '',
      terminal: json['terminal'] as String?,
    );
  }
}

class GateAssignment {
  final int assignmentId;
  final String? assignedFrom;
  final String? assignedTo;
  final GateSummary? gate;

  GateAssignment({
    required this.assignmentId,
    this.assignedFrom,
    this.assignedTo,
    this.gate,
  });

  factory GateAssignment.fromJson(Map<String, dynamic> json) {
    final gateJson = json['gate'] as Map<String, dynamic>?;
    return GateAssignment(
      assignmentId: json['assignmentId'] as int? ?? 0,
      assignedFrom: json['assignedFrom']?.toString(),
      assignedTo: json['assignedTo']?.toString(),
      gate: gateJson != null ? GateSummary.fromJson(gateJson) : null,
    );
  }
}
