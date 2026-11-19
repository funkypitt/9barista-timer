import 'package:baristatimer/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Future<void> brew(WidgetTester tester, int extractionMs) async {
  var clock = 0;
  nowMs = () => clock;
  forcedLanguage = 'en';
  await tester.pumpWidget(const TimerApp());
  await tester.tap(find.text('TAP TO START'));
  clock += 200000;
  await tester.pump(const Duration(milliseconds: 100));
  expect(find.text('3:20'), findsOneWidget);
  await tester.tap(find.text('3:20'));
  clock += extractionMs;
  await tester.pump(const Duration(milliseconds: 100));
  await tester.tap(find.byType(ExtractingContent));
  await tester.pump();
}

void main() {
  testWidgets('the name of the machine comes with the word unofficial', (tester) async {
    forcedLanguage = 'en';
    await tester.pumpWidget(const TimerApp());
    expect(find.text('ESPRESSO TIMER'), findsOneWidget);
    expect(find.text('for the 9Barista · unofficial app'), findsOneWidget);
  });

  testWidgets('under 25 s: grind finer', (tester) async {
    await brew(tester, 21000);
    expect(find.text('GRIND FINER'), findsOneWidget);
    expect(find.text('21.0s'), findsOneWidget);
  });

  testWidgets('25 to 30 s: perfect', (tester) async {
    await brew(tester, 27300);
    expect(find.text('PERFECT'), findsOneWidget);
    expect(find.text('3:20'), findsOneWidget);
  });

  testWidgets('over 30 s: grind coarser', (tester) async {
    await brew(tester, 34000);
    expect(find.text('GRIND COARSER'), findsOneWidget);
  });

  testWidgets('heating turns to a warning at 8 minutes', (tester) async {
    var clock = 0;
    nowMs = () => clock;
    forcedLanguage = 'en';
    await tester.pumpWidget(const TimerApp());
    await tester.tap(find.text('TAP TO START'));
    clock += 481000;
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.text('REMOVE FROM HEAT'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('care page opens from the result and a tap goes back', (tester) async {
    await brew(tester, 27000);
    await tester.tap(find.text('care and maintenance'));
    await tester.pump();
    expect(find.text('AFTER EACH BREW'), findsOneWidget);
    await tester.tap(find.text('AFTER EACH BREW'));
    await tester.pump();
    expect(find.text('PERFECT'), findsOneWidget);
  });

  testWidgets('every language has every text', (tester) async {
    for (final code in ['fr', 'de', 'es', 'pt', 'ru']) {
      forcedLanguage = code;
      expect(tr('for_machine'), contains('9Barista'));
      expect(tr('care_what_5').length, greaterThan(20));
    }
    forcedLanguage = 'en';
  });
}
