package com.vcserver.repositories

import com.vcserver.data.dao.ServerDao
import com.vcserver.models.Server
import kotlinx.coroutines.flow.Flow

/**
 * 服务器仓库实现
 */
class ServerRepositoryImpl(
	private val serverDao: ServerDao
) : ServerRepository {
	override fun getAllServers(): Flow<List<Server>> {
		return serverDao.getAllServers()
	}

	override suspend fun getServerById(id: Long): Server? {
		return serverDao.getServerById(id)
	}

	override suspend fun insertServer(server: Server): Long {
		return serverDao.insertServer(server)
	}

	override suspend fun updateServer(server: Server) {
		serverDao.updateServer(server)
	}

	override suspend fun deleteServer(server: Server) {
		serverDao.deleteServer(server)
	}
}



