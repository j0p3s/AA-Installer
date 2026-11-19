# How it works

## Why the installer matters

Every installed app has an *installer of record*. You can see it with `PackageManager.getInstallSourceInfo(pkg).getInstallingPackageName()`. Android Auto only shows third-party apps whose installer is `com.android.vending` (Google Play), unless developer mode's *Unknown sources* is on. Even with that on, some builds and some apps behave better when Play is the recorded installer.

An ordinary app can't set someone else as the installer. `PackageInstaller` rejects `setInstallerPackageName("com.android.vending")` unless the caller holds `INSTALL_PACKAGES`. The **shell user** (`adb`, uid 2000) and **root** are exceptions, which is why `adb install -i com.android.vending` works.

## Getting shell rights on the phone

```
AA Installer (app uid)
   │ bindUserService()
   ▼
Shizuku server (shell uid 2000, or root)
   │ starts app_process with AA Installer's APK
   ▼
ShellService (runs as shell)  ── AIDL: run(cmd[], stdin) ──▶ `pm …`
```

- `Shell.ShizukuShell` binds a **Shizuku UserService** (`ShellService`). Shizuku starts it in a separate process with the shell uid, or root if Shizuku runs as root.
- AA Installer sends commands over AIDL and streams file contents through a `ParcelFileDescriptor` pipe into the command's stdin.
- With root and no Shizuku, `Shell.RootShell` runs the same commands through `su -c`.

## The install session

The same session `adb install-multiple` uses:

```sh
pm install-create -r -i com.android.vending --user current -S <total bytes> [--bypass-low-target-sdk-block]
#   → Success: created install session [1234]
pm install-write -S <size> 1234 base.apk -          # stdin = APK bytes, once per part
pm install-write -S <size> 1234 split_config.arm64_v8a.apk -
pm install-commit 1234
#   → Success
```

- `--bypass-low-target-sdk-block` (Android 14+) lets apps with an old `targetSdk` install. AA Installer retries without it on ROMs that don't know the flag.
- If any step fails, the session is abandoned (`pm install-abandon`).
- Uninstalling (only after the user confirms) is `pm uninstall <package>`.
- Afterwards AA Installer reads the installer back with `getInstallSourceInfo` to confirm it is `com.android.vending`.

### Update check

Before installing over an existing package, AA Installer compares:

- **version**: the picked APK's `versionCode` against the installed one (Android refuses downgrades),
- **signer**: `PackageInfo.signingInfo.apkContentsSigners` of the installed app against the APK, accepting a rotated key from the APK's signing history. For a car-enabled APK, the check is against AA Installer's Keystore certificate.

If either would make Android refuse the update, the user is asked whether to run `pm uninstall <package>` first. If `install-commit` still fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or `INSTALL_FAILED_VERSION_DOWNGRADE`, the job offers the same uninstall-and-retry.

**Fix apps** feeds an installed app's own files (`ApplicationInfo.sourceDir` + `splitSourceDirs`) into the same session with `-r`. The package and its data stay; only the installer of record changes.

## Bundles

`.apks`, `.xapk` and `.apkm` are zip files containing several APKs. AA Installer:

1. copies the picked file to its cache,
2. treats it as one APK if it has `AndroidManifest.xml` at the root, otherwise extracts every `*.apk` inside,
3. skips `config.<abi>` splits for CPU types the phone doesn't support,
4. picks the base APK (`base.apk`, otherwise the largest non-split) and reads its label, icon and metadata.

An app counts as supporting Android Auto if its manifest has `com.google.android.gms.car.application` or `androidx.car.app.minCarApiLevel` metadata.

## The car patch (CarBridge)

`org.carbridge.core.CarPatch` and `org.carbridge.patch.*` are ported from [CarBridge](https://github.com/jmwerk/CarBridge) (GPL-3.0, commit `8fea836`). The binary manifest is edited with ARSCLib:

| Change | Why |
|---|---|
| `application android:appCategory="game"` | Android Auto lets this category show a full UI |
| `<meta-data distractionOptimized=true>` on the app and launcher activities | Marks the UI as allowed on the car display |
| `androidx.car.app.minCarApiLevel = 7` | Car API level declaration |
| `com.google.android.gms.car.application` → `res/xml/carbridge_automotive_app_desc.xml` (`<uses name="media"/>`), added only if missing | Makes Android Auto list the app |
| `androidx.car.app.ACCESS_SURFACE`, `androidx.car.app.MAP_TEMPLATES` permissions | Car-surface access |
| Launcher intent filters get `CAR_LAUNCHER`, `androidx.car.app.category.NAVIGATION`, `APP_MAPS` | Shows the app in Android Auto's launcher |
| `resizeableActivity=true` on launcher activities | Fits the car display |
| Removes intent filters for `androidx.car.app.CarAppService` (and media browser services unless *Keep media services*) | Stops Android Auto using its template UI instead of the app |
| *Fill the car screen*: removes portrait-only `screenOrientation` | Landscape car display |

**Car copy mode** also:

- renames the package to `<package>.car`, in the manifest and in the resource table's package name,
- rewrites relative class names to absolute names so they still point at the original classes,
- renames declared permissions, provider authorities and `sharedUserId` so they don't clash with the original,
- adds a badged adaptive icon and the label *Name (Car)*,
- applies the rename to every split.

**In-place mode** (*Car-enable before installing*) applies only the manifest changes and keeps the package name.

### Signing

Patched APKs lose their original signature. AA Installer:

1. strips the old v1 signature files and signing block,
2. zip-aligns (4 bytes; 16 KB for uncompressed native libraries),
3. signs with **apksig** (v2 + v3, and v1 when `minSdk < 24`) using an RSA-3072 key generated in the **Android Keystore** under the alias `aainstaller-car-signer`. The key never leaves the phone.
4. verifies every APK with `ApkVerifier` before installing.

Because the key lives in the Keystore, rebuilding a copy produces an update that installs over the old one. Clearing AA Installer's data deletes the key.

## Project layout

```
app/src/main/
  aidl/…/IShellService.aidl     shell service interface
  java/io/github/j0p3s/aainstaller/
    App.kt                      application class, crash reporter
    MainActivity.kt             activity, intents, Shizuku listeners
    Ui.kt                       Jetpack Compose / Material 3 screens
    AppModel.kt                 state, jobs, app lists
    Installer.java              bundle prep, pm session, AA detection, errors
    Shell.java                  Shizuku and root shells
    ShellService.java           runs in the shell process
  java/org/carbridge/           ported CarBridge car patch (GPL-3.0)
app/src/test/                   car-patch pipeline test + Roborazzi screenshots
```
