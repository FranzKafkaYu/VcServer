package com.vcserver.utils

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
		is com.vcserver.services.SshConnectionException -> {
			AppError.NetworkError(message ?: "SSH 连接失败")
		}
		is com.vcserver.services.ValidationException -> {
			AppError.ValidationError(message ?: "验证失败")
		}
		is com.vcserver.utils.SecureStorageException -> {
			AppError.AuthenticationError(message ?: "安全存储操作失败")
		}
		else -> {
			AppError.UnknownError(message ?: "未知错误")
		}
	}
}



