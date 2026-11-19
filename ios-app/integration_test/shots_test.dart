// Walks through one brew on a clock the test controls. At each scene the line
// "SHOT:<name>" tells the script that runs the test (codemagic.yaml,
// ios-screenshots) to take a screenshot of the simulator.
import 'package:baristatimer/main.dart' as app;
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

Future<void> wait(WidgetTester tester, [int seconds = 2]) async {
  for (var i = 0; i < seconds * 5; i++) {
    await tester.pump(const Duration(milliseconds: 200));
  }
}

Future<void> shot(WidgetTester tester, String name) async {
  await wait(tester, 2);
  // ignore: avoid_print
  print('SHOT:$name');
  await wait(tester, 6);
}

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('one brew, from the stove to the verdict', (tester) async {
    var clock = 0;
    app.nowMs = () => clock;
    app.forcedLanguage = 'en';
    app.main();
    await wait(tester, 3);
    await shot(tester, '01-start');

    await tester.tap(find.text('TAP TO START'));
    clock += 222000; // 3:42 on the stove
    await shot(tester, '02-heating');
    expect(find.text('3:42'), findsOneWidget);

    await tester.tap(find.text('3:42'));
    clock += 27300;
    await shot(tester, '03-extracting');
    expect(find.text('27.3'), findsOneWidget);

    await tester.tap(find.text('27.3'));
    await shot(tester, '04-done');
    expect(find.text('PERFECT'), findsOneWidget);

    await tester.tap(find.text('care and maintenance'));
    await shot(tester, '05-care');
    expect(find.text('CARE AND MAINTENANCE'), findsOneWidget);
  });
}
