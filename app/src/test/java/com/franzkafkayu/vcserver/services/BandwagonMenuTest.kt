package com.franzkafkayu.vcserver.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BandwagonMenuTest {
	@Test
	fun selectionModeHidesBandwagonActions() {
		assertTrue(bandwagonMenuEntries(selectionMode = true, bound = true).isEmpty())
		assertTrue(bandwagonMenuEntries(selectionMode = true, bound = false).isEmpty())
	}

	@Test
	fun unboundServerOnlyOffersBind() {
		assertEquals(listOf(BandwagonMenuEntry.BIND), bandwagonMenuEntries(selectionMode = false, bound = false))
	}

	@Test
	fun boundServerOffersPowerStatusAndUnbind() {
		assertEquals(
			listOf(
				BandwagonMenuEntry.START,
				BandwagonMenuEntry.STOP,
				BandwagonMenuEntry.RESTART,
				BandwagonMenuEntry.STATUS,
				BandwagonMenuEntry.UNBIND
			),
			bandwagonMenuEntries(selectionMode = false, bound = true)
		)
	}
}
