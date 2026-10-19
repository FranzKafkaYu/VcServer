package com.franzkafkayu.vcserver.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorMenuTest {
	@Test
	fun selectionModeHidesVendorActions() {
		assertTrue(vendorMenu(selectionMode = true, vendor = VendorKind.BANDWAGON).entries.isEmpty())
		assertTrue(vendorMenu(selectionMode = true, vendor = null).entries.isEmpty())
	}

	@Test
	fun unboundServerOnlyOffersBind() {
		val menu = vendorMenu(selectionMode = false, vendor = null)
		assertEquals(listOf(VendorMenuEntry.BIND), menu.entries)
		assertEquals(null, menu.vendor)
	}

	@Test
	fun bandwagonBoundServerOffersLabeledPower() {
		val menu = vendorMenu(selectionMode = false, vendor = VendorKind.BANDWAGON)
		assertEquals(VendorKind.BANDWAGON, menu.vendor)
		assertEquals(
			listOf(
				VendorMenuEntry.START,
				VendorMenuEntry.STOP,
				VendorMenuEntry.RESTART,
				VendorMenuEntry.STATUS,
				VendorMenuEntry.SERVICE_INFO,
				VendorMenuEntry.MIGRATE,
				VendorMenuEntry.RESET_ROOT,
				VendorMenuEntry.USAGE,
				VendorMenuEntry.UNBIND
			),
			menu.entries
		)
	}

	@Test
	fun cloudconeBoundServerOffersTheSameActions() {
		val menu = vendorMenu(selectionMode = false, vendor = VendorKind.CLOUDCONE)
		assertEquals(VendorKind.CLOUDCONE, menu.vendor)
		assertEquals(VendorMenuEntry.START, menu.entries.first())
		assertEquals(VendorMenuEntry.UNBIND, menu.entries.last())
	}

	@Test
	fun virtualizorBoundServerOffersBandwidth() {
		val menu = vendorMenu(selectionMode = false, vendor = VendorKind.VIRTUALIZOR)
		assertEquals(VendorKind.VIRTUALIZOR, menu.vendor)
		assertTrue(menu.entries.contains(VendorMenuEntry.BANDWIDTH))
		assertEquals(VendorMenuEntry.UNBIND, menu.entries.last())
	}

	@Test
	fun otherVendorsDoNotOfferServiceInfo() {
		assertTrue(
			VendorMenuEntry.SERVICE_INFO in vendorMenu(selectionMode = false, vendor = VendorKind.CLOUDCONE).entries
		)
		assertTrue(
			VendorMenuEntry.SERVICE_INFO !in vendorMenu(selectionMode = false, vendor = VendorKind.VIRTUALIZOR).entries
		)
		assertTrue(
			vendorMenu(selectionMode = true, vendor = VendorKind.CLOUDCONE).entries.isEmpty()
		)
		assertTrue(
			vendorMenu(selectionMode = true, vendor = VendorKind.BANDWAGON).entries.isEmpty()
		)
	}

	@Test
	fun otherVendorsDoNotOfferMigrate() {
		assertTrue(
			VendorMenuEntry.MIGRATE !in vendorMenu(selectionMode = false, vendor = VendorKind.CLOUDCONE).entries
		)
		assertTrue(
			VendorMenuEntry.MIGRATE !in vendorMenu(selectionMode = false, vendor = VendorKind.VIRTUALIZOR).entries
		)
		assertTrue(
			vendorMenu(selectionMode = true, vendor = VendorKind.BANDWAGON).entries.isEmpty()
		)
	}

	@Test
	fun otherVendorsDoNotOfferResetOrUsage() {
		assertTrue(
			VendorMenuEntry.RESET_ROOT !in vendorMenu(selectionMode = false, vendor = VendorKind.CLOUDCONE).entries
		)
		assertTrue(
			VendorMenuEntry.USAGE !in vendorMenu(selectionMode = false, vendor = VendorKind.VIRTUALIZOR).entries
		)
		assertTrue(
			vendorMenu(selectionMode = true, vendor = VendorKind.BANDWAGON).entries.isEmpty()
		)
	}

	@Test
	fun otherVendorsDoNotOfferBandwidth() {
		assertTrue(
			VendorMenuEntry.BANDWIDTH !in vendorMenu(selectionMode = false, vendor = VendorKind.BANDWAGON).entries
		)
		assertTrue(
			VendorMenuEntry.BANDWIDTH !in vendorMenu(selectionMode = false, vendor = VendorKind.CLOUDCONE).entries
		)
	}

	@Test
	fun greencloudBoundServerOffersInfoAndReset() {
		val menu = vendorMenu(selectionMode = false, vendor = VendorKind.GREENCLOUD)
		assertEquals(VendorKind.GREENCLOUD, menu.vendor)
		assertEquals(
			listOf(
				VendorMenuEntry.START,
				VendorMenuEntry.STOP,
				VendorMenuEntry.RESTART,
				VendorMenuEntry.STATUS,
				VendorMenuEntry.SERVICE_INFO,
				VendorMenuEntry.RESET_ROOT,
				VendorMenuEntry.UNBIND
			),
			menu.entries
		)
		assertTrue(VendorMenuEntry.MIGRATE !in menu.entries)
		assertTrue(VendorMenuEntry.BANDWIDTH !in menu.entries)
	}
}
