package app.opaw.ui.walks

import android.graphics.PointF
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.opaw.core.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** A muted light map, like the standard map without points of interest on iOS. No API key needed. */
private const val STYLE = "https://tiles.openfreemap.org/styles/positron"
private const val ROUTE = "route"

/** Where to look. */
sealed interface MapCamera {
    /** Fit the whole route. */
    data object FitRoute : MapCamera
    /** Follow the latest location, as `.userLocation(followsHeading: false)`. */
    data object Follow : MapCamera
}

/**
 * A map with the route drawn as a thick black line. Markers are Compose views placed with
 * [toScreen], so they can be the design's pins, start dot and pet sticker.
 */
@Composable
fun OpawMap(
    points: List<GeoPoint>,
    camera: MapCamera,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    follow: GeoPoint? = points.lastOrNull(),
    bottomPadding: Int = 0,
    markers: @Composable BoxScope.(toScreen: (Double, Double) -> Offset?) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val density = LocalDensity.current
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    // Bumped on every camera move so markers follow the map.
    var moves by remember { mutableIntStateOf(0) }
    val view = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(Bundle())
            getMapAsync { m ->
                map = m
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isTiltGesturesEnabled = false
                m.uiSettings.isCompassEnabled = false
                m.uiSettings.isLogoEnabled = false
                m.addOnCameraMoveListener { moves++ }
                m.addOnCameraIdleListener { moves++ }
                m.setStyle(Style.Builder().fromUri(STYLE)) { style ->
                    style.addSource(GeoJsonSource(ROUTE))
                    style.addLayer(
                        LineLayer("route-line", ROUTE).withProperties(
                            PropertyFactory.lineColor("#111111"),
                            PropertyFactory.lineWidth(7f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        ),
                    )
                    styleReady = true
                }
            }
        }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStart()
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onResume()
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            view.onPause()
            view.onStop()
            view.onDestroy()
        }
    }

    // The route line.
    LaunchedEffect(map, styleReady, points) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val source = m.style?.getSourceAs<GeoJsonSource>(ROUTE) ?: return@LaunchedEffect
        if (points.size >= 2) {
            source.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) })))
        } else {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        }
    }

    // The camera.
    var fitted by remember { mutableStateOf(false) }
    LaunchedEffect(map, camera, follow, points.size, bottomPadding) {
        val m = map ?: return@LaunchedEffect
        val bottom = with(density) { bottomPadding.dp.roundToPx() }
        m.setPadding(0, 0, 0, bottom)
        when (camera) {
            MapCamera.FitRoute -> {
                if (fitted) return@LaunchedEffect
                when {
                    points.size >= 2 -> {
                        val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
                        m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, with(density) { 60.dp.roundToPx() }))
                        fitted = true
                    }
                    points.size == 1 -> {
                        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].latitude, points[0].longitude), 16.0))
                        fitted = true
                    }
                }
            }
            MapCamera.Follow -> follow?.let {
                val target = LatLng(it.latitude, it.longitude)
                if (!fitted) {
                    m.moveCamera(CameraUpdateFactory.newLatLngZoom(target, 16.5))
                    fitted = true
                } else {
                    m.animateCamera(CameraUpdateFactory.newLatLng(target), 600)
                }
            }
        }
    }

    Box(modifier) {
        AndroidView({ view }, Modifier.fillMaxSize(), update = { it.isEnabled = interactive })
        val m = map
        if (m != null) {
            // Reading the counter makes the markers move with the map.
            val tick = moves
            val toScreen: (Double, Double) -> Offset? = { lat, lon ->
                val p: PointF = m.projection.toScreenLocation(LatLng(lat, lon))
                if (tick >= 0) Offset(p.x, p.y) else null
            }
            markers(toScreen)
        }
        if (!interactive) Box(Modifier.fillMaxSize().blockTouches())
    }
}

/** Swallows touches so a static map can't be panned. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
}
