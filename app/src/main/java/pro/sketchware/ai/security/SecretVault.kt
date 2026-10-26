package pro.sketchware.ai.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keeps AI Agent secrets (API keys, saved profiles) encrypted at rest using a hardware-backed
 * Android Keystore AES-256/GCM key, instead of storing them as plaintext in SharedPreferences.
 *
 * Values are stored as `v1:<base64(iv || ciphertext)>`. Untagged values are treated as legacy
 * plaintext so existing installs can be migrated transparently.
 */
object SecretVault {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "sketchware_ai_agent_secret_vault"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    const val PREFIX = "v1:"

    @Volatile
    private var cachedKey: SecretKey? = null

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    @Synchronized
    private fun getOrCreateKey(): SecretKey? {
        cachedKey?.let { return it }
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                cachedKey = existing.secretKey
                return existing.secretKey
            }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            val key = generator.generateKey()
            cachedKey = key
            key
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Encrypts [plaintext]. Returns the input unchanged only when the platform KeyStore is
     * unavailable, so the agent keeps working rather than losing the user's configuration.
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        return try {
            val key = getOrCreateKey() ?: return plaintext
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, packed, 0, iv.size)
            System.arraycopy(ciphertext, 0, packed, iv.size, ciphertext.size)
            PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (_: Throwable) {
            plaintext
        }
    }

    /**
     * Decrypts a vault-encoded value. Legacy plaintext (no [PREFIX]) is returned verbatim.
     * Returns null when the value is corrupted or the key was invalidated (e.g. app reinstall
     * after removing the KeyStore entry).
     */
    fun decrypt(value: String): String? {
        if (value.isEmpty()) return ""
        if (!isEncrypted(value)) return value
        return try {
            val key = getOrCreateKey() ?: return null
            val packed = Base64.decode(value.substring(PREFIX.length), Base64.NO_WRAP)
            if (packed.size <= IV_LENGTH) return null
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val ciphertext = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}
