# AA Installer user guide

This guide covers AA Installer v1.3.0 and later.

- [1. What you need](#1-what-you-need)
- [2. Set up Shizuku (no root)](#2-set-up-shizuku-no-root)
- [3. Set up Android Auto](#3-set-up-android-auto)
- [4. Install tab](#4-install-tab)
- [5. Car copy tab](#5-car-copy-tab)
- [6. Fix apps tab](#6-fix-apps-tab)
- [7. Setup tab](#7-setup-tab)
- [8. Log and progress](#8-log-and-progress)
- [9. Updating and uninstalling](#9-updating-and-uninstalling)
- [10. Tips for RedMagic / nubia phones](#10-tips-for-redmagic--nubia-phones)

---

## 1. What you need

- An Android phone with **Android 8.0 or newer**. AA Installer is built for Android 16.
- **Android Auto** installed (it comes built in on Android 10 and newer).
- **One** of these:
  - **Shizuku**, which gives AA Installer the same rights as `adb` without root, or
  - **root** (Magisk, KernelSU…). AA Installer uses `su` automatically when Shizuku isn't available.

AA Installer has no internet permission. Everything happens on the phone.

## 2. Set up Shizuku (no root)

1. Install Shizuku from the [Play Store](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) or from [GitHub](https://github.com/RikkaApps/Shizuku/releases).
2. Turn on **Developer options**: Settings → About phone → tap **Build number** 7 times.
3. Connect to **Wi-Fi**. Wireless debugging only works on Wi-Fi.
4. Open Shizuku → **Start via Wireless debugging** → **Pairing**, and follow its instructions. You'll open *Developer options → Wireless debugging → Pair device with pairing code*, then type the code into Shizuku's notification.
5. Tap **Start** in Shizuku.
6. Open AA Installer. The banner says **Allow Shizuku access**. Tap **Allow**, then confirm.

The top bar now shows **Shizuku ready**.

> Shizuku stops when the phone reboots. After a reboot, open Shizuku and tap **Start** again. You don't need to pair again.

## 3. Set up Android Auto

Android Auto hides apps that weren't installed by the Play Store **unless** developer mode is on:

1. AA Installer → **Setup** tab → **Open Android Auto**. Or go to Settings → Connected devices → Android Auto.
2. Scroll down and tap **Version** 10 times, then confirm.
3. Tap **⋮** (top right) → **Developer settings**.
4. Turn on **Unknown sources**.

Recommended: Setup tab → **Android Auto app info** → Battery → **Unrestricted**.

## 4. Install tab

1. Tap **Choose an app to install** and pick a file:
   - `.apk`: a normal app
   - `.apks`, `.xapk`, `.apkm`: split-APK bundles; AA Installer unpacks them

   You can also **share** an APK to AA Installer, or **open** it with AA Installer from your file manager.
2. Check the card:
   - **Android Auto ✓**: the app declares Android Auto support.
   - **No Android Auto support**: it installs, but won't show in the car unless you car-enable it.
   - **2 parts / 3 parts…**: a split APK.
   - **Installed · …**: the app is already installed, and by whom. Installing again updates it and keeps its data.
3. Optional: **Car-enable before installing** applies the [car patch](#car-patch-options) to this APK and keeps its package name. The APK is re-signed on your phone.
4. Tap **Install as Play Store app** (or **Update as Play Store app**).
5. When the progress panel shows ✅, reconnect to Android Auto.

### When the app is already installed

Android only updates an app with an APK **signed by the same key** and with the **same or a newer version**. Before installing, AA Installer checks both. If the update can't work, for example a modded Waze over the Play Store Waze, or any car-enabled APK over the original, it shows **Uninstall … first?** with the reason:

- **Uninstall & install** removes the installed app **and its data** (sign-in, settings, downloads), then installs the new one.
- **Cancel** leaves everything as it is.

If an install still fails for this reason, the progress panel shows an **Uninstall & retry** button that does the same.

## 5. Car copy tab

This tab builds a **car copy** of any app on your phone. CarBridge describes the result as showing the app's full interface on the car display while parked.

1. Set the [car patch options](#car-patch-options).
2. Search for the app and tap it. Use the **System apps** filter to show built-in apps.
3. Confirm **Build**.

What you get:

- A new app, **"*Name* (Car)"**, with package `<original>.car` and a badged icon.
- The original app is **not changed** and keeps updating from its store.
- The copy has **its own empty data**, so you sign in again.
- Apps that already have a copy show a **Car copy** label. Tapping one again **rebuilds** it, for example after the original updated.

### Car patch options

| Option | Default | Effect |
|---|---|---|
| **Fill the car screen** | On | Removes portrait-only locks, so screens fill the wide car display. Turn it off if a screen breaks. |
| **Keep media services** | Off | Music and podcast apps keep Android Auto's own audio player. With it off, they show the full app instead. |

### Which apps work

- Most apps work: browsers, video players, games, utilities.
- Apps that check **Play Integrity**, **their own signature** or a **licence** usually refuse to run, because the copy is re-signed.
- Google sign-in may not work in a copy, because the copy has a different package name and signature.

## 6. Fix apps tab

This tab lists the **Android Auto apps** on your phone:

- ✅ green tick: installed by the Play Store; Android Auto will show it.
- ⚠️ warning: installed by something else, like a file manager, browser or patch manager.

Tap a ⚠️ app → **Fix**. AA Installer reinstalls the app over itself from its own files with the Play Store as installer. **The app and its data are kept.** Use **↻** to refresh the list.

## 7. Setup tab

A checklist with buttons that open the right screens:

1. **Shizuku**: status, and opening or allowing Shizuku.
2. **Android Auto developer mode**: opens Android Auto's settings.
3. **Keep Android Auto alive**: opens Android Auto's app info, for battery settings.
4. **RedMagic / nubia only**: opens Developer options (see [section 10](#10-tips-for-redmagic--nubia-phones)).

## 8. Log and progress

- Every action opens a **progress panel** with the current step, a live log and the result: ✅ done, ⚠️ installed but check it, or ❌ failed with the reason.
- The **list icon** at the top right shows the full log of the session. You can select and copy the text for bug reports.

## 9. Updating and uninstalling

- **If AA Installer crashes**, the next time you open it a dialog shows the crash report. Tap **Copy & close** and paste it into an [issue](https://github.com/j0p3s/AA-Installer/issues).
- **Updating AA Installer:** install the newer APK over the old one. Releases are signed with the same key, so your data is kept.
- **Car copies are signed with a key stored inside AA Installer** (Android Keystore). If you uninstall AA Installer or clear its data, that key is gone. AA Installer then detects old car copies and offers to replace them, which uninstalls the old copy and its data.
- Uninstall a car copy like any app. The original is not affected.

## 10. Tips for RedMagic / nubia phones

- If installs fail with `INSTALL_FAILED_USER_RESTRICTED`, open Developer options and turn on **Install via USB**. Also turn on **USB debugging (Security settings)** if your ROM has it.
- RedMagic's power management can stop Android Auto and Shizuku in the background. Set both to **Unrestricted** battery usage.
- Shizuku stops after every reboot. Start it again from the Shizuku app.

---

Still stuck? See [Troubleshooting](TROUBLESHOOTING.md), or open an [issue](https://github.com/j0p3s/AA-Installer/issues) with the log.
