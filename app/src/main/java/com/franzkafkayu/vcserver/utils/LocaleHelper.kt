package com.franzkafkayu.vcserver.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.franzkafkayu.vcserver.models.LanguageMode
import java.util.Locale

/**
 * Locale 辅助工具类
 */
object LocaleHelper {
	/**
	 * 根据语言模式获取 Locale
	 */
	fun getLocale(languageMode: LanguageMode): Locale {
		return when (languageMode) {
			LanguageMode.CHINESE -> Locale("zh", "CN")
			LanguageMode.ENGLISH -> Locale.ENGLISH
			LanguageMode.SYSTEM -> {
				// 获取系统默认 Locale
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
					Resources.getSystem().configuration.locales[0]
				} else {
					@Suppress("DEPRECATION")
					Resources.getSystem().configuration.locale
				}
			}
		}
	}

	/**
	 * 更新 Context 的配置以应用新的 Locale
	 */
	fun updateConfiguration(context: Context, locale: Locale): Context {
		val resources = context.resources
		val configuration = Configuration(resources.configuration)

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			configuration.setLocale(locale)
			val localeList = LocaleList(locale)
			LocaleList.setDefault(localeList)
			configuration.setLocales(localeList)
		} else {
			@Suppress("DEPRECATION")
			configuration.locale = locale
			@Suppress("DEPRECATION")
			resources.updateConfiguration(configuration, resources.displayMetrics)
		}

		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
			context.createConfigurationContext(configuration)
		} else {
			@Suppress("DEPRECATION")
			resources.updateConfiguration(configuration, resources.displayMetrics)
			context
		}
	}

	/**
	 * 包装 Context 以应用指定的 Locale
	 */
	fun wrapContext(context: Context, locale: Locale): Context {
		return updateConfiguration(context, locale)
	}
}

