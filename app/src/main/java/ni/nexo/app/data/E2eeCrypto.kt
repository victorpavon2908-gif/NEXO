package ni.nexo.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografía E2EE v1 de NEXO.
 *
 * - La identidad RSA se genera dentro de AndroidKeyStore.
 * - La clave privada no es exportable y nunca se envía a Supabase.
 * - Cada mensaje/archivo usa una clave AES-256 aleatoria distinta.
 * - Esa clave AES se envuelve con RSA-OAEP para emisor y receptor para que
 *   ambos puedan abrir su copia del mensaje sin compartir la clave privada.
 * - El contenido se protege con AES/GCM/NoPadding (autenticado).
 */
object E2eeCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS_PREFIX = "nexo.e2ee."
    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_BYTES = 32
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128

    private val secureRandom = SecureRandom()
    private val oaepSpec = OAEPParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA1,
        PSource.PSpecified.DEFAULT
    )

    data class PublicIdentity(
        val keyId: String,
        val publicKeyBase64: String
    )

    data class EncryptedPayload(
        val ciphertextBase64: String,
        val nonceBase64: String,
        val wrappedKeySenderBase64: String,
        val wrappedKeyRecipientBase64: String
    )

    data class EncryptedBytes(
        val ciphertext: ByteArray,
        val nonceBase64: String,
        val wrappedKeySenderBase64: String,
        val wrappedKeyRecipientBase64: String
    )

    fun ensureIdentity(userId: String): PublicIdentity {
        require(userId.isNotBlank()) { "No se puede crear una identidad E2EE sin usuario." }
        val alias = alias(userId)
        val store = keyStore()
        if (!store.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE)
            generator.initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(2048)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generator.generateKeyPair()
        }

        val certificate = store.getCertificate(alias)
            ?: error("AndroidKeyStore no devolvió la clave pública E2EE.")
        val encoded = certificate.publicKey.encoded
        return PublicIdentity(
            keyId = fingerprint(encoded),
            publicKeyBase64 = b64(encoded)
        )
    }

    fun encryptText(
        plaintext: String,
        senderPublicKeyBase64: String,
        recipientPublicKeyBase64: String
    ): EncryptedPayload {
        val encrypted = encryptBytes(
            plaintext.toByteArray(Charsets.UTF_8),
            senderPublicKeyBase64,
            recipientPublicKeyBase64
        )
        return EncryptedPayload(
            ciphertextBase64 = b64(encrypted.ciphertext),
            nonceBase64 = encrypted.nonceBase64,
            wrappedKeySenderBase64 = encrypted.wrappedKeySenderBase64,
            wrappedKeyRecipientBase64 = encrypted.wrappedKeyRecipientBase64
        )
    }

    fun decryptText(
        userId: String,
        ciphertextBase64: String,
        nonceBase64: String,
        wrappedKeyBase64: String
    ): String = decryptBytes(
        userId = userId,
        ciphertext = unb64(ciphertextBase64),
        nonceBase64 = nonceBase64,
        wrappedKeyBase64 = wrappedKeyBase64
    ).toString(Charsets.UTF_8)

    fun encryptBytes(
        plaintext: ByteArray,
        senderPublicKeyBase64: String,
        recipientPublicKeyBase64: String
    ): EncryptedBytes {
        require(plaintext.isNotEmpty()) { "No se puede cifrar contenido vacío." }

        val aesKey = ByteArray(AES_KEY_BYTES).also(secureRandom::nextBytes)
        val nonce = ByteArray(GCM_NONCE_BYTES).also(secureRandom::nextBytes)
        val aes = Cipher.getInstance(AES_TRANSFORMATION)
        aes.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce)
        )
        val ciphertext = aes.doFinal(plaintext)

        return EncryptedBytes(
            ciphertext = ciphertext,
            nonceBase64 = b64(nonce),
            wrappedKeySenderBase64 = b64(wrapAesKey(aesKey, senderPublicKeyBase64)),
            wrappedKeyRecipientBase64 = b64(wrapAesKey(aesKey, recipientPublicKeyBase64))
        ).also {
            aesKey.fill(0)
        }
    }

    fun decryptBytes(
        userId: String,
        ciphertext: ByteArray,
        nonceBase64: String,
        wrappedKeyBase64: String
    ): ByteArray {
        val store = keyStore()
        val privateKey = store.getKey(alias(userId), null)
            ?: error("Este dispositivo no posee la clave privada necesaria para descifrar el contenido.")

        val unwrap = Cipher.getInstance(RSA_TRANSFORMATION)
        unwrap.init(Cipher.DECRYPT_MODE, privateKey, oaepSpec)
        val aesKey = unwrap.doFinal(unb64(wrappedKeyBase64))

        return try {
            val aes = Cipher.getInstance(AES_TRANSFORMATION)
            aes.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, unb64(nonceBase64))
            )
            aes.doFinal(ciphertext)
        } finally {
            aesKey.fill(0)
        }
    }

    private fun wrapAesKey(aesKey: ByteArray, publicKeyBase64: String): ByteArray {
        val decoded = unb64(publicKeyBase64)
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoded))
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
        return cipher.doFinal(aesKey)
    }

    private fun alias(userId: String): String = ALIAS_PREFIX + userId.lowercase()

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun fingerprint(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .take(16)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun b64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun unb64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
