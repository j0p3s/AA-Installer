package io.github.j0p3s.aainstaller;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Installs APKs through "pm install-create/-write/-commit" with the Play Store as installer of record. */
public final class Installer {

    public static final String PLAY_STORE = "com.android.vending";
    private static final Pattern SESSION_ID = Pattern.compile("\\[(\\d+)]");
    private static final List<String> KNOWN_ABIS = Arrays.asList(
            "arm64_v8a", "armeabi_v7a", "armeabi", "x86", "x86_64", "mips", "mips64", "riscv64");

    public interface Log {
        void line(String s);
    }

    /** What was picked: one APK or the APKs inside an .apks/.xapk/.apkm bundle. */
    public static final class Package {
        public final List<File> apks = new ArrayList<>();
        public File base;
        public String label = "?";
        public String packageName = "?";
        public String version = "?";
        public int targetSdk;
        public boolean androidAuto;
        public android.graphics.drawable.Drawable icon;
        public String aaReason = "";
    }

    private Installer() {
    }

    // --------------------------------------------------------------- preparing

    public static Package prepare(Context ctx, Uri uri, String displayName, Log log) throws Exception {
        File work = new File(ctx.getCacheDir(), "work");
        deleteRecursive(work);
        if (!work.mkdirs()) throw new IllegalStateException("Cannot create cache dir");

        File input = new File(work, "input.bin");
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(input)) {
            if (in == null) throw new IllegalStateException("Cannot open the selected file");
            copy(in, out);
        }
        log.line("Copied " + (displayName == null ? "file" : displayName) + " (" + human(input.length()) + ")");

