// Copyright (C) 2026 NovaScale contributors
// SPDX-License-Identifier: GPL-3.0-only
package cc.galaxnet.novascale.browser

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Call only on IO dispatcher. Corrupt/unrestorable secrets fail closed. */
internal class ProxySecretStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "proxy_credentials")
    fun load(profile: String, rotate: Boolean = false): String {
        check(directory.isDirectory || directory.mkdirs())
        val name = MessageDigest.getInstance("SHA-256").digest(profile.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = AtomicFile(File(directory, name))
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = keyStore.getKey("novascale.proxy.v1", null) as? SecretKey ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("novascale.proxy.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        if (file.baseFile.exists() && !rotate) {
            val bytes = file.readFully()
            require(bytes.size > 28)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(profile.toByteArray())
            return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
        val secret = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP or Base64.URL_SAFE)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(profile.toByteArray())
        val bytes = cipher.iv + cipher.doFinal(secret.toByteArray())
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch (error: Exception) { file.failWrite(stream); throw error }
        return secret
    }
}
