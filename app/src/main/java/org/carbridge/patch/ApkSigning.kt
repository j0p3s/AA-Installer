/*
 * Ported from CarBridge (https://github.com/jmwerk/CarBridge, commit 8fea836), GPL-3.0.
 * Used in AA Installer under the same licence.
 */
package org.carbridge.patch

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate

/** Signs and verifies APKs with any key, so the phone's Keystore key and a test key share one path. */
object ApkSigning {
    private const val PAGE_16K = 16 * 1024

    fun sign(input: File, output: File, minSdk: Int, key: PrivateKey, cert: X509Certificate) {
        val signer = ApkSigner.SignerConfig.Builder("AAINSTALLER", key, listOf(cert)).build()
        ApkSigner.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(minSdk)
            .setV1SigningEnabled(minSdk < 24)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setLibraryPageAlignmentBytes(PAGE_16K)
            .setCreatedBy("AA Installer (CarBridge)")
            .build()
            .sign()
    }

    /** Problems Android's installer would find in [apk]; empty when it verifies and [cert] signed it. */
    fun verify(apk: File, minSdk: Int, cert: X509Certificate): List<String> {
        val result = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(minSdk).build().verify()
        val problems = (result.errors + result.v2SchemeSigners.flatMap { it.errors } + result.v3SchemeSigners.flatMap { it.errors })
            .map { it.toString() }
            .toMutableList()
        val signer = result.signerCertificates.firstOrNull()
        if (signer != null && !signer.encoded.contentEquals(cert.encoded)) problems += "signed with an unexpected certificate"
        if (!result.isVerified && problems.isEmpty()) problems += "signature did not verify"
        return problems
    }

    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02x".format(it) }
}
