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
}

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

    var job by mutableStateOf<Job?>(null)
        internal set
    val history = mutableStateListOf<String>()

    var apps by mutableStateOf<List<AppEntry>?>(null)
        internal set

    val busy get() = job?.state == JobState.RUNNING || loading

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

    fun installPicked() {
        val p = picked ?: return
        val car = carEnable
        val opts = CarOptions(keepMedia, fillScreen)
        run(if (car) "Installing ${p.label} (car-enabled)" else "Installing ${p.label}", pickedIcon, p.packageName) { sh, j ->
            var files: List<File> = p.apks
            if (car) {
                step(j, "Applying the car patch")
                val splits = p.apks.filter { it != p.base }
                files = pipeline(j).inPlace(p.base, splits, opts)
            }
            step(j, "Installing as a Play Store app")
            Installer.install(sh, files) { log(j, it) }
            p.packageName
        }
    }

    fun retag(e: AppEntry) {
        run("Fixing ${e.label}", loadIcon(e.info), e.pkg) { sh, j ->
            step(j, "Re-registering with the Play Store as installer")
            Installer.retag(sh, e.info) { log(j, it) }
            e.pkg
        }
    }

    fun carCopy(e: AppEntry) {
        val opts = CarOptions(keepMedia, fillScreen)
        val carPkg = PatchPipeline.carPackageName(e.pkg)
        run("Car copy of ${e.label}", loadIcon(e.info), carPkg) { sh, j ->
            step(j, "Building ${e.label}${PatchPipeline.CAR_LABEL_SUFFIX}")
            val files = pipeline(j).carCopy(e.info, opts)
            step(j, "Installing as a Play Store app")
            Installer.install(sh, files) { log(j, it) }
            carPkg
        }
    }

    fun dismissJob() {
        if (job?.state != JobState.RUNNING) job = null
    }

    private fun pipeline(j: Job) = PatchPipeline(ctx) { log(j, "  $it") }

    private fun run(title: String, icon: ImageBitmap?, pkg: String, work: (Shell, Job) -> String) {
        if (busy) return
        val j = Job(title, icon)
        job = j
        history.add("\n=== $title ===")
        bg.execute {
            try {
                step(j, "Getting shell access")
                val sh = obtainShell(j)
                log(j, "Using ${sh.name()}")
                val installed = work(sh, j)
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
