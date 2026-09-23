/*
 * NovaScale for Android
 * Copyright (C) 2026 NovaScale contributors
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package cc.galaxnet.novascale.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.StringRes
import cc.galaxnet.novascale.R
import cc.galaxnet.novascale.nativecore.NativeSessionRequest
import cc.galaxnet.novascale.nativecore.NativeTerminalTheme
import cc.galaxnet.novascale.nativecore.SshAuthentication
import cc.galaxnet.novascale.ui.UiText
import cc.galaxnet.novascale.ui.uiText
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal enum class AuthenticationKind(@StringRes val labelRes: Int) {
    NONE(R.string.auth_none_tailscale),
    PASSWORD(R.string.auth_password),
    PRIVATE_KEY(R.string.auth_private_key),
}

internal data class RemoteConnectionConfig(
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authenticationKind: AuthenticationKind = AuthenticationKind.NONE,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
) {
    fun validate(): UiText? = when {
        host.isBlank() -> uiText(R.string.validation_choose_host)
        port.toIntOrNull() !in 1..65535 -> uiText(R.string.validation_port)
        username.isBlank() -> uiText(R.string.validation_username)
        authenticationKind == AuthenticationKind.PASSWORD && password.isEmpty() ->
            uiText(R.string.validation_password)
        authenticationKind == AuthenticationKind.PRIVATE_KEY && privateKey.isBlank() ->
            uiText(R.string.validation_private_key)
        else -> null
    }

    fun request(
        expectedHostKey: String?,
        columns: Int = 80,
        rows: Int = 24,
        terminalTheme: NativeTerminalTheme? = null,
    ) =
        NativeSessionRequest(
            host = host.trim(),
            username = username.trim(),
            authentication = when (authenticationKind) {
                AuthenticationKind.NONE -> SshAuthentication.None
                AuthenticationKind.PASSWORD -> SshAuthentication.Password(password)
                AuthenticationKind.PRIVATE_KEY -> SshAuthentication.PrivateKey(
                    privateKey = privateKey,
                    passphrase = passphrase.ifEmpty { null },
                )
            },
            expectedHostKey = expectedHostKey,
            columns = columns.coerceIn(20, 500),
            rows = rows.coerceIn(5, 300),
            terminalTheme = terminalTheme,
        )

    val portNumber: Int
        get() = port.toInt()

    val endpoint: String
        get() = "${host.trim()}:$portNumber"
}

/** Fingerprints are not secrets and are kept separately from encrypted credentials. */
internal class KnownHostStore(context: Context) {
    private val preferences = context.getSharedPreferences("known_ssh_hosts", Context.MODE_PRIVATE)

    fun fingerprint(config: RemoteConnectionConfig): String? =
        preferences.getString(config.endpoint, null)

    fun trust(config: RemoteConnectionConfig, fingerprint: String) {
        require(fingerprint.startsWith("SHA256:"))
        preferences.edit().putString(config.endpoint, fingerprint).apply()
    }

    fun forget(config: RemoteConnectionConfig) {
        preferences.edit().remove(config.endpoint).apply()
    }
}

/** Per-host SSH settings encrypted with a non-exportable Android Keystore key. */
internal class SshProfileStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun contains(hostId: String): Boolean = preferences.contains(preferenceKey(hostId))

    fun load(hostId: String): RemoteConnectionConfig? {
        val encoded = preferences.getString(preferenceKey(hostId), null) ?: return null
        return runCatching {
            val parts = encoded.split(':', limit = 3)
            require(parts.size == 3 && parts[0] == FORMAT_VERSION)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[1], Base64.NO_WRAP)),
            )
            val document = JSONObject(
                String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), StandardCharsets.UTF_8),
            )
            RemoteConnectionConfig(
                host = document.optString("host"),
                port = document.optString("port", "22"),
                username = document.optString("username"),
                authenticationKind = runCatching {
                    AuthenticationKind.valueOf(document.optString("authenticationKind"))
                }.getOrDefault(AuthenticationKind.NONE),
                password = document.optString("password"),
                privateKey = document.optString("privateKey"),
                passphrase = document.optString("passphrase"),
            )
        }.getOrNull()
    }

    fun save(hostId: String, config: RemoteConnectionConfig) {
        val document = JSONObject()
            .put("host", config.host.trim())
            .put("port", config.port)
            .put("username", config.username.trim())
            .put("authenticationKind", config.authenticationKind.name)
            .put("password", config.password)
            .put("privateKey", config.privateKey)
            .put("passphrase", config.passphrase)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val ciphertext = cipher.doFinal(document.toString().toByteArray(StandardCharsets.UTF_8))
        val encoded = listOf(
            FORMAT_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        ).joinToString(":")
        preferences.edit().putString(preferenceKey(hostId), encoded).apply()
    }

    fun remove(hostId: String) {
        preferences.edit().remove(preferenceKey(hostId)).apply()
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun preferenceKey(hostId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(hostId.toByteArray(StandardCharsets.UTF_8))
        return "host_" + digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PREFERENCES_NAME = "encrypted_ssh_profiles"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "novascale_ssh_profiles_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val FORMAT_VERSION = "v1"
    }
}

internal data class HostKeyPrompt(
    val fingerprint: String,
    val algorithm: String?,
    val expectedFingerprint: String?,
) {
    val changed: Boolean
        get() = expectedFingerprint != null
}
