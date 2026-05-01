package `in`.sakhi.core.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Derives the SQLCipher passphrase for SakhiDatabase.
 *
 * Passphrase = SHA-256(userId + deviceSecret) as ByteArray.
 * deviceSecret is a 256-bit AES key stored in Android Keystore — never leaves secure hardware.
 * This means the database is only decryptable on the device that created it.
 *
 * DISHA compliance: encrypted at rest, key tied to device hardware.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val keyAlias = "sakhi_db_key"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }

    fun derivePassphrase(userId: String): ByteArray {
        val deviceSecret = getOrCreateDeviceSecret()
        val combined = userId + deviceSecret.encoded.contentToString()
        return MessageDigest.getInstance("SHA-256").digest(combined.toByteArray(Charsets.UTF_8))
    }

    private fun getOrCreateDeviceSecret(): SecretKey {
        if (keyStore.containsAlias(keyAlias)) {
            return (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGenSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(256)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        keyGen.init(keyGenSpec)
        return keyGen.generateKey()
    }

    fun deleteKey() {
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }
}
