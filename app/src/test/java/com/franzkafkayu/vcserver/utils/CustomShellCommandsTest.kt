package com.franzkafkayu.vcserver.utils

import com.franzkafkayu.vcserver.models.AuthType
import com.franzkafkayu.vcserver.models.CustomShellCommand
import com.franzkafkayu.vcserver.models.CustomShellCommandScope
import com.franzkafkayu.vcserver.models.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomShellCommandsTest {
	@Test
	fun saveRejectsBlankTitleOrBody() {
		assertNull(CustomShellCommands.save(emptyList(), null, "  ", "uptime"))
		assertNull(CustomShellCommands.save(emptyList(), null, "uptime", "   \n"))
		assertNull(CustomShellCommands.save(emptyList(), null, "", "ls"))
	}

	@Test
	fun saveRejectsMissingGroupOrServerTarget() {
		assertNull(
			CustomShellCommands.save(
				emptyList(),
				null,
				"df",
				"df -h",
				CustomShellCommandScope.GROUP,
				null,
				groupIds = setOf(1L)
			)
		)
		assertNull(
			CustomShellCommands.save(
				emptyList(),
				null,
				"df",
				"df -h",
				CustomShellCommandScope.GROUP,
				9L,
				groupIds = setOf(1L)
			)
		)
		assertNull(
			CustomShellCommands.save(
				emptyList(),
				null,
				"df",
				"df -h",
				CustomShellCommandScope.SERVER,
				2L,
				serverIds = setOf(1L)
			)
		)
	}

	@Test
	fun saveAddsAndUpdatesWithScope() {
		val added = CustomShellCommands.save(
			emptyList(),
			null,
			"  uptime  ",
			"  uptime -p  ",
			CustomShellCommandScope.GROUP,
			3L,
			groupIds = setOf(3L)
		)!!
		assertEquals(1, added.size)
		assertEquals("uptime", added[0].title)
		assertEquals("uptime -p", added[0].body)
		assertEquals(CustomShellCommandScope.GROUP, added[0].scopeType)
		assertEquals(3L, added[0].scopeId)
		val updated = CustomShellCommands.save(
			added,
			added[0].id,
			"load",
			"uptime",
			CustomShellCommandScope.GLOBAL,
			3L
		)!!
		assertEquals(CustomShellCommandScope.GLOBAL, updated[0].scopeType)
		assertNull(updated[0].scopeId)
		assertEquals(added[0].id, updated[0].id)
	}

	@Test
	fun decodeMissingScopeAsGlobal() {
		val decoded = CustomShellCommands.decode(
			"""[{"id":"a","title":"df","body":"df -h"}]"""
		)
		assertEquals(1, decoded.size)
		assertEquals(CustomShellCommandScope.GLOBAL, decoded[0].scopeType)
		assertNull(decoded[0].scopeId)
		assertEquals(
			decoded,
			CustomShellCommands.decode(CustomShellCommands.encode(decoded))
		)
		assertTrue(CustomShellCommands.decode(null).isEmpty())
		assertTrue(CustomShellCommands.decode("{").isEmpty())
		assertTrue(CustomShellCommands.decode("[]").isEmpty())
	}

	@Test
	fun sanitizeDropsInvalidEntriesKeepsOrphanScope() {
		val raw = listOf(
			CustomShellCommand("1", "ok", "ls"),
			CustomShellCommand("", "bad", "ls"),
			CustomShellCommand("2", " ", "ls"),
			CustomShellCommand("3", "gone", "uptime", CustomShellCommandScope.GROUP, 99L)
		)
		val sanitized = CustomShellCommands.sanitize(raw)
		assertEquals(2, sanitized.size)
		assertEquals("ok", sanitized[0].title)
		assertEquals(CustomShellCommandScope.GROUP, sanitized[1].scopeType)
		assertEquals(99L, sanitized[1].scopeId)
	}

	@Test
	fun forServerFiltersByScope() {
		val global = CustomShellCommand("g", "all", "uptime", CustomShellCommandScope.GLOBAL, null)
		val group = CustomShellCommand("p", "prod", "df", CustomShellCommandScope.GROUP, 2L)
		val one = CustomShellCommand("s", "one", "free", CustomShellCommandScope.SERVER, 10L)
		val list = listOf(global, group, one)
		val grouped = server(id = 10L, groupId = 2L)
		val otherGroup = server(id = 11L, groupId = 3L)
		val ungrouped = server(id = 12L, groupId = null)
		assertEquals(listOf(global, group, one), CustomShellCommands.forServer(list, grouped))
		assertEquals(listOf(global), CustomShellCommands.forServer(list, otherGroup))
		assertEquals(listOf(global), CustomShellCommands.forServer(list, ungrouped))
		assertEquals(
			listOf(global, group),
			CustomShellCommands.forServer(list, server(id = 99L, groupId = 2L))
		)
		assertFalse(
			CustomShellCommands.appliesTo(
				CustomShellCommand("x", "old", "ls", CustomShellCommandScope.GROUP, 8L),
				ungrouped
			)
		)
	}

	private fun server(id: Long, groupId: Long?): Server {
		return Server(
			id = id,
			name = "s$id",
			host = "127.0.0.1",
			username = "root",
			authType = AuthType.PASSWORD,
			groupId = groupId
		)
	}
}
