package com.franzkafkayu.vcserver.utils

/**
 * 导入时把文件里的跳板 id 对到本地服务器。
 */
data class JumpImportRow(
	val exportId: Long,
	val host: String,
	val port: Int,
	val localId: Long,
	val jumpExportId: Long?,
	val written: Boolean
)

fun resolveImportedJumpHostId(rows: List<JumpImportRow>, jumpExportId: Long?): Long? {
	if (jumpExportId == null) return null
	return rows.find { it.exportId == jumpExportId }?.localId
}
