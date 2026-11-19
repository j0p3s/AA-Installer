# Security

## What AA Installer can do

AA Installer runs install commands with **shell (adb) or root rights** through Shizuku or `su`. It only runs `pm install-create`, `install-write`, `install-commit`, `install-abandon`, and `pm uninstall` after you confirm it in a dialog (plus `id` to detect root). It has **no internet permission**, collects nothing and sends nothing anywhere.

Car-patched APKs are signed with a key generated in the phone's **Android Keystore**. The key can't be exported and never leaves the device.

## Verify your download

Only download AA Installer from this repository's [Releases](https://github.com/j0p3s/AA-Installer/releases). The release signing certificate is:

```
SHA-256: 5E:FA:6B:83:CC:9A:A8:8F:92:E8:F3:0B:21:6C:51:00:1E:B0:B6:9C:40:E2:A9:4B:E9:57:2D:83:4E:3A:DB:92
```

Check it with [AppVerifier](https://github.com/soupslurpr/AppVerifier) or `apksigner verify --print-certs`.

## Your responsibility

AA Installer installs whatever APK you give it with elevated rights. **Only install APKs from sources you trust.**

## Reporting a vulnerability

Please report security problems privately through GitHub's **[Report a vulnerability](https://github.com/j0p3s/AA-Installer/security/advisories/new)** form rather than in a public issue.
