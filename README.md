# Timer for 9Barista

A minimal espresso timer for the 9Barista stovetop espresso machine.
Three taps per brew, nothing more.

1. Tap to start heating — counts up, warns at 8 minutes
2. Tap when espresso appears — extraction timer with a progress ring,
   green in the 25–30 s target zone
3. Tap when done — results and grind adjustment advice

Haptic feedback at 25 s, 30 s and 8 min. Screen stays on while brewing.
No ads, no tracking, no accounts. Only permission: vibration.

## Install

- **F-Droid:** add the repo `https://funkypitt.github.io/fdroid-repo/repo`
  and search for "Timer for 9Barista"
- **APK:** grab the latest from the
  [repo listing](https://funkypitt.github.io/fdroid-repo/repo/)
- **Build:** `ANDROID_HOME=<sdk> ./gradlew assembleRelease`

Requires Android 8.0+.

## Disclaimer

This is an unofficial, fan-made app. It is not affiliated with or
endorsed by 9Barista Ltd. "9Barista" is a trademark of its owner and
is used here only to identify the machine the timer is designed for.

## License

MIT
