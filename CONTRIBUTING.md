# Contributing

Thanks for helping. Bug reports, fixes and translations are welcome.

## Reporting bugs

Use the [bug report form](https://github.com/j0p3s/AA-Installer/issues/new?template=bug_report.yml) and include the log from the app (list icon, top right). See [Troubleshooting](docs/TROUBLESHOOTING.md) first; many problems are Android Auto or Shizuku settings.

Requests to make one specific app work in Android Auto usually can't be fixed in AA Installer. Apps that check their signature or Play Integrity will refuse a re-signed copy.

## Development

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

- Java 17+, Android SDK 36, Gradle wrapper included.
- UI code is in `Ui.kt` (Compose, Material 3). Install logic is in `Installer.java` and `Shell.java`.
- Screenshot tests (`ScreensTest`) write PNGs to `/tmp/shots`. Check them when you change the UI.
- Code under `org/carbridge/` is ported from [CarBridge](https://github.com/jmwerk/CarBridge). Fixes to the patch rules belong upstream first.

## Pull requests

- One topic per PR, with a clear description and how you tested it (device, Android version).
- **Bump `versionCode` and `versionName`** in `app/build.gradle` for every user-visible change, and add an entry to `CHANGELOG.md`.
- Keep the app free of network access, analytics and ads.

## Licence

By contributing you agree that your contribution is licensed under the **GNU GPL v3.0** (GPL-3.0-only), like the rest of the project. Add the standard header to new source files:

```
/*
 * AA Installer - install Android Auto apps as Play Store apps
 * Copyright (C) <year> <your name>
 * ...
 * SPDX-License-Identifier: GPL-3.0-only
 */
```
