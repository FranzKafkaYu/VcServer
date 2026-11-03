package com.franzkafkayu.vcserver.ui.viewmodels

import com.franzkafkayu.vcserver.services.VendorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorOperationTrackerTest {
	@Test
	fun beginRecordsOperationDetails() {
		val tracker = VendorOperationTracker()

		val operation = tracker.begin(7L, VendorKind.CLOUDCONE, VendorOperationKind.START)

		assertNotNull(operation)
		assertEquals(7L, operation!!.serverId)
		assertEquals(VendorKind.CLOUDCONE, operation.vendor)
		assertEquals(VendorOperationKind.START, operation.kind)
		assertTrue(tracker.isRunning(7L, VendorOperationKind.START))
	}

	@Test
	fun duplicateBeginReturnsNullUntilFinished() {
		val tracker = VendorOperationTracker()
		val first = tracker.begin(1L, VendorKind.BANDWAGON, VendorOperationKind.RESTART)!!

		assertNull(tracker.begin(1L, VendorKind.BANDWAGON, VendorOperationKind.RESTART))
		assertTrue(tracker.isRunning(1L, VendorOperationKind.RESTART))

		assertTrue(tracker.finish(first.id))

		assertFalse(tracker.isRunning(1L, VendorOperationKind.RESTART))
		assertNotNull(tracker.begin(1L, VendorKind.BANDWAGON, VendorOperationKind.RESTART))
	}

	@Test
	fun differentKindsRunIndependently() {
		val tracker = VendorOperationTracker()

		val start = tracker.begin(1L, VendorKind.VIRTUALIZOR, VendorOperationKind.START)!!
		val stop = tracker.begin(1L, VendorKind.VIRTUALIZOR, VendorOperationKind.STOP)!!

		assertNotEquals(start.id, stop.id)
		assertTrue(tracker.isRunning(1L, VendorOperationKind.START))
		assertTrue(tracker.isRunning(1L, VendorOperationKind.STOP))

		tracker.finish(start.id)

		assertFalse(tracker.isRunning(1L, VendorOperationKind.START))
		assertTrue(tracker.isRunning(1L, VendorOperationKind.STOP))
	}

	@Test
	fun differentServersRunIndependently() {
		val tracker = VendorOperationTracker()

		tracker.begin(1L, VendorKind.GREENCLOUD, VendorOperationKind.SERVICE_INFO)
		val second = tracker.begin(2L, VendorKind.GREENCLOUD, VendorOperationKind.SERVICE_INFO)

		assertNotNull(second)
		assertTrue(tracker.isRunning(1L, VendorOperationKind.SERVICE_INFO))
		assertTrue(tracker.isRunning(2L, VendorOperationKind.SERVICE_INFO))
	}

	@Test
	fun finishStaleIdKeepsCurrentRegistration() {
		val tracker = VendorOperationTracker()
		val first = tracker.begin(3L, VendorKind.BANDWAGON, VendorOperationKind.STATUS)!!
		tracker.finish(first.id)

		val current = tracker.begin(3L, VendorKind.BANDWAGON, VendorOperationKind.STATUS)!!

		assertNotEquals(first.id, current.id)
		assertFalse(tracker.finish(first.id))
		assertTrue(tracker.isRunning(3L, VendorOperationKind.STATUS))
	}
}
