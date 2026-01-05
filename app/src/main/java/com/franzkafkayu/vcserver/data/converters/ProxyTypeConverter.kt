package com.franzkafkayu.vcserver.data.converters

import androidx.room.TypeConverter
import com.franzkafkayu.vcserver.models.ProxyType

/**
 * ProxyType 类型转换器
 */
class ProxyTypeConverter {
	@TypeConverter
	fun fromProxyType(proxyType: ProxyType?): String? {
		return proxyType?.name
	}

	@TypeConverter
	fun toProxyType(proxyType: String?): ProxyType? {
		return proxyType?.let { ProxyType.valueOf(it) }
	}
}

