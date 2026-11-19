# AA Installer

Install Android Auto apps from APK files **directly on your phone**, registered as if they came from the **Google Play Store**, so Android Auto shows them.

It does the same thing as this PC command, but without a PC:

```
adb install -r -i com.android.vending app.apk
```

It also includes the **[CarBridge](https://github.com/jmwerk/CarBridge)** car patch, which puts an app's **full interface on the Android Auto screen** (intended for use while parked), either as a separate "App (Car)" copy or patched into an APK before installing.

Built for Android 16 (targets SDK 36). Works on Android 8.0 and newer.

![Screens](docs/screens.png)

## Why this is needed

Android Auto only lists apps whose *installer of record* is the Play Store. Apps you sideload are recorded as installed by your file manager or browser, so Android Auto hides them, even when they fully support Android Auto.

Only privileged installers (adb/shell or root) can set a different installer. AA Installer gets that privilege through **[Shizuku](https://github.com/RikkaApps/Shizuku)** (no root needed) or through **root**, and then runs the same `pm install-create / install-write / install-commit` session that `adb install` uses.

## Features

- **Car copy of any installed app** (CarBridge): builds a renamed `<package>.car` copy with a badged icon. Android Auto lists it and shows the app's full interface. The original is untouched.
- **Car-enable an APK before installing** (like CarBridge Patches for Morphe): same package name, so it replaces the app
- Car options: *Fill the car screen* (removes portrait-only locks) and *Keep media services* (music apps keep Android Auto's audio player)
- Install **APK**, **APKS**, **XAPK** and **APKM** (split APKs; parts for other CPU architectures are skipped)
- Shows the app name, package, version and whether it **declares Android Auto support**
- **Fix installed apps**: re-registers an already installed Android Auto app as a Play Store install, with no uninstall and no data loss
- Checks the result after installing (reads the installer back from Android)
- "Open with" / "Share" an APK straight from your file manager
- Shortcuts to the Android Auto settings and its battery settings
- No internet permission, no ads, no tracking

## Setup (Shizuku, no root)

1. Install **Shizuku** ([GitHub](https://github.com/RikkaApps/Shizuku/releases) or Play Store).
2. Enable **Developer options** → **Wireless debugging**, then start Shizuku with the *Pairing* option (Shizuku walks you through it).
3. Open **AA Installer** and tap **Allow Shizuku access**.

On a rooted phone, skip Shizuku. AA Installer falls back to `su` automatically.

> **RedMagic / nubia:** if installing fails with `INSTALL_FAILED_USER_RESTRICTED`, enable **Install via USB** (and **USB debugging (Security settings)** if your ROM has it) in Developer options.

## Usage

1. **Select APK / APKS / XAPK / APKM** and check the Android Auto line.
2. Tap **Install as Play Store app**.
3. In Android Auto, open the settings, tap the **version number 10×** to unlock developer settings, open the ⋮ menu → **Developer settings** → enable **Unknown sources**.
4. Reconnect to the car (or restart Android Auto).

Already installed the app another way? Use **Choose installed app…** under *Fix an app that is already installed*.

Tip: set Android Auto's battery usage to **Unrestricted** so the phone's power manager doesn't kill it.

## Car patch (CarBridge)

- **Make car copy…** picks any installed app and builds "*App* (Car)". The copy has its own data, so you sign in again.
- **Car-enable it first** (section 1) patches the APK you picked and keeps its package name. Patched APKs are re-signed with a key created on your phone, so if the app is already installed with another signature, uninstall it first.
- The signing key lives in the Android Keystore. If you uninstall AA Installer or clear its data, uninstall existing car copies before rebuilding them.
- Apps that check Play Integrity, their own signature or a license will not work after re-signing.
- Apps that use the car-app template service, and media apps unless *Keep media services* is on, lose Android Auto's own template UI in favour of the full app.

## Limitations

- It cannot add Android Auto support to an app that doesn't have it.
- APKs are not modified and keep their original signatures. An update must be signed with the same key as the installed version.
- Whether an app appears can still depend on the Android Auto version, the car and Google's server-side rules.
- Only install APKs from sources you trust.

## Credits and license

AA Installer is licensed under the **GNU GPL v3.0** (see `LICENSE`).

The car patch (`org.carbridge.core` and `org.carbridge.patch`) is ported from [CarBridge](https://github.com/jmwerk/CarBridge) by jmwerk (GPL-3.0, commit `8fea836`). It uses [ARSCLib](https://github.com/REAndroid/ARSCLib) and Google's `apksig`. CarBridge credits its car-visibility entries to AABrowser (GPL-3.0). Installs run through [Shizuku](https://github.com/RikkaApps/Shizuku).

## Building

```
./gradlew assembleRelease
```

Release signing reads `AAI_KEYSTORE`, `AAI_KS_PASS`, `AAI_KEY_ALIAS` and `AAI_KEY_PASS` from the environment.

## Disclaimer

Not affiliated with Google, Android Auto or any app vendor. Never interact with your phone while driving.
