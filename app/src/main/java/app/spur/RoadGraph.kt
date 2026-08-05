package app.spur

internal data class RoadGraphArc<T>(
    val key: String,
    val from: String,
    val to: String,
    val tag: T,
) {
    fun other(node: String): String = if (node == from) to else from
}

internal data class RoadGraphPath<T>(
    val nodes: List<String>,
    val arcs: List<RoadGraphArc<T>>,
)

internal fun <T> linearRoadGraphPaths(
    arcs: Collection<RoadGraphArc<T>>,
    canFollow: (first: T, next: T) -> Boolean = { _, _ -> true },
): List<RoadGraphPath<T>> {
    val adjacency = buildMap<String, MutableList<RoadGraphArc<T>>> {
        arcs.forEach { arc ->
            getOrPut(arc.from, ::mutableListOf) += arc
            getOrPut(arc.to, ::mutableListOf) += arc
        }
    }
    val visited = mutableSetOf<String>()
    val paths = mutableListOf<RoadGraphPath<T>>()

    fun consume(start: String, first: RoadGraphArc<T>) {
        if (first.key in visited) return
        val nodes = mutableListOf(start)
        val pathArcs = mutableListOf<RoadGraphArc<T>>()
        var node = start
        var arc = first
        while (arc.key !in visited) {
            visited += arc.key
            pathArcs += arc
            node = arc.other(node)
            nodes += node
            val connected = adjacency.getValue(node)
            if (connected.size != 2) break
            arc = connected.firstOrNull {
                it.key !in visited && canFollow(first.tag, it.tag)
            } ?: break
        }
        paths += RoadGraphPath(nodes = nodes, arcs = pathArcs)
    }

    adjacency
        .filterValues { it.size != 2 }
        .forEach { (node, connected) -> connected.forEach { consume(node, it) } }
    arcs.forEach { arc -> consume(arc.from, arc) }
    return paths
}
