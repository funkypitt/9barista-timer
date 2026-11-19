import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' show PlatformDispatcher;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import 'strings.dart';

/// Milliseconds on a clock that never goes backwards. Replaced in tests.
int Function() nowMs = _monotonic;
final Stopwatch _stopwatch = Stopwatch()..start();
int _monotonic() => _stopwatch.elapsedMilliseconds;

/// The language of the texts; null follows the phone. Set in tests.
String? forcedLanguage;

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  runApp(const TimerApp());
}

String tr(String key) {
  final code = forcedLanguage ?? PlatformDispatcher.instance.locale.languageCode;
  return (strings[code] ?? strings['en']!)[key] ?? strings['en']![key] ?? key;
}

class TimerApp extends StatelessWidget {
  const TimerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Timer for 9Barista',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(brightness: Brightness.dark, useMaterial3: true),
      home: const TimerScreen(),
    );
  }
}

enum Phase { idle, heating, extracting, done }

const copper = Color(0xFFC87941);
const warmWhite = Color(0xFFF5E6D3);
const dim = Color(0xFF6B6560);
const green = Color(0xFF6B9E6B);
const red = Color(0xFFC75050);
const bgDefault = Color(0xFF0D0D0D);
const bgHeating = Color(0xFF1A0E05);

String clockText(int seconds) =>
    '${seconds ~/ 60}:${(seconds % 60).toString().padLeft(2, '0')}';

class TimerScreen extends StatefulWidget {
  const TimerScreen({super.key});

  @override
  State<TimerScreen> createState() => _TimerScreenState();
}

class _TimerScreenState extends State<TimerScreen> {
  Phase phase = Phase.idle;
  int heatingStart = 0;
  int extractionStart = 0;
  int heatingMs = 0;
  int extractionMs = 0;
  int now = nowMs();

  // The brewer's care: one quiet line after the brew, a page when it is tapped.
  bool care = false;

  Timer? _ticker;
  bool _buzzed25 = false;
  bool _buzzed30 = false;
  bool _buzzed8min = false;

  @override
  void dispose() {
    _ticker?.cancel();
    WakelockPlus.disable();
    super.dispose();
  }

  void _setPhase(Phase next) {
    phase = next;
    _ticker?.cancel();
    _ticker = null;
    final running = next == Phase.heating || next == Phase.extracting;
    // The screen stays on while a brew is being timed.
    if (running) {
      WakelockPlus.enable();
      _buzzed25 = _buzzed30 = _buzzed8min = false;
      _ticker = Timer.periodic(const Duration(milliseconds: 50), (_) => _tick());
    } else {
      WakelockPlus.disable();
    }
  }

  void _tick() {
    final t = nowMs();
    if (phase == Phase.extracting) {
      final s = (t - extractionStart) / 1000;
      if (!_buzzed25 && s >= 25) {
        HapticFeedback.mediumImpact();
        _buzzed25 = true;
      }
      if (!_buzzed30 && s >= 30) {
        HapticFeedback.heavyImpact();
        _buzzed30 = true;
      }
    }
    if (phase == Phase.heating && !_buzzed8min && (t - heatingStart) / 1000 >= 480) {
      HapticFeedback.vibrate();
      _buzzed8min = true;
    }
    setState(() => now = t);
  }

