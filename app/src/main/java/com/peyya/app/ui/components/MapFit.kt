package com.peyya.app.ui.components

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.tan

/** A Web-Mercator viewport shared by the Leaflet base map and the Compose photo-pin overlay. */
data class MapFrame(
    val centerLat: Double,
    val centerLon: Double,
    /** Fractional Leaflet zoom (requires `zoomSnap: 0`). */
    val zoom: Double,
    private val centerX: Double,
    private val centerY: Double,
    private val pxPerUnit: Double,
    private val widthPx: Float,
    private val heightPx: Float,
) {
    /** Projects a coordinate to device pixels inside the frame; matches Leaflet exactly. */
    fun project(lat: Double, lon: Double): Pair<Float, Float> {
        val x = widthPx / 2 + (MapFit.mercX(lon) - centerX) * pxPerUnit
        val y = heightPx / 2 + (MapFit.mercY(lat) - centerY) * pxPerUnit
        return x.toFloat() to y.toFloat()
    }
}

object MapFit {
    private const val TILE = 256.0
    private const val MIN_SPAN_DEG = 0.008
    private const val MAX_ZOOM = 16.0

    fun mercX(lon: Double): Double = (lon + 180.0) / 360.0

    fun mercY(lat: Double): Double {
        val rad = lat * PI / 180.0
        return (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0
    }

    private fun latOf(y: Double): Double = atan(sinh(PI * (1.0 - 2.0 * y))) * 180.0 / PI

    /**
     * Fits [points] (lat, lon) into a [widthPx]×[heightPx] box leaving [padding] (0..0.5) on each side.
     * [density] converts Leaflet CSS pixels to device pixels.
     */
    fun fit(
        points: List<Pair<Double, Double>>,
        widthPx: Float,
        heightPx: Float,
        density: Float,
        padding: Double = 0.2,
    ): MapFrame? {
        if (points.isEmpty() || widthPx <= 0f || heightPx <= 0f) return null
        val xs = points.map { mercX(it.second) }
        val ys = points.map { mercY(it.first) }
        val minSpan = MIN_SPAN_DEG / 360.0
        val spanX = (xs.max() - xs.min()).coerceAtLeast(minSpan)
        val spanY = (ys.max() - ys.min()).coerceAtLeast(minSpan)
        val usable = 1.0 - 2.0 * padding
        val tilePx = TILE * density
        val maxScale = tilePx * Math.pow(2.0, MAX_ZOOM)
        val scale = min(min(widthPx * usable / spanX, heightPx * usable / spanY), maxScale)
        val cx = (xs.max() + xs.min()) / 2.0
        val cy = (ys.max() + ys.min()) / 2.0
        return MapFrame(
            centerLat = latOf(cy),
            centerLon = cx * 360.0 - 180.0,
            zoom = log2(scale / tilePx),
            centerX = cx,
            centerY = cy,
            pxPerUnit = scale,
            widthPx = widthPx,
            heightPx = heightPx,
        )
    }
}
