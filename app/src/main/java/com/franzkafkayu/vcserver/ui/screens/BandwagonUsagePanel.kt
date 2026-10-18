package com.franzkafkayu.vcserver.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.franzkafkayu.vcserver.R
import com.franzkafkayu.vcserver.services.BandwagonUsagePoint
import com.franzkafkayu.vcserver.services.BandwagonUsageRange
import com.franzkafkayu.vcserver.services.BandwagonUsageStats
import com.franzkafkayu.vcserver.services.sliceBandwagonUsage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

private val InboundGreen = Color(0xFF4CAF50)
private val OutboundBlue = Color(0xFF2196F3)

@Composable
fun BandwagonUsagePanel(
	stats: BandwagonUsageStats,
	range: BandwagonUsageRange,
	onRange: (BandwagonUsageRange) -> Unit
) {
	val sliced = sliceBandwagonUsage(stats, range)
	val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
	val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.verticalScroll(rememberScrollState()),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(4.dp)
		) {
			BandwagonUsageRange.entries.forEach { option ->
				val selected = option == range
				Surface(
					onClick = { onRange(option) },
					modifier = Modifier
						.weight(1f)
						.height(32.dp),
					shape = RoundedCornerShape(16.dp),
					color = if (selected) {
						MaterialTheme.colorScheme.secondaryContainer
					} else {
						MaterialTheme.colorScheme.surface
					},
					border = BorderStroke(
						1.dp,
						if (selected) {
							MaterialTheme.colorScheme.secondaryContainer
						} else {
							MaterialTheme.colorScheme.outline
						}
					)
				) {
					Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
						Text(
							text = rangeLabel(option),
							style = MaterialTheme.typography.labelSmall,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
							textAlign = TextAlign.Center
						)
					}
				}
			}
		}
		if (sliced.networkInBytes.isNotEmpty() || sliced.networkOutBytes.isNotEmpty()) {
			ChartHeader(
				title = stringResource(R.string.bandwagon_usage_bytes_title),
				legend = listOf(
					InboundGreen to stringResource(R.string.bandwagon_usage_in),
					OutboundBlue to stringResource(R.string.bandwagon_usage_out)
				)
			)
			StackedBarChart(
				inbound = sliced.networkInBytes,
				outbound = sliced.networkOutBytes,
				range = range,
				axisColor = axisColor,
				gridColor = gridColor,
				yFormatter = { formatAxisBytes(it) },
				modifier = Modifier
					.fillMaxWidth()
					.height(200.dp)
			)
		}
		if (sliced.cpuUsage.isNotEmpty()) {
			Text(
				text = stringResource(R.string.bandwagon_usage_cpu_title),
				style = MaterialTheme.typography.titleSmall
			)
			LineChart(
				points = sliced.cpuUsage,
				color = InboundGreen,
				range = range,
				axisColor = axisColor,
				gridColor = gridColor,
				yFormatter = { formatAxisPercent(it) },
				modifier = Modifier
					.fillMaxWidth()
					.height(200.dp)
			)
		}
	}
}

@Composable
private fun ChartHeader(title: String, legend: List<Pair<Color, String>>) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(text = title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
			legend.forEach { (color, label) ->
				Row(
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Box(
						modifier = Modifier
							.size(8.dp)
							.background(color, RoundedCornerShape(2.dp))
					)
					Text(text = label, style = MaterialTheme.typography.labelSmall)
				}
			}
		}
	}
}

@Composable
private fun rangeLabel(range: BandwagonUsageRange): String {
	return stringResource(
		when (range) {
			BandwagonUsageRange.DAY -> R.string.bandwagon_usage_day
			BandwagonUsageRange.WEEK -> R.string.bandwagon_usage_week
			BandwagonUsageRange.MONTH -> R.string.bandwagon_usage_month
			BandwagonUsageRange.YEAR -> R.string.bandwagon_usage_year
			BandwagonUsageRange.MAX -> R.string.bandwagon_usage_max
		}
	)
}

