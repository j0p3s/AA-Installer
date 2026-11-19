package org.carbridge.patch

import org.carbridge.core.CarOptions
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/** Merges real bundletool split APKs (dir in MERGE_IN), applies the parked patch, signs with a test key. */
class MergeJvmTest {
    @Test
    fun mergePatchSign() {
        val dir = File(System.getenv("MERGE_IN") ?: return)
        val out = File(System.getenv("MERGE_OUT")).apply { mkdirs() }
        val parts = listOf("base-master.apk", "base-en.apk", "base-pt.apk", "base-xxhdpi.apk", "base-arm64_v8a.apk")
            .map { File(dir, it) }.filter { it.exists() }
        val log = { s: String -> println("LOG $s") }
        val merged = File(out, "merged.unsigned.apk")
        SplitMerger.merge(parts, out, merged, log)
        val unsigned = File(out, "patched.unsigned.apk")
        val minSdk = PatchPipeline.rewrite(merged, unsigned) { apk ->
            ManifestPatcher(log).patch(apk, "io.github.j0p3s.aainstaller.car", ManifestPatcher.Branding("Test (Car)", null, null), CarOptions())
        }
        val ks = KeyStore.getInstance("PKCS12").apply { File(System.getenv("TEST_KS")).inputStream().use { load(it, "testpass".toCharArray()) } }
        val key = ks.getKey("t", "testpass".toCharArray()) as PrivateKey
        val cert = ks.getCertificate("t") as X509Certificate
        val signed = File(out, "car.apk")
        ApkSigning.sign(unsigned, signed, minSdk, key, cert)
        val problems = ApkSigning.verify(signed, minSdk, cert)
        println("RESULT parts=${parts.size} minSdk=$minSdk size=${signed.length()} problems=$problems")
        check(problems.isEmpty())
    }
}
