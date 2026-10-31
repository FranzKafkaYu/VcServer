package com.franzkafkayu.vcserver.mosh

import com.franzkafkayu.vcserver.models.Server
import com.franzkafkayu.vcserver.ssh.SshClientSession

object MoshBootstrap {
	fun requireDirect(server: Server) {
		if (server.jumpHostId != null) {
			throw MoshException("MOSH_JUMP_UNSUPPORTED")
		}
		if (server.proxyEnabled) {
			throw MoshException("MOSH_PROXY_UNSUPPORTED")
		}
	}

	fun start(
		session: SshClientSession,
		server: Server,
		cols: Int,
		rows: Int,
		udpFactory: (String, Int) -> UdpLink = { host, port -> MoshUdp.open(host, port) }
	): TerminalIo {
		requireDirect(server)
		val command = MoshConnectParser.buildCommand(server.moshUdpPort)
		val captured = session.execCapture(command, 20_000) { output ->
			MoshConnectParser.parse(output) != null
		}
		val combined = captured.stdout + "\n" + captured.stderr
		val info = MoshConnectParser.parse(combined)
		if (info == null) {
			if (MoshConnectParser.looksMissing(combined, captured.exitStatus)) {
				throw MoshException("MOSH_SERVER_MISSING")
			}
			throw MoshException("MOSH_PARSE_FAILED")
		}
		val udpPort = info.udpPort
		return MoshEngine.connect(
			host = server.host,
			port = udpPort,
			key = info.key,
			cols = cols,
			rows = rows,
			udpFactory = udpFactory,
			stopRemote = {
				try {
					session.execCapture(MoshConnectParser.stopServerCommand(udpPort), 8_000)
				} catch (_: Exception) {
				}
			}
		)
	}
}
