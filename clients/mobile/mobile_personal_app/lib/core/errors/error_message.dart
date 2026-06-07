import '../../services/airport_api.dart';
import '../../services/auth_service.dart';

/// Преобразует исключение в сообщение для пользователя.
String userErrorMessage(Object error) {
  if (error is AuthException) return error.message;
  if (error is ApiException) return error.message;
  final text = error.toString();
  if (text.contains('SocketException') || text.contains('Failed host lookup')) {
    return 'Нет подключения к серверу';
  }
  return text.replaceFirst('Exception: ', '');
}
