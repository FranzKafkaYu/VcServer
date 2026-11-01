package com.franzkafkayu.vcserver.models

data class CustomShellCommand(
	val id: String,
	val title: String,
	val body: String,
	val scopeType: String = CustomShellCommandScope.GLOBAL,
	val scopeId: Long? = null
)

object CustomShellCommandScope {
	const val GLOBAL = "GLOBAL"
	const val GROUP = "GROUP"
	const val SERVER = "SERVER"

	fun normalize(type: String?): String {
		return when (type?.trim()?.uppercase()) {
			GROUP -> GROUP
			SERVER -> SERVER
			else -> GLOBAL
		}
	}
}
