# AA Installer

Install Android Auto apps from APK files **directly on your phone**, registered as if they came from the **Google Play Store**, so Android Auto shows them.

It does the same thing as this PC command, but without a PC:

```
adb install -r -i com.android.vending app.apk
```

Built for Android 16 (targets SDK 36). Works on Android 8.0 and newer.

## Why this is needed

Android Auto only lists apps whose *installer of record* is the Play Store. Apps you sideload are recorded as installed by your file manager or browser, so Android Auto hides them, even when they fully support Android Auto.

Only privileged installers (adb/shell or root) can set a different installer. AA Installer gets that privilege through **[Shizuku](https://github.com/RikkaApps/Shizuku)** (no root needed) or through **root**, and then runs the same `pm install-create / install-write / install-commit` session that `adb install` uses.

## Features

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

## Limitations

- It cannot add Android Auto support to an app that doesn't have it.
- APKs are not modified and keep their original signatures. An update must be signed with the same key as the installed version.
- Whether an app appears can still depend on the Android Auto version, the car and Google's server-side rules.
- Only install APKs from sources you trust.

## Building

```
./gradlew assembleRelease
```

Release signing reads `AAI_KEYSTORE`, `AAI_KS_PASS`, `AAI_KEY_ALIAS` and `AAI_KEY_PASS` from the environment.

## Disclaimer

Not affiliated with Google, Android Auto or any app vendor. Never interact with your phone while driving.
