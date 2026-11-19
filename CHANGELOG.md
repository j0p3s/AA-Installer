# Changelog

All notable changes to AA Installer. Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow [Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-11-19

### Added
- **Update check before installing**: if the installed app is signed with a different key, or is a newer version, AA Installer explains why Android can't update it and offers **Uninstall & install**. It warns that the app's data will be lost.
- **Uninstall & retry** button when an install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or `INSTALL_FAILED_VERSION_DOWNGRADE`.
- Car copies signed with an older key (after AA Installer was reinstalled or its data cleared) are detected, with an offer to replace them.
- **Crash reporter**: an uncaught crash is saved and shown on the next launch, with a *Copy & close* button.

### Changed
- `largeHeap` enabled so very large apps (180 MB+) can be car-patched.
- Clearer messages for signature and downgrade errors.

## [1.2.0] - 2026-11-19

### Added
- New interface built with Jetpack Compose and Material 3, using Material You colours from your wallpaper, in light and dark.
- Bottom navigation: **Install**, **Car copy**, **Fix apps**, **Setup**.
- App icons and status labels (Android Auto support, split parts, current installer).
- Searchable app lists, with a system-apps filter in Car copy.
- Progress panel with the current step, a live log and the result; full session log in the top bar.
- Setup checklist with buttons for Shizuku, Android Auto developer mode, battery settings and Developer options.
- Screenshot tests (Robolectric + Roborazzi).

### Changed
- Licence headers on all source files (GPL-3.0-only).

## [1.1.0] - 2026-11-19

### Added
- **Car copy**: builds a renamed `<package>.car` copy of any installed app that Android Auto lists and shows full screen. Ported from [CarBridge](https://github.com/jmwerk/CarBridge).
- **Car-enable before installing**: applies the car patch to a picked APK or bundle and keeps the package name.
- Car options: *Fill the car screen* and *Keep media services*.
- Patched APKs are signed on the device with an Android Keystore key and verified before installing.

### Changed
- The project is now licensed under **GPL-3.0**, as required by the CarBridge code.

## [1.0.0] - 2026-11-19

### Added
- Installs APK, APKS, XAPK and APKM files with `com.android.vending` as the installer, the on-device equivalent of `adb install -i com.android.vending`.
- Shell access through Shizuku, with `su` as a fallback.
- Android Auto support detection.
- **Fix installed app**: re-registers an installed app with the Play Store as installer, keeping its data.
- Open or share an APK from a file manager.
- Clear explanations for common install errors, including RedMagic/nubia `INSTALL_FAILED_USER_RESTRICTED`.

[1.3.0]: https://github.com/j0p3s/AA-Installer/releases/tag/v1.3.0
[1.2.0]: https://github.com/j0p3s/AA-Installer/releases/tag/v1.2.0
[1.1.0]: https://github.com/j0p3s/AA-Installer/releases/tag/v1.1.0
[1.0.0]: https://github.com/j0p3s/AA-Installer/releases/tag/v1.0.0
