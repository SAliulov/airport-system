class LoginResponse {
  final String accessToken;
  final String tokenType;
  final String role;

  LoginResponse({
    required this.accessToken,
    required this.tokenType,
    required this.role,
  });

  factory LoginResponse.fromJson(Map<String, dynamic> json) {
    return LoginResponse(
      accessToken: json['accessToken'] as String? ?? '',
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      role: json['role'] as String? ?? '',
    );
  }
}
