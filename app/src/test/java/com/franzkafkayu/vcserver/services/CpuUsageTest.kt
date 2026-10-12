package com.franzkafkayu.vcserver.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class CpuUsageTest {
	private val service = ServerMonitoringServiceImpl(
		mock<SshAuthenticationService>(),
		mock<SshCommandService>(),
		mock()
	)

	private val first = "cpu 100 0 50 800 50 0 0 0 10 0"
	private val second = "cpu 200 0 100 850 50 0 0 0 40 0"

	@Test
	fun twoSamplesExcludeGuest() {
		val usage = cpuUsagePercent("$first\n$second")
		assertEquals(75.0, usage, 0.001)
	}

	@Test
	fun zeroDeltaMissingSampleOrShortColumnsAreZeroAndCoresRemain() {
		assertUsageAndCores("$first\n$first", 0.0)
		assertUsageAndCores(first, 0.0)
		assertUsageAndCores("cpu 1 2 3", 0.0)
	}

	@Test
	fun snapshotAndFallbackUseTheSamePercent() {
		val raw = "$first\n$second"
		val fromSnapshot = service.parseCoreSnapshot(snapshot(raw))
		assertNotNull(fromSnapshot)
		assertEquals(4, fromSnapshot!!.cpu.cores)
		assertEquals(cpuUsagePercent(raw), fromSnapshot.cpu.usagePercent, 0.001)
		assertTrue(ServerMonitoringServiceImpl.CORE_SNAPSHOT_COMMAND.contains(
			ServerMonitoringServiceImpl.CPU_STAT_SAMPLE_COMMAND
		))
		assertEquals(
			2,
			Regex("head -1 /proc/stat").findAll(ServerMonitoringServiceImpl.CPU_STAT_SAMPLE_COMMAND).count()
		)
		assertTrue(ServerMonitoringServiceImpl.CPU_STAT_SAMPLE_COMMAND.contains("sleep 1"))
	}

	@Test
	fun operatingSystemCommandDoesNotReadProcStat() {
		val command = ServerMonitoringServiceImpl.OS_ONLY_COMMAND
		assertFalse(command.contains("/proc/stat"))
		assertFalse(command.contains("sleep"))
		assertTrue(command.contains("uname -r"))
		assertTrue(command.contains("/etc/os-release"))
	}

	private fun assertUsageAndCores(stat: String, usage: Double) {
		assertEquals(usage, cpuUsagePercent(stat), 0.001)
		val status = service.parseCoreSnapshot(snapshot(stat))
		assertNotNull(status)
		assertEquals(4, status!!.cpu.cores)
		assertEquals(usage, status.cpu.usagePercent, 0.001)
	}

	private fun snapshot(stat: String): String = buildString {
		appendLine("---VC_CPU_STAT---")
		appendLine(stat.trim())
		appendLine("---VC_CPU_CORES---")
		appendLine("4")
		appendLine("---VC_CPU_ARCH---")
		appendLine("x86_64")
		appendLine("---VC_CPU_MODEL---")
		appendLine("Test CPU")
		appendLine("---VC_MEM---")
		appendLine("              total        used        free      shared  buff/cache   available")
		appendLine("Mem:     8000000000  2000000000  4000000000           0  2000000000  5000000000")
		appendLine("---VC_DISK---")
		appendLine("---VC_UPTIME---")
		appendLine("---VC_KERNEL---")
		appendLine("5.15.0")
		appendLine("---VC_OS---")
		appendLine("PRETTY_NAME=\"Ubuntu 22.04 LTS\"")
		appendLine("---VC_END---")
	}.trimEnd()
}
