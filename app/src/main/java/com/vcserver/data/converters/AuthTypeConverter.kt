package com.vcserver.data.converters

import androidx.room.TypeConverter
import com.vcserver.models.AuthType

/**
 * AuthType 类型转换器
 */
class AuthTypeConverter {
	@TypeConverter
	fun fromAuthType(authType: AuthType): String {
		return authType.name
	}

	@TypeConverter
	fun toAuthType(authType: String): AuthType {
		return AuthType.valueOf(authType)
	}
}



