package com.vcserver.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * 安全存储工具类
 * 使用 Android Keystore 加密存储敏感信息
 */
class SecureStorage(private val context: Context) {
	companion object {
		private const val KEYSTORE_ALIAS = "VcServerKeyStore"
		private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
		private const val KEY_SIZE = 256
	}

	private val masterKey: MasterKey by lazy {
		MasterKey.Builder(context)
			.setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
			.build()
	}

	/**
	 * 加密并存储密码
	 */
	fun encryptPassword(plainPassword: String): String {
		return try {
			val file = File(context.filesDir, "encrypted_password_${System.currentTimeMillis()}.txt")
			val encryptedFile = EncryptedFile.Builder(
				context,
				file,
				masterKey,
				EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
			).build()

			encryptedFile.openFileOutput().use { outputStream ->
				outputStream.write(plainPassword.toByteArray(StandardCharsets.UTF_8))
			}

			// 返回文件路径作为标识，实际使用时需要读取文件内容
			file.absolutePath
		} catch (e: Exception) {
			throw SecureStorageException("Failed to encrypt password", e)
		}
	}

	/**
	 * 解密并读取密码
	 */
	fun decryptPassword(encryptedData: String): String {
		return try {
			val file = File(encryptedData)
			if (!file.exists()) {
				throw SecureStorageException("Encrypted file not found")
			}

			val encryptedFile = EncryptedFile.Builder(
				context,
				file,
				masterKey,
				EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
			).build()

			encryptedFile.openFileInput().use { inputStream ->
				val bytes = inputStream.readBytes()
				String(bytes, StandardCharsets.UTF_8)
			}
		} catch (e: Exception) {
			throw SecureStorageException("Failed to decrypt password", e)
		}
	}

	/**
	 * 加密并存储私钥
	 */
	fun encryptPrivateKey(plainPrivateKey: String): String {
		return try {
			val file = File(context.filesDir, "encrypted_key_${System.currentTimeMillis()}.txt")
			val encryptedFile = EncryptedFile.Builder(
				context,
				file,
				masterKey,
				EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
			).build()

			encryptedFile.openFileOutput().use { outputStream ->
				outputStream.write(plainPrivateKey.toByteArray(StandardCharsets.UTF_8))
			}

			file.absolutePath
		} catch (e: Exception) {
			throw SecureStorageException("Failed to encrypt private key", e)
		}
	}

	/**
	 * 解密并读取私钥
	 */
	fun decryptPrivateKey(encryptedData: String): String {
		return try {
			val file = File(encryptedData)
			if (!file.exists()) {
				throw SecureStorageException("Encrypted file not found")
			}

			val encryptedFile = EncryptedFile.Builder(
				context,
				file,
				masterKey,
				EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
			).build()

			encryptedFile.openFileInput().use { inputStream ->
				val bytes = inputStream.readBytes()
				String(bytes, StandardCharsets.UTF_8)
			}
		} catch (e: Exception) {
			throw SecureStorageException("Failed to decrypt private key", e)
		}
	}

	/**
	 * 删除加密文件
	 */
	fun deleteEncryptedFile(filePath: String) {
		try {
			val file = File(filePath)
			if (file.exists()) {
				file.delete()
			}
		} catch (e: Exception) {
			// 忽略删除错误
		}
	}
}

/**
 * 安全存储异常
 */
class SecureStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)



