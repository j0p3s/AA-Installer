/*
 * AA Installer - install Android Auto apps as Play Store apps
 * Copyright (C) 2026 j0p3s
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package io.github.j0p3s.aainstaller

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import org.carbridge.core.CarOptions
import org.carbridge.patch.KeystoreSigner
import org.carbridge.patch.PatchPipeline
import java.io.File
import java.util.concurrent.Executors

enum class Privilege { GRANTED, NEEDS_PERMISSION, NOT_RUNNING }

enum class JobState { RUNNING, SUCCESS, WARNING, FAILED }

/** One long operation shown in the progress sheet. */
class Job(val title: String, val icon: ImageBitmap?) {
    val log = mutableStateListOf<String>()
    var state by mutableStateOf(JobState.RUNNING)
    var step by mutableStateOf("Starting…")
    var message by mutableStateOf("")

    /** Set when the failure can be fixed by uninstalling the installed app first. */
    var retryWithUninstall by mutableStateOf<(() -> Unit)?>(null)

    /** The patched APK (or .apks bundle) kept in files/exports, to save or share. */
    var export by mutableStateOf<File?>(null)
}

/** Asks before uninstalling an app that can't be updated in place. */
class UninstallAsk(val label: String, val pkg: String, val reason: String, val proceed: () -> Unit)

/** A row in the app lists. */
data class AppEntry(
    val info: ApplicationInfo,
    val label: String,
    val pkg: String,
    val installer: String?,
    val androidAuto: Boolean,
    val launchable: Boolean,
    val system: Boolean,
    val hasCarCopy: Boolean,
) {
    val playTagged get() = installer == Installer.PLAY_STORE
}

class AppModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val bg = Executors.newSingleThreadExecutor()
    private val lists = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    var privilege by mutableStateOf(Privilege.NOT_RUNNING)
        internal set
    var picked by mutableStateOf<Installer.Package?>(null)
        internal set
    var pickedIcon by mutableStateOf<ImageBitmap?>(null)
        internal set
    var pickedInstaller by mutableStateOf<String?>(null)
        internal set
    var loading by mutableStateOf(false)
        internal set
    var pickError by mutableStateOf<String?>(null)
        internal set

    var carEnable by mutableStateOf(false)
    var fillScreen by mutableStateOf(true)
    var keepMedia by mutableStateOf(false)
    var mergeSplits by mutableStateOf(true)

    var job by mutableStateOf<Job?>(null)
        internal set
    val history = mutableStateListOf<String>()

    var apps by mutableStateOf<List<AppEntry>?>(null)
        internal set
    var askUninstall by mutableStateOf<UninstallAsk?>(null)
        internal set
    var checking by mutableStateOf(false)
        internal set
    var lastCrash by mutableStateOf<String?>(null)
        internal set

    val busy get() = job?.state == JobState.RUNNING || loading || checking

    fun loadCrash() {
        lastCrash = CrashLog.read(ctx)
    }

    fun clearCrash() {
        CrashLog.clear(ctx)
        lastCrash = null
    }

    fun refreshPrivilege() {
        privilege = when {
            Shell.shizukuGranted() -> Privilege.GRANTED
            Shell.shizukuRunning() -> Privilege.NEEDS_PERMISSION
            else -> Privilege.NOT_RUNNING
        }
    }

    // ------------------------------------------------------------------ lists

    fun refreshApps() {
        lists.execute {
            val pm = ctx.packageManager
            val all = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (e: Exception) {
                emptyList()
            }
            val pkgs = all.map { it.packageName }.toHashSet()
            val list = all.asSequence()
                .filter { it.packageName != ctx.packageName }
                .map { ai ->
                    AppEntry(
                        info = ai,
                        label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(ai.packageName),
                        pkg = ai.packageName,
                        installer = Installer.installerOf(ctx, ai.packageName),
                        androidAuto = Installer.isAndroidAuto(ai),
                        launchable = pm.getLaunchIntentForPackage(ai.packageName) != null,
                        system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        hasCarCopy = PatchPipeline.carPackageName(ai.packageName) in pkgs,
                    )
                }
                .sortedBy { it.label.lowercase() }
                .toList()
            main.post { apps = list }
        }
    }

    fun loadIcon(info: ApplicationInfo): ImageBitmap? =
        runCatching { ctx.packageManager.getApplicationIcon(info).toImageBitmap() }.getOrNull()

    // ---------------------------------------------------------------- picking

    fun load(uri: Uri) {
        if (busy) return
        loading = true
        pickError = null
        picked = null
        pickedIcon = null
        val name = displayName(uri)
        bg.execute {
            try {
                val p = Installer.prepare(ctx, uri, name) { line -> post { history.add(line) } }
                val icon = p.icon?.toImageBitmap()
                val inst = if (isInstalled(p.packageName)) Installer.installerOf(ctx, p.packageName) ?: "unknown" else null
                post {
                    picked = p
                    pickedIcon = icon
                    pickedInstaller = inst
                    loading = false
                }
            } catch (e: Throwable) {
                post {
                    pickError = e.message ?: e.toString()
                    loading = false
                }
            }
        }
    }

    fun clearPicked() {
        picked = null
        pickedIcon = null
        pickError = null
    }

    private fun displayName(u: Uri): String? = try {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: u.lastPathSegment

    // ------------------------------------------------------------- operations

    /** Checks whether the installed version can be updated; asks to uninstall it first if not. */
    fun installPicked() {
        val p = picked ?: return
        if (busy) return
        val car = carEnable
        checking = true
        bg.execute {
            val reason = updateBlocker(p, car)
            post {
                checking = false
                if (reason == null) doInstall(p, car, uninstallFirst = false)
                else askUninstall = UninstallAsk(p.label, p.packageName, reason) { doInstall(p, car, uninstallFirst = true) }
            }
        }
    }

    /** Why Android would refuse to update the installed app with this file, or null if it would accept it. */
    private fun updateBlocker(p: Installer.Package, car: Boolean): String? {
        if (!isInstalled(p.packageName)) return null
        val pm = ctx.packageManager
        val installedCode = runCatching {
            val pi = pm.getPackageInfo(p.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
        }.getOrDefault(-1L)
        if (p.versionCode in 0 until installedCode) {
            return "A newer version (${installedCode}) is installed than the one you picked (${p.versionCode}). Android can't downgrade it."
        }
        val sameKey = if (car) runCatching { KeystoreSigner().signedHere(pm, p.packageName) }.getOrDefault(true)
        else Installer.sameSigner(ctx, p.base, p.packageName) ?: true
        if (!sameKey) {
            return if (car) "The installed ${p.label} is signed with its original key. A car-enabled APK is re-signed on this phone, so Android can't update it."
            else "The installed ${p.label} is signed with a different key than this APK, so Android can't update it."
        }
        return null
    }

    fun confirmUninstall() {
        val a = askUninstall ?: return
        askUninstall = null
        a.proceed()
    }

    fun cancelUninstall() {
        askUninstall = null
    }

    private fun options() = CarOptions(keepMediaServices = keepMedia, fillScreen = fillScreen, mergeSplits = mergeSplits)

    private fun doInstall(p: Installer.Package, car: Boolean, uninstallFirst: Boolean) {
        val opts = options()
        run(
            if (car) "Installing ${p.label} (car-enabled)" else "Installing ${p.label}", pickedIcon, p.packageName,
            retry = if (uninstallFirst) null else ({ doInstall(p, car, uninstallFirst = true) }),
        ) { shell, j ->
            var files: List<File> = p.apks
            if (car) {
                step(j, "Applying the car patch")
                val splits = p.apks.filter { it != p.base }
                files = pipeline(j).inPlace(p.base, splits, opts)
                keepExport(j, files, "${p.label}-car")
            }
            val sh = shell.value
            if (uninstallFirst && isInstalled(p.packageName)) {
                step(j, "Uninstalling the old ${p.label}")
                Installer.uninstall(sh, p.packageName) { log(j, it) }
            }
            step(j, "Installing as a Play Store app")
            Installer.install(sh, files) { log(j, it) }
            p.packageName
        }
    }

    /** Applies the car patch to the picked file and keeps the result to save or share, without installing. */
    fun savePickedCarEnabled() {
        val p = picked ?: return
        val opts = options()
        run("Car-enabling ${p.label}", pickedIcon, p.packageName, retry = null) { _, j ->
            step(j, "Applying the car patch")
            val files = pipeline(j).inPlace(p.base, p.apks.filter { it != p.base }, opts)
            keepExport(j, files, "${p.label}-car")
            null
        }
    }

    fun retag(e: AppEntry) {
        run("Fixing ${e.label}", loadIcon(e.info), e.pkg, retry = null) { shell, j ->
            step(j, "Re-registering with the Play Store as installer")
            Installer.retag(shell.value, e.info) { log(j, it) }
            e.pkg
        }
    }

    fun carCopy(e: AppEntry, install: Boolean = true) {
        if (busy) return
        if (!install) {
            doCarCopy(e, uninstallFirst = false, install = false)
            return
        }
        val carPkg = PatchPipeline.carPackageName(e.pkg)
        val stale = isInstalled(carPkg) &&
            !runCatching { KeystoreSigner().signedHere(ctx.packageManager, carPkg) }.getOrDefault(true)
        if (stale) {
            askUninstall = UninstallAsk(
                "${e.label}${PatchPipeline.CAR_LABEL_SUFFIX}", carPkg,
                "The existing car copy was signed with an older key (AA Installer was reinstalled or its data cleared), so it can't be updated.",
            ) { doCarCopy(e, uninstallFirst = true, install = true) }
        } else {
            doCarCopy(e, uninstallFirst = false, install = true)
        }
    }

    private fun doCarCopy(e: AppEntry, uninstallFirst: Boolean, install: Boolean) {
        val opts = options()
        val carPkg = PatchPipeline.carPackageName(e.pkg)
        val carLabel = "${e.label}${PatchPipeline.CAR_LABEL_SUFFIX}"
        run(
            "Car copy of ${e.label}", loadIcon(e.info), carPkg,
            retry = if (uninstallFirst || !install) null else ({ doCarCopy(e, uninstallFirst = true, install = true) }),
        ) { shell, j ->
            step(j, "Building $carLabel")
            val files = pipeline(j).carCopy(e.info, opts)
            keepExport(j, files, "${e.label}-car-copy")
            if (!install) return@run null
            val sh = shell.value
            if (uninstallFirst && isInstalled(carPkg)) {
                step(j, "Uninstalling the old $carLabel")
                Installer.uninstall(sh, carPkg) { log(j, it) }
            }
            step(j, "Installing as a Play Store app")
            Installer.install(sh, files) { log(j, it) }
            carPkg
        }
    }

    fun dismissJob() {
        if (job?.state != JobState.RUNNING) job = null
    }

    /** Copies the signed result to files/exports: one .apk, or an .apks bundle if the splits weren't merged. */
    private fun keepExport(j: Job, files: List<File>, baseName: String) {
        val dir = ExportProvider.dir(ctx)
        dir.listFiles()?.forEach { it.delete() }
        val safe = baseName.replace(Regex("[^A-Za-z0-9._ ()-]"), "_").trim().ifEmpty { "app-car" }
        val out = if (files.size == 1) {
            File(dir, "$safe.apk").also { files[0].copyTo(it, overwrite = true) }
        } else {
            File(dir, "$safe.apks").also { zip ->
                java.util.zip.ZipOutputStream(zip.outputStream().buffered()).use { z ->
                    for (f in files) {
                        z.putNextEntry(java.util.zip.ZipEntry(f.name))
                        f.inputStream().use { it.copyTo(z) }
                        z.closeEntry()
                    }
                }
            }
        }
        log(j, "Kept ${out.name} (${Installer.human(out.length())}) to save or share")
        post { j.export = out }
    }

    /** Copies the last export to a location the user picked. */
    fun saveExport(target: Uri, done: (Boolean) -> Unit) {
        val f = job?.export ?: return
        bg.execute {
            val ok = try {
                ctx.contentResolver.openOutputStream(target)?.use { out -> f.inputStream().use { it.copyTo(out) } } != null
            } catch (e: Exception) {
                false
            }
            post { done(ok) }
        }
    }

    private fun pipeline(j: Job) = PatchPipeline(ctx) { log(j, "  $it") }

    /** [work] returns the installed package to verify, or null when the result was only kept for saving. */
    private fun run(title: String, icon: ImageBitmap?, pkg: String, retry: (() -> Unit)?, work: (Lazy<Shell>, Job) -> String?) {
        if (job?.state == JobState.RUNNING || loading) return
        val j = Job(title, icon)
        job = j
        history.add("\n=== $title ===")
        bg.execute {
            try {
                val shell = lazy {
                    step(j, "Getting shell access")
                    obtainShell(j).also { log(j, "Using ${it.name()}") }
                }
                val installed = work(shell, j)
                if (installed == null) {
                    post {
                        j.state = JobState.SUCCESS
                        j.step = "Ready to save"
                        j.message = "Save the APK or share it. Uninstall the original app before installing it, because it is signed with a different key."
                    }
                    return@execute
                }
                val inst = Installer.installerOf(ctx, installed)
                val ok = inst == Installer.PLAY_STORE
                log(j, if (ok) "✅ Installer is now $inst" else "⚠️ Installer reads as $inst")
                post {
                    j.state = if (ok) JobState.SUCCESS else JobState.WARNING
                    j.step = if (ok) "Done" else "Installed, but check the installer"
                    j.message = if (ok) "Registered as a Play Store install. Reconnect to Android Auto to see it."
                    else "Android reports the installer as '$inst'."
                    if (picked?.packageName == pkg) pickedInstaller = inst
                }
                refreshApps()
            } catch (e: Throwable) {
                val msg = e.message ?: e.toString()
                log(j, "❌ $msg")
                post {
                    j.state = JobState.FAILED
                    j.step = "Failed"
                    j.message = msg
                    if (e is Installer.NeedsUninstallException && retry != null) {
                        j.retryWithUninstall = {
                            job = null
                            retry()
                        }
                    }
                }
            } finally {
                PatchPipeline.clearWork(ctx)
            }
        }
    }

    private fun obtainShell(j: Job): Shell {
        if (Shell.shizukuGranted()) return Shell.ShizukuShell.get()
        log(j, "Shizuku not available, trying root…")
        if (Shell.RootShell.available()) return Shell.RootShell()
        throw IllegalStateException("No shell access. Start Shizuku and allow AA Installer, or grant root.")
    }

    private fun step(j: Job, s: String) = post {
        j.step = s
        j.log.add("▶ $s")
        history.add("▶ $s")
    }

    private fun log(j: Job, s: String?) {
        if (s.isNullOrBlank()) return
        post {
            j.log.add(s)
            history.add(s)
        }
    }

    private fun post(r: () -> Unit) {
        main.post(r)
    }

    fun isInstalled(pkg: String) = try {
        ctx.packageManager.getApplicationInfo(pkg, 0); true
    } catch (e: Exception) {
        false
    }

    override fun onCleared() {
        bg.shutdown()
        lists.shutdown()
    }
}

fun Drawable.toImageBitmap(size: Int = 144): ImageBitmap {
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val old = copyBounds()
    setBounds(0, 0, size, size)
    draw(c)
    bounds = old
    return b.asImageBitmap()
}