  void _onTap() {
    final t = nowMs();
    setState(() {
      if (care) {
        care = false;
        return;
      }
      switch (phase) {
        case Phase.idle:
          heatingStart = t;
          now = t;
          _setPhase(Phase.heating);
        case Phase.heating:
          heatingMs = t - heatingStart;
          extractionStart = t;
          now = t;
          _setPhase(Phase.extracting);
        case Phase.extracting:
          extractionMs = t - extractionStart;
          now = t;
          _setPhase(Phase.done);
        case Phase.done:
          _setPhase(Phase.idle);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final Widget content;
    if (care) {
      content = const CareContent();
    } else {
      content = switch (phase) {
        Phase.idle => const IdleContent(),
        Phase.heating => HeatingContent(elapsedMs: now - heatingStart),
        Phase.extracting => ExtractingContent(elapsedMs: now - extractionStart),
        Phase.done => DoneContent(
            heatingMs: heatingMs,
            extractionMs: extractionMs,
            onCare: () => setState(() => care = true),
          ),
      };
    }

    return AnnotatedRegion<SystemUiOverlayStyle>(
      // The screen is always dark: light icons in the status bar.
      value: SystemUiOverlayStyle.light,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: _onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 600),
          color: phase == Phase.heating ? bgHeating : bgDefault,
          child: SafeArea(
            child: DefaultTextStyle(
              style: const TextStyle(color: warmWhite, decoration: TextDecoration.none),
              child: Stack(
                alignment: Alignment.center,
                children: [
                  Positioned.fill(child: content),
                  if (phase == Phase.idle && !care)
                    Positioned(
                      bottom: 24,
                      child: Text(
                        tr('credits'),
                        style: const TextStyle(color: dim, fontSize: 11, letterSpacing: 1),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class PulsingHint extends StatefulWidget {
  final String text;
  const PulsingHint(this.text, {super.key});

  @override
  State<PulsingHint> createState() => _PulsingHintState();
}

class _PulsingHintState extends State<PulsingHint> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1500),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) => Text(
        widget.text,
        textAlign: TextAlign.center,
        style: TextStyle(
          color: warmWhite.withValues(alpha: 0.25 + 0.35 * _controller.value),
          fontSize: 13,
          letterSpacing: 1,
        ),
      ),
    );
  }
}

class IdleContent extends StatelessWidget {
  const IdleContent({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(
          tr('espresso_timer'),
          textAlign: TextAlign.center,
          style: const TextStyle(
            color: copper,
            fontSize: 22,
            fontWeight: FontWeight.bold,
            letterSpacing: 4,
          ),
        ),
        const SizedBox(height: 10),
        // The machine is named, and the app says at once that it is not the maker's.
        Text(
          tr('for_machine'),
          textAlign: TextAlign.center,
          style: const TextStyle(color: dim, fontSize: 13, letterSpacing: 1),
        ),
        const SizedBox(height: 64),
        PulsingHint(tr('tap_to_start')),
      ],
    );
  }
}

class HeatingContent extends StatelessWidget {
  final int elapsedMs;
  const HeatingContent({super.key, required this.elapsedMs});

  @override
  Widget build(BuildContext context) {
    final total = elapsedMs ~/ 1000;
    final over = total >= 480;
    return Column(
      children: [
        const Spacer(),
        Text(
          tr('heating'),
          style: const TextStyle(
            color: copper,
            fontSize: 16,
            fontWeight: FontWeight.w500,
            letterSpacing: 6,
          ),
        ),
        const SizedBox(height: 48),
        Text(
          clockText(total),
          style: TextStyle(
            color: over ? red : warmWhite,
            fontSize: 72,
            fontWeight: FontWeight.w300,
            fontFeatures: const [FontFeature.tabularFigures()],
          ),
        ),
        const SizedBox(height: 16),
        Text(
          over ? tr('remove_from_heat') : tr('heating_typical'),
          style: TextStyle(color: over ? red : dim, fontSize: 14, letterSpacing: 2),
        ),
        const Spacer(),
        PulsingHint(tr('tap_when_appears')),
        const SizedBox(height: 32),
      ],
    );
  }
}

class ExtractingContent extends StatelessWidget {
  final int elapsedMs;
  const ExtractingContent({super.key, required this.elapsedMs});

  @override
  Widget build(BuildContext context) {
    final seconds = elapsedMs / 1000;
    final timerColor = seconds < 25
        ? warmWhite
        : seconds <= 30
            ? green
            : red;
    return Column(
      children: [
        const Spacer(),
        Text(
          tr('extracting'),
          style: const TextStyle(
            color: copper,
            fontSize: 16,
            fontWeight: FontWeight.w500,
            letterSpacing: 6,
          ),
        ),
        const SizedBox(height: 32),
        SizedBox(
          width: 240,
          height: 240,
          child: CustomPaint(
            painter: ExtractionRing(seconds),
            child: Center(
              child: Text(
                seconds.toStringAsFixed(1),
                style: TextStyle(
                  color: timerColor,
                  fontSize: 64,
                  fontWeight: FontWeight.w300,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        Text(
          tr('target_time'),
          style: const TextStyle(color: dim, fontSize: 14, letterSpacing: 2),
        ),
        const Spacer(),
        PulsingHint(tr('tap_when_done')),
        const SizedBox(height: 32),
      ],
    );
  }
}

/// One turn of the ring is 30 seconds; the last sixth, 25 to 30 s, is the target.
class ExtractionRing extends CustomPainter {
  final double seconds;
  ExtractionRing(this.seconds);

  @override
  void paint(Canvas canvas, Size size) {
    const stroke = 6.0;
    final rect = Rect.fromLTWH(stroke / 2, stroke / 2, size.width - stroke, size.height - stroke);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = stroke;
    const turn = 2 * math.pi;

    canvas.drawArc(rect, 0, turn, false, paint..color = const Color(0xFF2A2A2A));
    canvas.drawArc(
      rect,
      (25 / 30) * turn - math.pi / 2,
      (5 / 30) * turn,
      false,
      paint..color = green.withValues(alpha: 0.15),
    );

    final color = seconds < 25
        ? copper
        : seconds <= 30
            ? green
            : red;
    canvas.drawArc(
      rect,
      -math.pi / 2,
      math.min(seconds / 30, 1) * turn,
      false,
      paint
        ..color = color
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(ExtractionRing old) => old.seconds != seconds;
}

class DoneContent extends StatelessWidget {
  final int heatingMs;
  final int extractionMs;
  final VoidCallback onCare;
  const DoneContent({
    super.key,
    required this.heatingMs,
    required this.extractionMs,
    required this.onCare,
  });

  @override
  Widget build(BuildContext context) {
    final heatSec = heatingMs ~/ 1000;
    final extSec = extractionMs / 1000;
    final inZone = extSec >= 25 && extSec <= 30;
    final extColor = inZone ? green : red;
    final verdict = tr(extSec < 25
        ? 'grind_finer'
        : extSec > 30
            ? 'grind_coarser'
            : 'perfect');
    const label = TextStyle(color: dim, fontSize: 12, letterSpacing: 3);

    return Column(
      children: [
        const Spacer(),
        Text(
          tr('done'),
          style: const TextStyle(
            color: copper,
            fontSize: 16,
            fontWeight: FontWeight.w500,
            letterSpacing: 6,
          ),
        ),
        const SizedBox(height: 48),
        Text(tr('heating'), style: label),
        const SizedBox(height: 4),
        Text(
          clockText(heatSec),
          style: const TextStyle(color: warmWhite, fontSize: 36, fontWeight: FontWeight.w300),
        ),
        const SizedBox(height: 32),
        Text(tr('extraction'), style: label),
        const SizedBox(height: 4),
        Text(
          '${extSec.toStringAsFixed(1)}s',
          style: TextStyle(color: extColor, fontSize: 48, fontWeight: FontWeight.w300),
        ),
        const SizedBox(height: 12),
        Text(
          verdict,
          style: TextStyle(
            color: extColor,
            fontSize: 14,
            fontWeight: FontWeight.w500,
            letterSpacing: 3,
          ),
        ),
        const SizedBox(height: 24),
        Text(
          tr('target_weight'),
          style: const TextStyle(color: dim, fontSize: 12, letterSpacing: 2),
        ),
        const Spacer(),
        // One quiet line, dimmer than the rest: the brewer's care is a tap away, never in the way.
        GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: onCare,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
            child: Text(
              tr('care_line'),
              style: TextStyle(
                color: dim.withValues(alpha: 0.7),
                fontSize: 13,
                letterSpacing: 1,
              ),
            ),
          ),
        ),
        const SizedBox(height: 8),
        PulsingHint(tr('tap_to_reset')),
        const SizedBox(height: 32),
      ],
    );
  }
}

/// The brewer's care, from 9Barista's own guides: when, and what.
/// Scrolls when the screen is small; a tap anywhere goes back.
class CareContent extends StatelessWidget {
  const CareContent({super.key});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(horizontal: 32),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 48),
          Text(
            tr('care_title'),
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: copper,
              fontSize: 14,
              fontWeight: FontWeight.w500,
              letterSpacing: 4,
            ),
          ),
          const SizedBox(height: 36),
          for (var i = 1; i <= 5; i++) ...[
            Text(
              tr('care_when_$i'),
              textAlign: TextAlign.center,
              style: const TextStyle(color: dim, fontSize: 12, letterSpacing: 3),
            ),
            const SizedBox(height: 6),
            Text(
              tr('care_what_$i'),
              textAlign: TextAlign.center,
              style: const TextStyle(
                color: warmWhite,
                fontSize: 15,
                fontWeight: FontWeight.w300,
                height: 22 / 15,
              ),
            ),
            const SizedBox(height: 26),
          ],
          Text(
            tr('care_source'),
            textAlign: TextAlign.center,
            style: TextStyle(color: dim.withValues(alpha: 0.7), fontSize: 11, letterSpacing: 1),
          ),
          const SizedBox(height: 36),
          PulsingHint(tr('tap_to_go_back')),
          const SizedBox(height: 32),
        ],
      ),
    );
  }
}
