# Troubleshooting

Open the **log** (list icon, top right) to see exactly what happened. The `pm` output there includes Android's own error code.

## The app installed but doesn't appear in Android Auto

Work through these in order:

1. **Unknown sources off.** Open Android Auto → tap Version 10× → ⋮ → Developer settings → **Unknown sources** on. Android Auto updates sometimes turn it off again.
2. **Stale app list.** Settings → Apps → Android Auto → **Force stop** and **Clear cache** (not data), then reconnect.
3. **Installer not Play Store.** Check the **Fix apps** tab. If the app shows ⚠️, tap it → Fix.
4. **No Android Auto support.** If the Install tab said *No Android Auto support*, nothing is wrong with the install. Use **Car-enable before installing** or a **Car copy** instead.
5. **Hidden in the launcher settings.** In Android Auto → Customise launcher, make sure the app is ticked.
6. **Signature check (navigation apps).** Some Google-owned or allow-listed navigation packages, for example a modded `com.waze`, seem to be checked by signing key, not only by installer. If the app shows ✅ in Fix apps and still doesn't appear, use a **Car copy**: it has a different package name.

## Error messages

| Message / code | Meaning | What to do |
|---|---|---|
| *No shell access. Start Shizuku…* | Shizuku isn't running or not allowed, and there's no root | Start Shizuku and tap **Allow** in AA Installer |
| *Could not start the Shizuku shell service* | Shizuku stopped while connecting | Open Shizuku, tap **Start**, try again |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed app has a different signature | Tap **Uninstall & install** / **Uninstall & retry** (deletes the app's data). Car-enabled APKs are always re-signed. |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | A newer version is installed | Tap **Uninstall & install** / **Uninstall & retry** |
| *Could not uninstall …* | The shell couldn't remove the app (device owner, system app) | Uninstall it from Settings → Apps, then install again |
| `INSTALL_FAILED_USER_RESTRICTED` | The ROM blocks shell/USB installs | Developer options → **Install via USB** on (RedMagic, nubia, Xiaomi…) |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | Built for another CPU type | Get the arm64-v8a build of the app |
| `INSTALL_FAILED_MISSING_SPLIT` | A split APK is missing parts | Pick the whole `.apks`/`.xapk`/`.apkm` bundle, not one APK |
| `INSTALL_FAILED_DEPRECATED_SDK_VERSION` | The app targets an Android version that's too old | AA Installer already passes `--bypass-low-target-sdk-block`; if this still appears, the ROM refuses it |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` / *Not enough free space* | Not enough storage | Patching needs about 2× the app's size free. Free up space. |
| *This is not an APK or an APK bundle* | The file isn't a valid zip/APK | Download it again |
| *no launcher activity found* | Car patch: the app has no launcher screen | That app can't be car-enabled |
| *The signed copy didn't pass Android's signature check* | Car patch signing failed verification | Report it with the log |

## AA Installer itself crashed

Open AA Installer again. A dialog shows the saved crash report. Since v1.3.1 this includes native crashes and "app not responding" events on Android 11+. Tap **Copy & close** and paste it into a [bug report](https://github.com/j0p3s/AA-Installer/issues/new?template=bug_report.yml).

- **v1.2.0 / v1.3.0 close as soon as you open the Car copy tab** (RedMagic/nubia, Android 16): fixed in **v1.3.1**. Update.
- For a full native backtrace, take a bug report (Developer options → *Take bug report*) or run `adb logcat -b crash`.

## Car copies

- **Copy won't update ("signed with an older key")**: AA Installer was reinstalled or its data cleared, so the signing key changed. Tapping the app offers to replace the copy (its data is lost).
- **Copy opens but crashes or says it isn't genuine**: the app checks Play Integrity or its own signature, so it can't run as a re-signed copy.
- **Car copy shows "can't be used while driving"**: this is expected. Car apps are parked-only.
- **A car-patched app crashes on start or misses images/strings**: rebuild it with **Single APK** off, so the split APKs are kept as they are.
- **A screen looks broken on the car display**: rebuild the copy with **Fill the car screen** off.
- **Music app shows Android Auto's player instead of the full app**: rebuild with **Keep media services** off, or the other way round.

## Shizuku

- **Shizuku stops after reboot**: this is normal without root. Open Shizuku and tap **Start**.
- **Wireless debugging turns itself off**: stay on Wi-Fi while starting Shizuku. Some ROMs turn it off when Wi-Fi changes.
- **The Allow dialog doesn't show**: open the Shizuku app → Authorised applications → enable **AA Installer**.

## Reporting a bug

Open an [issue](https://github.com/j0p3s/AA-Installer/issues/new/choose) with:

- Phone model, Android version and AA Installer version
- Shizuku or root
- The log from the progress panel or the top-right log
- The app you tried, and where it came from