@Composable
private fun StackedBarChart(
	inbound: List<BandwagonUsagePoint>,
	outbound: List<BandwagonUsagePoint>,
	range: BandwagonUsageRange,
	axisColor: Color,
	gridColor: Color,
	yFormatter: (Double) -> String,
	modifier: Modifier = Modifier
) {
	val buckets = alignedBuckets(inbound, outbound)
	Canvas(modifier = modifier.padding(top = 4.dp)) {
		if (buckets.isEmpty()) return@Canvas
		val plot = plotRect()
		val maxY = niceMax(buckets.maxOf { it.inbound + it.outbound }.coerceAtLeast(1.0))
		drawAxes(plot, axisColor, gridColor, maxY, yFormatter, buckets.map { it.timestampSec }, range)
		val barWidth = plot.width / buckets.size
		buckets.forEachIndexed { index, bucket ->
			val inH = ((bucket.inbound / maxY).toFloat() * plot.height).coerceAtLeast(0f)
			val outH = ((bucket.outbound / maxY).toFloat() * plot.height).coerceAtLeast(0f)
			val x = plot.left + index * barWidth + barWidth * 0.15f
			val width = barWidth * 0.7f
			drawRect(
				color = InboundGreen,
				topLeft = Offset(x, plot.bottom - inH),
				size = Size(width, inH)
			)
			drawRect(
				color = OutboundBlue,
				topLeft = Offset(x, plot.bottom - inH - outH),
				size = Size(width, outH)
			)
		}
	}
}

@Composable
private fun LineChart(
	points: List<BandwagonUsagePoint>,
	color: Color,
	range: BandwagonUsageRange,
	axisColor: Color,
	gridColor: Color,
	yFormatter: (Double) -> String,
	modifier: Modifier = Modifier
) {
	Canvas(modifier = modifier.padding(top = 4.dp)) {
		if (points.isEmpty()) return@Canvas
		val plot = plotRect()
		val minX = points.minOf { it.timestampSec }.toFloat()
		val maxX = points.maxOf { it.timestampSec }.toFloat().coerceAtLeast(minX + 1f)
		val maxY = niceMax(points.maxOf { it.value }.coerceAtLeast(1.0))
		drawAxes(plot, axisColor, gridColor, maxY, yFormatter, points.map { it.timestampSec }, range)
		val path = Path()
		points.forEachIndexed { index, point ->
			val x = plot.left + ((point.timestampSec - minX) / (maxX - minX)) * plot.width
			val y = plot.bottom - (point.value / maxY).toFloat() * plot.height
			if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
		}
		val fill = Path().apply {
			addPath(path)
			lineTo(plot.left + ((points.last().timestampSec - minX) / (maxX - minX)) * plot.width, plot.bottom)
			lineTo(plot.left + ((points.first().timestampSec - minX) / (maxX - minX)) * plot.width, plot.bottom)
			close()
		}
		drawPath(fill, color.copy(alpha = 0.25f))
		drawPath(path, color, style = Stroke(width = 3f))
	}
}

private data class PlotRect(val left: Float, val top: Float, val width: Float, val height: Float) {
	val right: Float get() = left + width
	val bottom: Float get() = top + height
}

private fun DrawScope.plotRect(): PlotRect {
	val left = 52.dp.toPx()
	val bottomPad = 22.dp.toPx()
	val top = 8.dp.toPx()
	return PlotRect(left, top, size.width - left - 8.dp.toPx(), size.height - bottomPad - top)
}

