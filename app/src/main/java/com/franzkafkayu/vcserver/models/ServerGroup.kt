package com.franzkafkayu.vcserver.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 服务器分组实体类
 */
@Entity(
	tableName = "server_groups",
	indices = [Index(value = ["name"], unique = true)]
)
data class ServerGroup(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val name: String,
	val createdAt: Long = System.currentTimeMillis(),
	val updatedAt: Long = System.currentTimeMillis()
)
