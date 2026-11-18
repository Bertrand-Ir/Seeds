package app.seeds.ui.run

import android.graphics.Color as AColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.seeds.domain.LatLng
import app.seeds.ui.theme.SeedsTheme
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos

val KIGALI = LatLng(-1.9441, 30.0619)

private fun LatLng.gp() = GeoPoint(lat, lon)

/** Where the map should look. Bump [key] to re-apply. */
data class Camera(val center: LatLng? = null, val zoom: Double = 16.0, val fit: List<LatLng>? = null, val key: Int = 0)

/**
 * OpenStreetMap view for planning and tracking. Planned route in brand green, your actual path in
 * sprout, waypoints as numbered dots, and you as a pulsing ring.
 */
@Composable
fun SeedsMap(
    modifier: Modifier,
    camera: Camera,
    planned: List<LatLng> = emptyList(),
    track: List<LatLng> = emptyList(),
    waypoints: List<LatLng> = emptyList(),
    me: LatLng? = null,
    followMe: Boolean = false,
    onTap: ((LatLng) -> Unit)? = null,
) {
    val colors = SeedsTheme.colors
    val dark = colors.isDark
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { MapHolder() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                minZoomLevel = 4.0
                maxZoomLevel = 19.5
                controller.setZoom(camera.zoom)
                controller.setCenter((camera.center ?: KIGALI).gp())
                if (dark) overlayManager.tilesOverlay.setColorFilter(darkFilter())
                state.map = this
            }
        },
        update = { map ->
            map.overlays.clear()
            onTap?.let { tap ->
                map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean { tap(LatLng(p.latitude, p.longitude)); return true }
                    override fun longPressHelper(p: GeoPoint): Boolean = false
                }))
            }
            if (planned.size >= 2) {
                map.overlays.add(line(map, planned, colors.accent.copy(alpha = if (track.isEmpty()) 0.95f else 0.45f).toArgb(), 9f, casing = true))
            }
            if (track.size >= 2) map.overlays.add(line(map, track, (if (dark) colors.sprout else Color(0xFF2F9E5A)).toArgb(), 8f, casing = true))
            waypoints.forEachIndexed { i, p ->
                map.overlays.add(dot(map, p, if (i == 0) colors.accent.toArgb() else AColor.WHITE, if (i == 0) AColor.WHITE else colors.accent.toArgb(), 14))
            }
            me?.let { map.overlays.add(dot(map, it, 0xFF2F80ED.toInt(), AColor.WHITE, 18)) }

            if (state.cameraKey != camera.key) {
                state.cameraKey = camera.key
                val fit = camera.fit
                if (fit != null && fit.size >= 2) {
                    map.post {
                        runCatching {
                            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(fit.map { it.gp() }).increaseByScale(1.25f), false)
                        }
                    }
                } else camera.center?.let {
                    map.controller.setZoom(camera.zoom)
                    map.controller.setCenter(it.gp())
                }
            }
            if (followMe && me != null) map.controller.animateTo(me.gp())
            map.invalidate()
        },
    )

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> state.map?.onResume()
                Lifecycle.Event.ON_PAUSE -> state.map?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            state.map?.onDetach()
        }
    }
}

private class MapHolder {
    var map: MapView? = null
    var cameraKey: Int = -1
}

private fun line(map: MapView, pts: List<LatLng>, color: Int, width: Float, casing: Boolean): Polyline =
    Polyline(map).apply {
        setPoints(pts.map { it.gp() })
        outlinePaint.apply {
            this.color = color
            strokeWidth = width * map.resources.displayMetrics.density / 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        if (casing) outlinePaint.setShadowLayer(3f, 0f, 0f, 0x55000000)
        infoWindow = null
    }

private fun dot(map: MapView, p: LatLng, fill: Int, stroke: Int, sizeDp: Int): Marker =
    Marker(map).apply {
        position = p.gp()
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        val px = (sizeDp * map.resources.displayMetrics.density).toInt()
        icon = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            setStroke((3 * map.resources.displayMetrics.density).toInt(), stroke)
            setSize(px, px)
        }
        infoWindow = null
        setOnMarkerClickListener { _, _ -> true }
    }

/** Inverted, desaturated, slightly green-tinted tiles that sit nicely on Soil. */
private fun darkFilter(): ColorMatrixColorFilter {
    val invert = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val desat = ColorMatrix().apply { setSaturation(0.25f) }
    val dim = ColorMatrix(
        floatArrayOf(
            0.82f, 0f, 0f, 0f, -6f,
            0f, 0.86f, 0f, 0f, -4f,
            0f, 0f, 0.82f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    invert.postConcat(desat)
    invert.postConcat(dim)
    return ColorMatrixColorFilter(invert)
}

/** Lightweight route silhouette for cards — no map tiles needed. */
@Composable
fun RouteShape(points: List<LatLng>, color: Color, modifier: Modifier, stroke: Float = 5f) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val k = cos(Math.toRadians(points.first().lat))
        val xs = points.map { it.lon * k }
        val ys = points.map { -it.lat }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val w = (maxX - minX).coerceAtLeast(1e-9); val h = (maxY - minY).coerceAtLeast(1e-9)
        val pad = stroke * 2
        val scale = minOf((size.width - pad * 2) / w, (size.height - pad * 2) / h)
        val ox = (size.width - w * scale) / 2; val oy = (size.height - h * scale) / 2
        fun o(i: Int) = Offset((ox + (xs[i] - minX) * scale).toFloat(), (oy + (ys[i] - minY) * scale).toFloat())
        val path = Path().apply { moveTo(o(0).x, o(0).y); for (i in 1 until points.size) lineTo(o(i).x, o(i).y) }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, stroke * 1.2f, o(0))
    }
}
