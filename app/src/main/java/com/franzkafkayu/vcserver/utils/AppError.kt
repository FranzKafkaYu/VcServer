package com.franzkafkayu.vcserver.utils

/**
 * 应用错误类型
 */
sealed class AppError {
	abstract val message: String

	data class NetworkError(override val message: String) : AppError()
	data class AuthenticationError(override val message: String) : AppError()
	data class DatabaseError(override val message: String) : AppError()
	data class ValidationError(override val message: String) : AppError()
	data class UnknownError(override val message: String) : AppError()
}

/**
 * 将异常转换为 AppError
 */
fun Throwable.toAppError(): AppError {
	return when (this) {
		is com.franzkafkayu.vcserver.services.SshConnectionException -> {
			AppError.NetworkError(message ?: "CONNECTION_FAILED")
		}
		is com.franzkafkayu.vcserver.services.ValidationException -> {
			AppError.ValidationError(message ?: "VALIDATION_FAILED")
		}
		is com.franzkafkayu.vcserver.utils.SecureStorageException -> {
			AppError.AuthenticationError(message ?: "SECURE_STORAGE_FAILED")
		}
		else -> {
			AppError.UnknownError(message ?: "UNKNOWN_ERROR")
		}
	}
}



