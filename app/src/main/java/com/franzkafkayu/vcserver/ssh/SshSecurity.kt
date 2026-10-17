package com.franzkafkayu.vcserver.ssh

import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 自带名为 BC 的残缺提供者。sshj 的 ed25519 / rsa-sha2 需要 org.bouncycastle 这份。
 * 只替换同名但不同实现的提供者，避免重复插入。
 */
object SshSecurity {
	private const val TAG = "SshSecurity"
	private val installed = AtomicBoolean(false)

	fun install() {
		if (!installed.compareAndSet(false, true)) {
			return
		}
		val full = BouncyCastleProvider()
		val current = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
		if (current != null && current.javaClass.name != full.javaClass.name) {
			Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
		}
		if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
			Security.insertProviderAt(full, 1)
		}
		val active = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
		try {
			Log.i(TAG, "BC provider=${active?.javaClass?.name}")
		} catch (_: RuntimeException) {
		}
	}

	fun providerClassName(): String? {
		return Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)?.javaClass?.name
	}
}
