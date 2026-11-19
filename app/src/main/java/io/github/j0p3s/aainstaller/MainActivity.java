package io.github.j0p3s.aainstaller;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1;
    private static final int REQ_SHIZUKU = 42;
    private static final String AA_PKG = "com.google.android.projection.gearhead";
    private static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView status, info, log;
    private Button shizukuBtn, installBtn, pickBtn, fixBtn;
    private ScrollView scroll;
    private Installer.Package picked;
    private boolean busy;

    private final Shizuku.OnBinderReceivedListener binderReceived = () -> ui.post(this::refreshStatus);
    private final Shizuku.OnBinderDeadListener binderDead = () -> ui.post(this::refreshStatus);
    private final Shizuku.OnRequestPermissionResultListener permResult = (code, result) -> ui.post(this::refreshStatus);

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        Shizuku.addRequestPermissionResultListener(permResult);
        refreshStatus();
        handleIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        Shizuku.removeRequestPermissionResultListener(permResult);
        bg.shutdown();
        super.onDestroy();
    }

    private void handleIntent(Intent i) {
        if (i == null) return;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u != null) load(u);
    }

    // ------------------------------------------------------------------------ UI

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private void buildUi() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom, left, right;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = s.top; bottom = s.bottom; left = s.left; right = s.right;
            } else {
                //noinspection deprecation
                top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom();
                //noinspection deprecation
                left = insets.getSystemWindowInsetLeft(); right = insets.getSystemWindowInsetRight();
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });

        TextView title = new TextView(this);
        title.setText(getString(R.string.app_name) + "  v" + BuildConfig.VERSION_NAME);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(R.string.subtitle);
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub);

        status = card(root);
        shizukuBtn = button(root, "", v -> onShizukuButton());

        section(root, "1. Install an Android Auto app");
        pickBtn = button(root, "Select APK / APKS / XAPK / APKM", v -> pick());
        info = card(root);
        info.setText("No file selected.");
        installBtn = button(root, "Install as Play Store app", v -> install());
        installBtn.setEnabled(false);

        section(root, "2. Fix an app that is already installed");
        fixBtn = button(root, "Choose installed app…", v -> chooseInstalled());

        section(root, "3. Android Auto setup");
        button(root, "Open Android Auto settings", v -> openAndroidAuto());
        button(root, "Android Auto app info (battery → Unrestricted)", v -> openAppInfo(AA_PKG));

        section(root, "Log");
        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(12);
        log.setTextIsSelectable(true);
        log.setPadding(dp(8), dp(8), dp(8), dp(8));
        log.setBackgroundColor(0x22808080);
        root.addView(log, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
    }

    private TextView card(LinearLayout root) {
        TextView t = new TextView(this);
        t.setPadding(dp(12), dp(10), dp(12), dp(10));
        t.setBackgroundColor(0x1A808080);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(4));
        root.addView(t, lp);
        return t;
    }

    private void section(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(16);
        t.setPadding(0, dp(18), 0, dp(4));
        root.addView(t);
    }

    private Button button(LinearLayout root, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(l);
        root.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private void logLine(String s) {
        if (s == null || s.isEmpty()) return;
        ui.post(() -> {
            log.append(s + "\n");
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        pickBtn.setEnabled(!b);
        fixBtn.setEnabled(!b);
        installBtn.setEnabled(!b && picked != null);
    }

    // -------------------------------------------------------------- privileges

    private void refreshStatus() {
        if (status == null) return;
        boolean running = Shell.shizukuRunning();
        boolean granted = Shell.shizukuGranted();
        if (granted) {
            status.setText("✅ Shizuku is running and allowed.\nInstalls will be registered as coming from the Play Store.");
            shizukuBtn.setVisibility(View.GONE);
        } else if (running) {
            status.setText("⚠️ Shizuku is running but AA Installer is not allowed yet.");
            shizukuBtn.setText("Allow Shizuku access");
            shizukuBtn.setVisibility(View.VISIBLE);
        } else {
            status.setText("❌ Shizuku is not running.\nStart Shizuku (via Wireless debugging) — or, if your phone is rooted, root will be used automatically.");
            shizukuBtn.setText(isInstalled(SHIZUKU_PKG) ? "Open Shizuku" : "Get Shizuku");
            shizukuBtn.setVisibility(View.VISIBLE);
        }
    }

    private void onShizukuButton() {
        if (Shell.shizukuRunning()) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                toast("Permission was denied permanently — allow AA Installer inside the Shizuku app.");
                openPackage(SHIZUKU_PKG);
            } else {
                Shizuku.requestPermission(REQ_SHIZUKU);
            }
        } else if (isInstalled(SHIZUKU_PKG)) {
            openPackage(SHIZUKU_PKG);
        } else {
            openUrl("https://github.com/RikkaApps/Shizuku/releases");
        }
    }

    /** Runs on background thread. */
    private Shell obtainShell() throws Exception {
        if (Shell.shizukuGranted()) return Shell.ShizukuShell.get();
        logLine("Shizuku not available, trying root…");
        if (Shell.RootShell.available()) return new Shell.RootShell();
        throw new IllegalStateException("No privileges: start Shizuku and allow AA Installer (or grant root).");
    }

    // ---------------------------------------------------------------- picking

    private void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.android.package-archive", "application/octet-stream",
                "application/zip", "application/x-zip-compressed", "application/vnd.apkm"});
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (ActivityNotFoundException e) {
            toast("No file picker available");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null && data.getData() != null) load(data.getData());
    }

    private String displayName(Uri u) {
        try (Cursor c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        }
        return u.getLastPathSegment();
    }

    private void load(Uri uri) {
        if (busy) return;
        picked = null;
        setBusy(true);
        info.setText("Reading…");
        String name = displayName(uri);
        bg.execute(() -> {
            try {
                Installer.Package p = Installer.prepare(this, uri, name, this::logLine);
                ui.post(() -> {
                    picked = p;
                    showInfo(p);
                    setBusy(false);
                });
            } catch (Exception e) {
                logLine("ERROR: " + e.getMessage());
                ui.post(() -> {
                    info.setText("❌ " + e.getMessage());
                    setBusy(false);
                });
            }
        });
    }

    private void showInfo(Installer.Package p) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.label).append('\n')
                .append(p.packageName).append('\n')
                .append("Version ").append(p.version);
        if (p.apks.size() > 1) sb.append("  •  ").append(p.apks.size()).append(" parts");
        sb.append("\n\n");
        sb.append(p.androidAuto ? "✅ Android Auto app — " : "⚠️ Not an Android Auto app — ").append(p.aaReason);
        if (!p.androidAuto)
            sb.append("\nIt will install, but it will not show up in Android Auto.");
        String inst = Installer.installerOf(this, p.packageName);
        if (isInstalled(p.packageName))
            sb.append("\n\nAlready installed (installer: ").append(inst == null ? "unknown" : inst).append("). It will be updated, data is kept.");
        info.setText(sb);
    }

    // -------------------------------------------------------------- installing

    private void install() {
        if (picked == null || busy) return;
        Installer.Package p = picked;
        setBusy(true);
        logLine("\n=== Installing " + p.label + " ===");
        bg.execute(() -> {
            try {
                Shell sh = obtainShell();
                logLine("Using " + sh.name());
                Installer.install(sh, p.apks, this::logLine);
                done(p.packageName, p.label);
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    private void chooseInstalled() {
        if (busy) return;
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = new ArrayList<>();
        List<ApplicationInfo> all;
        try {
            all = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        } catch (Exception e) {
            toast("Cannot list apps: " + e.getMessage());
            return;
        }
        for (ApplicationInfo ai : all) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (ai.packageName.equals(getPackageName())) continue;
            if (Installer.isAndroidAuto(ai)) apps.add(ai);
        }
        if (apps.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No Android Auto apps found")
                    .setMessage("None of your installed (non-system) apps declare Android Auto support.")
                    .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        Collections.sort(apps, (a, b) -> String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
        String[] items = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            ApplicationInfo ai = apps.get(i);
            String inst = Installer.installerOf(this, ai.packageName);
            boolean ok = Installer.PLAY_STORE.equals(inst);
            items[i] = (ok ? "✅ " : "⚠️ ") + ai.loadLabel(pm) + "\n" + ai.packageName
                    + "\ninstaller: " + (inst == null ? "none" : inst);
        }
        new AlertDialog.Builder(this)
                .setTitle("Mark as installed from Play Store")
                .setItems(items, (d, which) -> retag(apps.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void retag(ApplicationInfo ai) {
        String label = String.valueOf(ai.loadLabel(getPackageManager()));
        setBusy(true);
        logLine("\n=== Fixing " + label + " ===");
        bg.execute(() -> {
            try {
                Shell sh = obtainShell();
                logLine("Using " + sh.name());
                Installer.retag(sh, ai, this::logLine);
                done(ai.packageName, label);
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    private void done(String pkg, String label) {
        String inst = Installer.installerOf(this, pkg);
        boolean ok = Installer.PLAY_STORE.equals(inst);
        logLine(ok ? "✅ Done. Installer is now " + inst
                : "⚠️ Installed, but installer reads as: " + inst);
        ui.post(() -> {
            setBusy(false);
            if (picked != null && pkg.equals(picked.packageName)) showInfo(picked);
            new AlertDialog.Builder(this)
                    .setTitle(ok ? "Installed" : "Installed (check)")
                    .setMessage(label + (ok
                            ? " is now registered as a Play Store install.\n\nIn Android Auto developer settings make sure 'Unknown sources' is on, then reconnect to the car."
                            : " installed, but Android reports the installer as '" + inst + "'."))
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton("Android Auto settings", (d, w) -> openAndroidAuto())
                    .show();
        });
    }

    private void fail(Exception e) {
        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
        logLine("❌ " + msg);
        ui.post(() -> {
            setBusy(false);
            new AlertDialog.Builder(this)
                    .setTitle("Failed")
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        });
    }

    // ----------------------------------------------------------------- helpers

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openPackage(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) startActivity(i);
        else openAppInfo(pkg);
    }

    private void openAndroidAuto() {
        if (!isInstalled(AA_PKG)) {
            toast("Android Auto is not installed");
            return;
        }
        // Android 10+: Android Auto has no launcher icon; its settings live in the AA app itself.
        String[] components = {
                AA_PKG + "/com.google.android.apps.auto.carservice.service.impl.CarServiceSettingsActivity",
                AA_PKG + "/com.google.android.projection.gearhead.companion.settings.SettingsActivity",
        };
        for (String c : components) {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setComponent(android.content.ComponentName.unflattenFromString(c));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                toast("Tap the version number 10× to unlock developer settings → enable 'Unknown sources'");
                return;
            } catch (Exception ignored) {
            }
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(AA_PKG);
        if (launch != null) {
            startActivity(launch);
        } else {
            toast("Open Settings → Connected devices → Android Auto");
            try {
                startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            } catch (Exception e) {
                openAppInfo(AA_PKG);
            }
        }
    }

    private void openAppInfo(String pkg) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
        } catch (Exception e) {
            toast("Cannot open app info");
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(url);
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