private fun DrawScope.drawAxes(
	plot: PlotRect,
	axisColor: Color,
	gridColor: Color,
	maxY: Double,
	yFormatter: (Double) -> String,
	timestamps: List<Long>,
	range: BandwagonUsageRange
) {
	val paint = Paint().apply {
		color = axisColor.toArgb()
		textSize = 10.sp.toPx()
		isAntiAlias = true
		typeface = Typeface.SANS_SERIF
	}
	val yTicks = 4
	for (i in 0..yTicks) {
		val fraction = i / yTicks.toFloat()
		val y = plot.bottom - fraction * plot.height
		drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1f)
		val label = yFormatter(maxY * fraction)
		paint.textAlign = Paint.Align.RIGHT
		drawContext.canvas.nativeCanvas.drawText(label, plot.left - 6.dp.toPx(), y + 3.dp.toPx(), paint)
	}
	drawLine(axisColor, Offset(plot.left, plot.top), Offset(plot.left, plot.bottom), strokeWidth = 1.5f)
	drawLine(axisColor, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), strokeWidth = 1.5f)
	val xLabels = xAxisLabels(timestamps, range)
	paint.textAlign = Paint.Align.CENTER
	val minTs = timestamps.minOrNull() ?: return
	val maxTs = timestamps.maxOrNull()?.coerceAtLeast(minTs + 1) ?: return
	xLabels.forEach { (ts, text) ->
		val x = plot.left + ((ts - minTs).toFloat() / (maxTs - minTs).toFloat()) * plot.width
		drawLine(gridColor, Offset(x, plot.top), Offset(x, plot.bottom), strokeWidth = 1f)
		drawContext.canvas.nativeCanvas.drawText(text, x, plot.bottom + 14.dp.toPx(), paint)
	}
}

private data class UsageBucket(val timestampSec: Long, val inbound: Double, val outbound: Double)

private fun alignedBuckets(
	inbound: List<BandwagonUsagePoint>,
	outbound: List<BandwagonUsagePoint>
): List<UsageBucket> {
	val times = (inbound.map { it.timestampSec } + outbound.map { it.timestampSec }).distinct().sorted()
	if (times.isEmpty()) return emptyList()
	val inMap = inbound.associate { it.timestampSec to it.value }
	val outMap = outbound.associate { it.timestampSec to it.value }
	return times.map { ts -> UsageBucket(ts, inMap[ts] ?: 0.0, outMap[ts] ?: 0.0) }
}

private fun xAxisLabels(timestamps: List<Long>, range: BandwagonUsageRange): List<Pair<Long, String>> {
	if (timestamps.isEmpty()) return emptyList()
	val min = timestamps.first()
	val max = timestamps.last()
	val pattern = when (range) {
		BandwagonUsageRange.DAY -> "HH:mm"
		BandwagonUsageRange.WEEK, BandwagonUsageRange.MONTH -> "MM-dd"
		BandwagonUsageRange.YEAR, BandwagonUsageRange.MAX -> "yyyy-MM"
	}
	val formatter = SimpleDateFormat(pattern, Locale.getDefault())
	val count = 4
	if (max <= min) return listOf(min to formatter.format(Date(min * 1000L)))
	return (0 until count).map { i ->
		val ts = min + (max - min) * i / (count - 1)
		ts to formatter.format(Date(ts * 1000L))
	}
}

private fun niceMax(raw: Double): Double {
	if (raw <= 0.0) return 1.0
	val exp = 10.0.pow(floorLog10(raw))
	val n = raw / exp
	val nice = when {
		n <= 1.0 -> 1.0
		n <= 2.0 -> 2.0
		n <= 5.0 -> 5.0
		else -> 10.0
	}
	return nice * exp
}

private fun floorLog10(value: Double): Double {
	return floorToInt(log10(value)).toDouble()
}

private fun floorToInt(value: Double): Int {
	val asInt = value.toInt()
	return if (value < 0 && asInt.toDouble() != value) asInt - 1 else asInt
}

private fun formatAxisBytes(value: Double): String {
	val units = arrayOf("B", "KB", "MB", "GB", "TB")
	var amount = value
	var unit = 0
	while (amount >= 1024.0 && unit < units.lastIndex) {
		amount /= 1024.0
		unit++
	}
	return if (amount >= 10 || amount % 1.0 == 0.0) {
		"${amount.toInt()}${units[unit]}"
	} else {
		"%.1f%s".format(Locale.US, amount, units[unit])
	}
}

private fun formatAxisPercent(value: Double): String {
	return "${ceil(value).toInt()}%"
}
