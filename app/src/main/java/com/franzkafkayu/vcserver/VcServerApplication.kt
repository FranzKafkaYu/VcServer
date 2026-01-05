package com.franzkafkayu.vcserver

import android.app.Application
import com.franzkafkayu.vcserver.utils.SessionManager
import com.franzkafkayu.vcserver.utils.SshConnectionPool

/**
 * VcServer 应用程序类
 * 用于管理应用生命周期和全局资源
 */
class VcServerApplication : Application() {
	override fun onCreate() {
		super.onCreate()
		// 应用启动时，清理可能残留的连接（如果应用异常退出）
		SshConnectionPool.cleanupIdleConnections()
	}

	override fun onTerminate() {
		super.onTerminate()
		// 应用退出时，清理所有连接
		SshConnectionPool.clear()
		SessionManager.clear()
	}
}
