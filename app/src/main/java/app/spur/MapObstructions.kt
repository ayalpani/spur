package app.spur

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import kotlin.math.abs

/**
 * Window bounds of the map chrome (header, control rails, bottom bars) that edge
 * indicators must stay clear of. Snapshot state, so indicators re-place themselves
 * whenever chrome is measured again (for example after fading back in).
 */
internal class MapObstructions {
    private val bounds = mutableStateMapOf<String, Rect>()

    fun update(key: String, rect: Rect) {
        if (bounds[key] != rect) bounds[key] = rect
    }

    fun remove(key: String) {
        bounds.remove(key)
    }

    fun windowBounds(): List<Rect> = bounds.values.toList()
}

/**
 * Registers this element's measured window bounds as map chrome. A zero-size layout
 * (for example a faded-out control) keeps its last bounds so indicators do not jump
 * while controls fade around a gesture; pass [active] = false when the element is
 * structurally absent.
 */
internal fun Modifier.mapObstruction(
    key: String,
    obstructions: MapObstructions,
    active: Boolean = true,
): Modifier = if (active) this then MapObstructionElement(key, obstructions) else this

private data class MapObstructionElement(
    val key: String,
    val obstructions: MapObstructions,
) : ModifierNodeElement<MapObstructionNode>() {
    override fun create() = MapObstructionNode(key, obstructions)

    override fun update(node: MapObstructionNode) {
        if (node.key != key || node.obstructions !== obstructions) {
            node.obstructions.remove(node.key)
            node.key = key
            node.obstructions = obstructions
        }
    }
}

private class MapObstructionNode(
    var key: String,
    var obstructions: MapObstructions,
) : Modifier.Node(), GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val rect = coordinates.boundsInWindow()
        if (rect.width > 0f && rect.height > 0f) obstructions.update(key, rect)
    }

    override fun onDetach() {
        obstructions.remove(key)
    }
}

/**
 * Places one marker-and-label block of [width] x [height], wanted at [left]/[top], inside
 * [area] without overlapping any of [obstacles]. A blocked block moves the shortest
 * distance that clears every obstacle, counting moves away from its screen edge double
 * so it prefers sliding along that edge past a corner button. [alongHorizontalEdge] is
 * true for blocks on the top or bottom edge.
 */
internal fun resolveIndicatorBlock(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    area: LandmarkIndicatorBounds,
    obstacles: List<LandmarkIndicatorBounds>,
    gap: Float,
    alongHorizontalEdge: Boolean,
): LandmarkIndicatorBounds {
    val horizontalCost = if (alongHorizontalEdge) 1f else 2f
    val verticalCost = if (alongHorizontalEdge) 2f else 1f
    val wantedLeft = left
    val wantedTop = top
    fun at(position: Pair<Float, Float>) = LandmarkIndicatorBounds(
        left = position.first.coerceIn(area.left, maxOf(area.left, area.right - width)),
        top = position.second.coerceIn(area.top, maxOf(area.top, area.bottom - height)),
        right = 0f,
        bottom = 0f,
    ).let { it.copy(right = it.left + width, bottom = it.top + height) }
    fun LandmarkIndicatorBounds.isFree() = obstacles.none { it.overlaps(this) }
    fun LandmarkIndicatorBounds.distance() =
        abs(this.left - wantedLeft) * horizontalCost + abs(this.top - wantedTop) * verticalCost
    fun LandmarkIndicatorBounds.neighbours() = obstacles.flatMap { obstacle ->
        listOf(
            obstacle.left - gap - width to this.top,
            obstacle.right + gap to this.top,
            this.left to obstacle.top - gap - height,
            this.left to obstacle.bottom + gap,
        )
    }.map(::at)

    val start = at(wantedLeft to wantedTop)
    if (start.isFree()) return start
    // Two steps reach corners, where a block must clear a bar and a button at once.
    val firstStep = start.neighbours()
    return (firstStep + firstStep.flatMap { it.neighbours() })
        .filter { it.isFree() }
        .minByOrNull { it.distance() }
        ?: start
}

private fun LandmarkIndicatorBounds.overlaps(other: LandmarkIndicatorBounds): Boolean =
    left < other.right && other.left < right && top < other.bottom && other.top < bottom

internal fun LandmarkIndicatorBounds.outset(amount: Float): LandmarkIndicatorBounds =
    inset(-amount)

/** Blocks already placed in the current layout pass, so later indicators avoid them. */
internal class EdgeIndicatorPlacements {
    var location: LandmarkIndicatorBounds? = null
}

internal fun LandmarkIndicatorBounds.inset(amount: Float): LandmarkIndicatorBounds =
    LandmarkIndicatorBounds(
        left = left + amount,
        top = top + amount,
        right = right - amount,
        bottom = bottom - amount,
    )
