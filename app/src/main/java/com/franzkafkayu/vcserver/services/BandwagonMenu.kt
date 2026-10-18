package com.franzkafkayu.vcserver.services

enum class BandwagonMenuEntry {
	BIND,
	START,
	STOP,
	RESTART,
	STATUS,
	UNBIND
}

fun bandwagonMenuEntries(selectionMode: Boolean, bound: Boolean): List<BandwagonMenuEntry> {
	if (selectionMode) return emptyList()
	if (!bound) return listOf(BandwagonMenuEntry.BIND)
	return listOf(
		BandwagonMenuEntry.START,
		BandwagonMenuEntry.STOP,
		BandwagonMenuEntry.RESTART,
		BandwagonMenuEntry.STATUS,
		BandwagonMenuEntry.UNBIND
	)
}
