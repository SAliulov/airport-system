import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_personal_app/main.dart';

void main() {
  testWidgets('App renders login screen', (WidgetTester tester) async {
    await tester.pumpWidget(const AirportApp());
    expect(find.text('Войти'), findsOneWidget);
  });
}
