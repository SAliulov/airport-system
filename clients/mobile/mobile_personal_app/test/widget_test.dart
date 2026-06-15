import 'package:flutter_test/flutter_test.dart';
import 'package:mobile_personal_app/main.dart';
import 'package:mobile_personal_app/models/airport_config.dart';

void main() {
  testWidgets('App renders login screen', (WidgetTester tester) async {
    const config = AirportConfig(
      homeIata: 'SVO',
      timezone: 'Europe/Moscow',
      gatePlanWindowHours: 2,
      gatePostGraceMinutes: 5,
    );
    await tester.pumpWidget(const AirportApp(airportConfig: config));
    expect(find.text('Войти'), findsOneWidget);
  });
}
