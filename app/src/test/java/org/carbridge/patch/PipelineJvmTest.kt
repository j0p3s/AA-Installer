package org.carbridge.patch

import org.carbridge.core.CarOptions
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate

/** Runs the real patch + sign path on a real APK given by -Dcar.in, writes to -Dcar.out. */
class PipelineJvmTest {
    @Test
    fun patchAndSign() {
        val input = File(System.getenv("CAR_IN") ?: return)
        val outDir = File(System.getenv("CAR_OUT")).apply { mkdirs() }
        val ks = KeyStore.getInstance("PKCS12").apply {
            File(System.getenv("CAR_KS")).inputStream().use { load(it, System.getenv("CAR_PASS").toCharArray()) }
        }
        val key = ks.getKey("aainstaller", System.getenv("CAR_PASS").toCharArray()) as PrivateKey
        val cert = ks.getCertificate("aainstaller") as X509Certificate
        val png = javaClass.getResourceAsStream("/dot.png")?.readBytes()
        val log = { s: String -> println("LOG " + s) }

        for ((mode, newPkg) in listOf("copy" to "${System.getenv("CAR_PKG")}.car", "inplace" to null)) {
            val unsigned = File(outDir, "$mode.unsigned.apk")
            val branding = if (newPkg != null) ManifestPatcher.Branding("Test (Car)", png, png) else null
            val minSdk = PatchPipeline.rewrite(input, unsigned) { apk ->
                ManifestPatcher(log).patch(apk, newPkg, branding, CarOptions(false, true))
            }
            val signed = File(outDir, "$mode.apk")
            ApkSigning.sign(unsigned, signed, minSdk, key, cert)
            val problems = ApkSigning.verify(signed, minSdk, cert)
            println("RESULT $mode minSdk=$minSdk problems=$problems")
            check(problems.isEmpty())
        }
    }
}
