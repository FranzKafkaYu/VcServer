package com.franzkafkayu.vcserver.services

enum class VendorKind {
	BANDWAGON,
	CLOUDCONE,
	VIRTUALIZOR
}

enum class VendorMenuEntry {
	BIND,
	START,
	STOP,
	RESTART,
	STATUS,
	SERVICE_INFO,
	MIGRATE,
	RESET_ROOT,
	USAGE,
	BANDWIDTH,
	UNBIND
}

data class VendorMenu(
	val vendor: VendorKind?,
	val entries: List<VendorMenuEntry>
)

fun vendorMenu(selectionMode: Boolean, vendor: VendorKind?): VendorMenu {
	if (selectionMode) return VendorMenu(vendor = null, entries = emptyList())
	if (vendor == null) return VendorMenu(vendor = null, entries = listOf(VendorMenuEntry.BIND))
	val entries = mutableListOf(
		VendorMenuEntry.START,
		VendorMenuEntry.STOP,
		VendorMenuEntry.RESTART,
		VendorMenuEntry.STATUS
	)
	if (vendor == VendorKind.BANDWAGON) {
		entries += VendorMenuEntry.SERVICE_INFO
		entries += VendorMenuEntry.MIGRATE
		entries += VendorMenuEntry.RESET_ROOT
		entries += VendorMenuEntry.USAGE
	}
	if (vendor == VendorKind.CLOUDCONE) {
		entries += VendorMenuEntry.SERVICE_INFO
	}
	if (vendor == VendorKind.VIRTUALIZOR) {
		entries += VendorMenuEntry.BANDWIDTH
	}
	entries += VendorMenuEntry.UNBIND
	return VendorMenu(vendor = vendor, entries = entries)
}
