package com.franzkafkayu.vcserver.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security

class SshSecurityTest {
	@Test
	fun installRegistersOrgBouncyCastleOnce() {
		SshSecurity.install()
		val provider = Security.getProvider("BC")
		assertNotNull(provider)
		assertTrue(provider!!.javaClass.name.startsWith("org.bouncycastle"))
		assertEquals(provider.javaClass.name, SshSecurity.providerClassName())

		SshSecurity.install()
		assertEquals(provider.javaClass.name, Security.getProvider("BC")?.javaClass?.name)
	}
}
