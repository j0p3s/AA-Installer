/*
 * Ported from CarBridge (https://github.com/jmwerk/CarBridge, commit 8fea836), GPL-3.0.
 * Used in AA Installer under the same licence.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package org.carbridge.patch

import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.security.auth.x500.X500Principal

/**
 * Ported from CarBridge (GPL-3.0). Signs patched APKs with one key per device, kept in the Android Keystore. Reusing the key lets
 * a re-patched app update in place; the key is lost if CarBridge itself is uninstalled.
 */
class KeystoreSigner {

    fun sign(input: File, output: File, minSdk: Int) {
        val (key, cert) = loadOrCreateKey()
        ApkSigning.sign(input, output, minSdk, key, cert)
    }

    /** The certificate copies are signed with; created with the key on first use. */
    fun certificate(): X509Certificate = loadOrCreateKey().second

    /** The certificate if the key exists; it's gone after CarBridge is reinstalled or its data cleared. */
    private val existingCertificate: X509Certificate? by lazy {
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.getCertificate(ALIAS) as X509Certificate?
    }

    /**
     * False when installed [pkg] was signed with a key this phone no longer has, so Android would
     * refuse a rebuilt copy as an update. True when the signers can't be read, to never block.
     */
    fun signedHere(pm: PackageManager, pkg: String): Boolean {
        val signers = runCatching {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        }.getOrNull() ?: return true
        val cert = existingCertificate ?: return false
        return signers.any { it.toByteArray().contentEquals(cert.encoded) }
    }

    private fun loadOrCreateKey(): Pair<PrivateKey, X509Certificate> {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(ALIAS)) generateKey()
        val entry = ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey to entry.certificate as X509Certificate
    }

    private fun generateKey() {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setKeySize(3072)
            // SHA-1 is required for v1 signatures on apps with minSdk < 18
            .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(X500Principal("CN=AA Installer car signer"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(start.time)
            .setCertificateNotAfter(end.time)
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE).apply {
            initialize(spec)
            generateKeyPair()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "aainstaller-car-signer"
    }
}
