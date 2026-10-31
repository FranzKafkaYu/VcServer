package com.franzkafkayu.vcserver.mosh

data class MoshConnectInfo(
	val udpPort: Int,
	val key: String
)

object MoshConnectParser {
	private val line = Regex(
		"""MOSH CONNECT\s+(\d+)\s+([A-Za-z0-9+/]+)={0,2}"""
	)

	fun parse(output: String): MoshConnectInfo? {
		val normalized = output.replace("\r", "\n")
		val match = line.find(normalized) ?: return null
		val port = match.groupValues[1].toIntOrNull() ?: return null
		if (port !in 1..65535) return null
		val key = match.groupValues[2]
		if (key.length < 22) return null
		return MoshConnectInfo(udpPort = port, key = key.take(22))
	}

	fun looksMissing(output: String, exitStatus: Int?): Boolean {
		if (exitStatus == 127) return true
		val text = output.lowercase()
		return text.contains("not found") ||
			text.contains("no such file") ||
			text.contains("command not found")
	}

	fun buildCommand(udpPort: Int?): String {
		val portArg = portFlag(udpPort)
		return "LANG=C.UTF-8 LC_ALL=C.UTF-8 mosh-server new -c 256 -l LANG=C.UTF-8$portArg"
	}

	fun stopServerCommand(udpPort: Int): String {
		require(udpPort in 1..65535)
		return "sh -c 'P=$udpPort; command -v fuser >/dev/null 2>&1 && fuser -k \"\$P\"/udp >/dev/null 2>&1; " +
			"for pid in \$(ss -H -ulnp 2>/dev/null | grep mosh-server | grep \":\$P\" | sed -n \"s/.*pid=\\([0-9][0-9]*\\).*/\\1/p\" | sort -u); do kill \"\$pid\" >/dev/null 2>&1; done'"
	}

	private fun portFlag(udpPort: Int?): String {
		if (udpPort == null) {
			return " -p 60000:61000"
		}
		if (udpPort < 61000) {
			return " -p $udpPort:61000"
		}
		return " -p $udpPort"
	}
}
