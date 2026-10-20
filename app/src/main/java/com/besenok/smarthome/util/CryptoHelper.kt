package com.besenok.smarthome.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Утилита для шифрования/дешифрования чувствительных данных (пароли, API ключи)
 * Использует Android Keystore + AES/GCM/NoPadding
 */
object CryptoHelper {

    private const val KEY_ALIAS = "smart_home_monitor_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12 // bytes
    private const val GCM_TAG_LENGTH = 128 // bits

    private val cipher: Cipher by lazy {
        Cipher.getInstance(TRANSFORMATION)
    }

    /**
     * Генерирует ключ в Android Keystore
     */
    private fun initKeyGenerator(): KeyGenerator {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        val keyGenSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keyGenSpec)
        return keyGenerator
    }

    /**
     * Получает секретный ключ из Keystore
     */
    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * Шифрует строку
     * @return Base64 строка: IV + ciphertext
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""

        val key = getSecretKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv

        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Объединяем IV + encrypted и кодируем в Base64
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)

        return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
    }

    /**
     * Дешифрует Base64 строку
     * @return Исходная строка или пустая при ошибке
     */
    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""

        try {
            val combined = android.util.Base64.decode(encryptedText, android.util.Base64.NO_WRAP)
            val key = getSecretKey()

            // Извлекаем IV
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )

            val decrypted = cipher.doFinal(encrypted)
            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            // Если ключ не найден или данные повреждены
            return ""
        }
    }
}
