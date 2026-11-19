/*
 * Ported from CarBridge (https://github.com/jmwerk/CarBridge, commit 8fea836), GPL-3.0.
 * Changed for AA Installer: can also patch a picked APK/bundle in place (keeps the package
 * name, like CarBridge Patches for Morphe), merges splits into one APK first (ported from
 * Android Auto Maker, GPL-3.0), and returns the files for AA Installer's own Shizuku/root
 * install session or for saving.
 */
package org.carbridge.patch

import org.carbridge.core.CarOptions

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.storage.StorageManager
import com.reandroid.apk.ApkModule
import com.reandroid.archive.writer.ZipAligner
import java.io.File

/** A failure the user can act on; its message is shown as is. */
class PatchFailure(message: String) : Exception(message)

/** Original APK(s) in, signed car-enabled APK(s) out, base first. Runs off the main thread. */
class PatchPipeline(private val context: Context, private val log: (String) -> Unit) {

    /** CarBridge mode: a renamed, badged "App (Car)" copy that installs next to the installed original. */
    fun carCopy(app: ApplicationInfo, options: CarOptions): List<File> {
        val splits = app.splitSourceDirs.orEmpty().map(::File)
        val base = File(app.sourceDir)
        return patchFiles(base, splits, carPackageName(app.packageName), IconBadger.branding(context, app), options)
    }

    /** CarBridge Patches mode: same package name, so it replaces the app (uninstall first if signed differently). */
    fun inPlace(base: File, splits: List<File>, options: CarOptions): List<File> =
        patchFiles(base, splits, null, null, options)

    fun patchFiles(
        base: File,
        splits: List<File>,
        newPackage: String?,
        branding: ManifestPatcher.Branding?,
        options: CarOptions,
    ): List<File> {
        val work = workDir(context).apply { deleteRecursively(); mkdirs() }

        val total = (splits + base).sumOf { it.length() }
        log("Reading ${total / MB} MB in ${splits.size + 1} APK(s)")
        // Unsigned and signed copies sit side by side while signing
        val needed = total * (if (options.mergeSplits && splits.isNotEmpty()) 4 else 2) + 64 * MB
        val free = try {
            // Includes cache Android would clear to make room, unlike File.usableSpace
            val storage = context.getSystemService(StorageManager::class.java)
            storage.getAllocatableBytes(storage.getUuidForPath(work))
        } catch (e: Exception) {
            work.usableSpace
        }
        if (free < needed) {
            throw PatchFailure("Not enough free space: needs about ${needed / MB} MB, ${free / MB} MB free.")
        }

        var base = base
        var splits = splits
        if (options.mergeSplits && splits.isNotEmpty()) {
            log("Merging ${splits.size + 1} APKs into one")
            val merged = File(work, "merged.unsigned.apk")
            try {
                SplitMerger.merge(listOf(base) + splits, work, merged, log)
                base = merged
                splits = emptyList()
            } catch (e: Throwable) {
                merged.delete()
                log("Merging failed (${e.message ?: e.javaClass.simpleName}); keeping the split APKs")
            }
        }

        val patcher = ManifestPatcher(log)
        val unsignedBase = File(work, "base.unsigned.apk")
        val minSdk = rewrite(base, unsignedBase) { apk -> patcher.patch(apk, newPackage, branding, options) }
        if (base.name == "merged.unsigned.apk") base.delete()
        val unsignedSplits = splits.mapIndexed { i, split ->
            File(work, "split$i.unsigned.apk").also { out ->
                rewrite(split, out) { apk -> if (newPackage != null) patcher.patchSplit(apk, newPackage) }
            }
        }

        log("Signing")
        val signer = KeystoreSigner()
        val signed = (listOf(unsignedBase) + unsignedSplits).map { unsigned ->
            File(work, unsigned.name.replace(".unsigned", "")).also { out ->
                signer.sign(unsigned, out, minSdk)
                unsigned.delete()
            }
        }

        log("Verifying signatures")
        val cert = signer.certificate()
        val problems = signed.flatMap { apk -> ApkSigning.verify(apk, minSdk, cert).map { "${apk.name}: $it" } }
        if (problems.isNotEmpty()) {
            throw PatchFailure("The signed copy didn't pass Android's signature check.\n" + problems.joinToString("\n"))
        }
        log("Signed with ${ApkSigning.sha256(cert).take(17)}…")
        return signed
    }

    companion object {
        private const val MB = 1024L * 1024
        private val V1_SIGNATURE_FILES =
            Regex("META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC))", RegexOption.IGNORE_CASE).toPattern()

        const val CAR_LABEL_SUFFIX = " (Car)"

        @JvmStatic
        fun carPackageName(original: String) = "$original.car"

        private fun workDir(context: Context) = File(context.cacheDir, "patch")

        /** Frees the patched APKs, which can run to hundreds of MB, once installing has copied them. */
        @JvmStatic
        fun clearWork(context: Context) {
            workDir(context).deleteRecursively()
        }

        /** Applies [edit], writes an unsigned APK and returns its minSdk. */
        internal fun rewrite(input: File, output: File, edit: (ApkModule) -> Unit): Int =
            ApkModule.loadApkFile(input).use { apk ->
                apk.setApkSignatureBlock(null)
                apk.zipEntryMap.removeIf(V1_SIGNATURE_FILES)
                edit(apk)
                val writer = apk.createApkFileWriter(output)
                writer.zipAligner = ZipAligner().apply {
                    setDefaultAlignment(4)
                    // Uncompressed native libs must be page-aligned; 16 KB covers 4 KB devices too
                    setFileAlignment(ZipAligner.PREDICATE_NATIVE_LIBS, 16 * 1024)
                }
                writer.write()
                apk.androidManifest.minSdkVersion ?: 1
            }
    }
}
