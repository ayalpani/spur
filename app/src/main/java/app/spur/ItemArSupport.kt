package app.spur

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.ar.core.Anchor
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.node.CylinderNode
import io.github.sceneview.node.ModelNode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.math.roundToInt

internal class ItemArLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = registry

    init {
        registry.currentState = Lifecycle.State.INITIALIZED
    }

    fun moveTo(state: Lifecycle.State) {
        if (registry.currentState != Lifecycle.State.DESTROYED) {
            registry.currentState = state.coerceAtMost(Lifecycle.State.RESUMED)
        }
    }
}

internal data class FruitAnchor(
    val root: AnchorNode,
    val model: ModelNode,
    val localOffset: LocalArOffset,
    val baseHeightMeters: Float,
)

internal data class PublicAnchorPlan(
    val idsToRemove: Set<String>,
    val idsToCreate: Set<String>,
)

private const val ItemGuideBaseRadiusMeters = 0.12f
private const val ItemGuideBaseHeightMeters = 0.02f
private const val ItemGuideLineRadiusMeters = 0.0075f

internal fun planPublicAnchors(
    existingIds: Set<String>,
    visibleIds: Set<String>,
    sessionChanged: Boolean,
): PublicAnchorPlan {
    val retainedIds = if (sessionChanged) emptySet() else existingIds intersect visibleIds
    return PublicAnchorPlan(
        idsToRemove = existingIds - retainedIds,
        idsToCreate = visibleIds - retainedIds,
    )
}

@Composable
internal fun PlacementGuidance(
    validGroundHit: Boolean,
    waitingForLocation: Boolean,
    modifier: Modifier,
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = Color.White) {
        Text(
            text = when {
                waitingForLocation -> "Warte auf einen frischen, genauen Standort …"
                validGroundHit -> "Tippe, um die Vorschau hier schweben zu lassen"
                else -> "Bewege dein Handy und visiere die Stelle am Boden an"
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
internal fun ItemDirectionGuide(
    item: PublicItem,
    distanceMeters: Double,
    deviceLocation: ItemLocation?,
    northBearing: Double?,
    modifier: Modifier,
) {
    val targetBearing = if (deviceLocation != null) {
        bearingDegrees(deviceLocation, item.location)
    } else {
        0.0
    }
    val relative = ((targetBearing - (northBearing ?: targetBearing) + 540) % 360 - 180).toFloat()
    Surface(modifier = modifier, shape = CircleShape, color = Color.White, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "➤",
                modifier = Modifier.graphicsLayer(rotationZ = relative),
                style = MaterialTheme.typography.titleLarge,
            )
            Image(
                painter = painterResource(item.kind.mapImageResource),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = if (distanceMeters <= ItemDiscoveryRadiusMeters) {
                    "${distanceMeters.roundToInt()} m"
                } else {
                    "Noch nicht in AR sichtbar"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

internal fun createFruitAnchor(
    engine: Engine,
    modelLoader: ModelLoader,
    guideMaterial: MaterialInstance,
    anchor: Anchor,
    kind: ItemKind,
    heightMeters: Float,
    localOffset: LocalArOffset,
): FruitAnchor {
    val root = AnchorNode(engine, anchor).apply { isPositionEditable = false }
    val lineHeight = ItemPlacementHeightMeters - ItemGuideBaseHeightMeters
    CylinderNode(
        engine = engine,
        radius = ItemGuideLineRadiusMeters,
        height = lineHeight,
        center = Position(y = -lineHeight / 2f),
        materialInstance = guideMaterial,
    ).apply {
        isTouchable = false
        parent = root
    }
    CylinderNode(
        engine = engine,
        radius = ItemGuideBaseRadiusMeters,
        height = ItemGuideBaseHeightMeters,
        center = Position(
            y = -ItemPlacementHeightMeters + ItemGuideBaseHeightMeters / 2f,
        ),
        materialInstance = guideMaterial,
    ).apply {
        isTouchable = false
        parent = root
    }
    val model = ModelNode(
        modelInstance = modelLoader.createModelInstance(kind.modelAsset),
        scaleToUnits = ItemSemanticSizeMeters,
        centerOrigin = Position(0f, -1f, 0f),
    ).apply {
        position = Position(0f, heightMeters, 0f)
        isPositionEditable = false
        parent = root
    }
    return FruitAnchor(root, model, localOffset, heightMeters)
}

@Composable
internal fun rememberNorthBearing(location: Location?): Float? {
    val context = LocalContext.current
    val currentLocation by rememberUpdatedState(location)
    var bearing by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotation = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                val magneticCameraBearing = Math.toDegrees(
                    kotlin.math.atan2(-rotation[2].toDouble(), -rotation[5].toDouble()),
                ).toFloat()
                val declination = currentLocation?.let {
                    GeomagneticField(
                        it.latitude.toFloat(),
                        it.longitude.toFloat(),
                        it.altitude.toFloat(),
                        System.currentTimeMillis(),
                    ).declination
                } ?: 0f
                bearing = smoothBearingDegrees(
                    current = bearing?.toDouble(),
                    candidate = (magneticCameraBearing + declination).toDouble(),
                ).toFloat()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager?.unregisterListener(listener) }
    }
    return bearing
}
