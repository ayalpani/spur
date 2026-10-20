package app.spur

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement

/**
 * Window bounds of the map chrome (header, control rails, bottom bars) that edge
 * indicators must stay clear of. Read on the main thread when indicators are published.
 */
internal class MapObstructions {
    private val bounds = LinkedHashMap<String, Rect>()

    fun update(key: String, rect: Rect) {
        bounds[key] = rect
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
 * Shrinks these bounds so none of [obstructions] overlaps them. Each obstruction that
 * reaches within [attachDistance] of one or more sides pushes back the side that loses
 * the least area, so a tall rail narrows the map from the side and a wide bar shortens
 * it from above or below. Obstructions floating further inside are ignored.
 */
internal fun LandmarkIndicatorBounds.avoiding(
    obstructions: List<LandmarkIndicatorBounds>,
    gap: Float,
    attachDistance: Float,
): LandmarkIndicatorBounds {
    var result = this
    obstructions
        .sortedByDescending { (it.right - it.left) * (it.bottom - it.top) }
        .forEach { obstruction ->
            val current = result
            if (
                obstruction.right <= current.left || obstruction.left >= current.right ||
                obstruction.bottom <= current.top || obstruction.top >= current.bottom
            ) return@forEach
            val width = current.right - current.left
            val height = current.bottom - current.top
            val candidates = buildList {
                if (obstruction.top <= current.top + attachDistance) {
                    val top = obstruction.bottom + gap
                    add((top - current.top) * width to current.copy(top = top))
                }
                if (obstruction.bottom >= current.bottom - attachDistance) {
                    val bottom = obstruction.top - gap
                    add((current.bottom - bottom) * width to current.copy(bottom = bottom))
                }
                if (obstruction.left <= current.left + attachDistance) {
                    val left = obstruction.right + gap
                    add((left - current.left) * height to current.copy(left = left))
                }
                if (obstruction.right >= current.right - attachDistance) {
                    val right = obstruction.left - gap
                    add((current.right - right) * height to current.copy(right = right))
                }
            }
            candidates
                .filter { (_, bounds) -> bounds.left < bounds.right && bounds.top < bounds.bottom }
                .minByOrNull { (loss, _) -> loss }
                ?.let { (_, bounds) -> result = bounds }
        }
    return result
}

internal fun LandmarkIndicatorBounds.inset(amount: Float): LandmarkIndicatorBounds =
    LandmarkIndicatorBounds(
        left = left + amount,
        top = top + amount,
        right = right - amount,
        bottom = bottom - amount,
    )