        Package p = new Package();
        boolean isApk;
        try (ZipFile zip = new ZipFile(input)) {
            isApk = zip.getEntry("AndroidManifest.xml") != null;
            if (!isApk) {
                File parts = new File(work, "parts");
                parts.mkdirs();
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory() || !e.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                    String n = sanitize(new File(e.getName()).getName());
                    if (!splitFitsDevice(n)) {
                        log.line("  skip " + n + " (other CPU architecture)");
                        continue;
                    }
                    File f = new File(parts, n);
                    try (InputStream in = zip.getInputStream(e); OutputStream out = new FileOutputStream(f)) {
                        copy(in, out);
                    }
                    p.apks.add(f);
                }
            }
        } catch (java.util.zip.ZipException ze) {
            throw new IllegalStateException("This is not an APK or an APK bundle (.apks/.xapk/.apkm)");
        }

        if (isApk) {
            File f = new File(work, "base.apk");
            if (!input.renameTo(f)) throw new IllegalStateException("Cannot rename input");
            p.apks.add(f);
        } else {
            input.delete();
            if (p.apks.isEmpty()) throw new IllegalStateException("No APK files found inside this bundle");
        }

        p.base = pickBase(p.apks);
        inspect(ctx, p);
        log.line("Parts: " + p.apks.size() + (p.apks.size() > 1 ? " (split APK)" : ""));
        return p;
    }

    private static boolean splitFitsDevice(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace('-', '_');
        for (String abi : KNOWN_ABIS) {
            if (n.contains("config." + abi + ".") || n.endsWith("." + abi + ".apk") || n.equals(abi + ".apk")) {
                for (String dev : Build.SUPPORTED_ABIS)
                    if (dev.toLowerCase(Locale.ROOT).replace('-', '_').equals(abi)) return true;
                return false;
            }
        }
        return true;
    }

    private static File pickBase(List<File> apks) {
        for (File f : apks) if (f.getName().equalsIgnoreCase("base.apk")) return f;
        File best = null;
        for (File f : apks) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.startsWith("split_") || n.startsWith("config.")) continue;
            if (best == null || f.length() > best.length()) best = f;
        }
        if (best != null) return best;
        for (File f : apks) if (best == null || f.length() > best.length()) best = f;
        return best;
    }

    private static void inspect(Context ctx, Package p) {
        PackageManager pm = ctx.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(p.base.getAbsolutePath(), PackageManager.GET_META_DATA);
        if (info == null || info.applicationInfo == null) return;
        ApplicationInfo ai = info.applicationInfo;
        ai.sourceDir = p.base.getAbsolutePath();
        ai.publicSourceDir = p.base.getAbsolutePath();
        p.packageName = info.packageName;
        p.version = info.versionName + " (" + (Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode) + ")";
        p.targetSdk = ai.targetSdkVersion;
        try {
            p.icon = ai.loadIcon(pm);
        } catch (Throwable ignored) {
        }
        try {
            p.label = String.valueOf(ai.loadLabel(pm));
        } catch (Throwable t) {
            p.label = info.packageName;
        }
        checkAndroidAuto(ai.metaData, p);
    }

    static void checkAndroidAuto(Bundle md, Package p) {
        p.androidAuto = false;
        if (md == null) {
            p.aaReason = "no Android Auto metadata found";
            return;
        }
        if (md.containsKey("com.google.android.gms.car.application")) {
            p.androidAuto = true;
            p.aaReason = "declares com.google.android.gms.car.application";
        } else if (md.containsKey("androidx.car.app.minCarApiLevel")) {
            p.androidAuto = true;
            p.aaReason = "uses the Android for Cars App Library";
        } else {
            p.aaReason = "no Android Auto metadata found";
        }
    }

    public static boolean isAndroidAuto(ApplicationInfo ai) {
        Package tmp = new Package();
        checkAndroidAuto(ai.metaData, tmp);
        return tmp.androidAuto;
    }

    // --------------------------------------------------------------- installing

    /** Equivalent of: adb install-multiple -r -i com.android.vending <apks...> */
    public static void install(Shell sh, List<File> apks, Log log) throws Exception {
        long total = 0;
        for (File f : apks) total += f.length();

        List<String> create = new ArrayList<>(Arrays.asList(
                "pm", "install-create", "-r", "-i", PLAY_STORE, "--user", "current", "-S", String.valueOf(total)));
        if (Build.VERSION.SDK_INT >= 34) create.add("--bypass-low-target-sdk-block");

        log.line("$ " + String.join(" ", create));
        Shell.Result r = sh.run(create.toArray(new String[0]), null);
        if (!r.ok() && Build.VERSION.SDK_INT >= 34 && r.out.contains("bypass-low-target-sdk-block")) {
            create.remove("--bypass-low-target-sdk-block");
            r = sh.run(create.toArray(new String[0]), null);
        }
        log.line(r.out);
        Matcher m = SESSION_ID.matcher(r.out);
        if (!r.ok() || !m.find()) throw new IllegalStateException("Could not create install session");
        String id = m.group(1);

        try {
            int i = 0;
            for (File f : apks) {
                String name = (i++ == 0 && apks.size() == 1) ? "base.apk" : sanitize(f.getName());
                String[] w = {"pm", "install-write", "-S", String.valueOf(f.length()), id, name, "-"};
                log.line("$ pm install-write " + name + " (" + human(f.length()) + ")");
                try (InputStream in = new FileInputStream(f)) {
                    r = sh.run(w, in);
                }
                if (!r.ok()) {
                    log.line(r.out);
                    throw new IllegalStateException("Writing " + name + " failed");
                }
            }
            log.line("$ pm install-commit " + id);
            r = sh.run("pm", "install-commit", id);
            log.line(r.out);
            if (!r.ok() || !r.out.contains("Success")) throw new IllegalStateException(explain(r.out));
        } catch (Exception e) {
            try {
                sh.run("pm", "install-abandon", id);
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    /** Re-installs an already installed app from its own APK files so the Play Store becomes its installer. Data is kept. */
    public static void retag(Shell sh, ApplicationInfo ai, Log log) throws Exception {
        List<File> files = new ArrayList<>();
        files.add(new File(ai.sourceDir));
        if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) files.add(new File(s));
        for (File f : files) if (!f.canRead()) throw new IllegalStateException("Cannot read " + f);
        install(sh, files, log);
    }

    public static String installerOf(Context ctx, String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            if (Build.VERSION.SDK_INT >= 30) {
                InstallSourceInfo s = pm.getInstallSourceInfo(pkg);
                return s.getInstallingPackageName();
            }
            //noinspection deprecation
            return pm.getInstallerPackageName(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    static String explain(String out) {
        if (out.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
            return "Signature differs from the installed version (a car-enabled APK is re-signed on this phone). Uninstall the installed app first, then try again.";
        if (out.contains("INSTALL_FAILED_VERSION_DOWNGRADE"))
            return "A newer version is already installed. Uninstall it first.";
        if (out.contains("INSTALL_FAILED_NO_MATCHING_ABIS"))
            return "This APK is not built for this phone's CPU.";
        if (out.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION"))
            return "Android refuses apps with this old target SDK.";
        if (out.contains("INSTALL_FAILED_MISSING_SPLIT"))
            return "Split APK parts are missing. Pick the full .apks/.xapk bundle.";
        if (out.contains("INSTALL_FAILED_USER_RESTRICTED"))
            return "Installs via USB/shell are blocked. On RedMagic/nubia: Developer options → enable 'Install via USB' (and 'USB debugging (Security settings)' if present).";
        return "Install failed: " + out;
    }

    // ------------------------------------------------------------------- utils

    static String sanitize(String s) {
        String r = s.replaceAll("[^A-Za-z0-9._-]", "_");
        return r.isEmpty() ? "part.apk" : r;
    }

    static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    static String human(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", b / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", b / (1024.0 * 1024));
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
