package app.araha.navigation.core

import java.util.PriorityQueue
import kotlin.math.*

data class Projection(val point: Vec, val t: Double, val distance: Double)
fun project(point: Vec, a: Vec, b: Vec): Projection {
    val d = b - a
    val t = ((point-a).dot(d) / d.dot(d)).coerceIn(0.0, 1.0)
    val p = a + d*t
    return Projection(p, t, point.distance(p))
}
data class Route(val points: List<Vec>, val corridorWidths: List<Double>) {
    val length get() = points.zipWithNext().sumOf { (a,b) -> a.distance(b) }
}
class Router(private val map: FloorMap) {
    private val nodes = map.nodes.associateBy { it.id }
    fun route(from: Vec, destinationId: String): Route {
        require(from.x.isFinite() && from.z.isFinite())
        val goal = map.rooms.firstOrNull { it.id == destinationId }?.destinationNodeId ?: error("Destination unavailable")
        val candidates = map.edges.map { e -> e to project(from, nodes.getValue(e.fromNodeId).point(), nodes.getValue(e.toNodeId).point()) }
            .sortedBy { it.second.distance }
        require(candidates.isNotEmpty()) { "No mapped corridor is available." }
        val (edge, source) = candidates.first()
        data class Link(val to: String, val distance: Double, val width: Double)
        val graph = mutableMapOf<String, MutableList<Link>>()
        fun connect(a: String, b: String, distance: Double, width: Double) {
            graph.getOrPut(a) { mutableListOf() }.add(Link(b,distance,width))
            graph.getOrPut(b) { mutableListOf() }.add(Link(a,distance,width))
        }
        for (e in map.edges) if (e.id != edge.id) connect(e.fromNodeId,e.toNodeId,nodes.getValue(e.fromNodeId).point().distance(nodes.getValue(e.toNodeId).point()),e.halfWidthMeters)
        val start = "__temporary_source__"
        connect(start,edge.fromNodeId,source.point.distance(nodes.getValue(edge.fromNodeId).point()),edge.halfWidthMeters)
        connect(start,edge.toNodeId,source.point.distance(nodes.getValue(edge.toNodeId).point()),edge.halfWidthMeters)
        val costs = mutableMapOf(start to 0.0)
        val previous = mutableMapOf<String, Pair<String, Double>>()
        val queue = PriorityQueue<Pair<String,Double>>(compareBy { it.second }); queue.add(start to 0.0)
        while (queue.isNotEmpty()) {
            val (id,cost) = queue.remove()
            if (cost > costs.getValue(id)) continue
            if (id == goal) break
            for (link in graph[id].orEmpty()) {
                val nextCost = cost + link.distance
                if (nextCost < (costs[link.to] ?: Double.POSITIVE_INFINITY)) {
                    costs[link.to] = nextCost; previous[link.to] = id to link.width; queue.add(link.to to nextCost)
                }
            }
        }
        require(goal in costs) { "No route to destination" }
        val reversed = mutableListOf<Pair<Vec,Double>>()
        var id = goal
        while (id != start) {
            val prev = previous.getValue(id)
            reversed.add(nodes.getValue(id).point() to prev.second); id = prev.first
        }
        // An uncertain source starts on the nearest mapped segment, without inventing
        // a straight walkable connection through unmapped space to that segment.
        val onCorridor = source.distance <= min(edge.halfWidthMeters,map.positionSnapMaxMeters)
        val points = mutableListOf(if(onCorridor) from else source.point); val widths = mutableListOf<Double>()
        fun append(p: Vec, w: Double) { if (points.last().distance(p) > 0.01) { points.add(p); widths.add(w) } }
        append(source.point,edge.halfWidthMeters)
        for ((p,w) in reversed.asReversed()) append(p,w)
        return Route(points,widths)
    }
}

data class Progress(val segment: Int, val remaining: Double, val nextDistance: Double, val instruction: String, val arrived: Boolean, val onRoute: Boolean)
class RouteProgress(private val route: Route, private val radius: Double) {
    private var segment = 0
    private var arrivedSince: Long? = null
    fun interruptArrival() { arrivedSince = null }
    fun update(position: Vec, timeMs: Long): Progress {
        if (route.points.size > 1 && segment < route.points.size-2) {
            val next = route.points[segment+1]
            // Advance one segment per observation, only near its physical endpoint.
            if (position.distance(next) <= 1.0) segment++
        }
        val final = segment >= route.points.size-2
        val destination = route.points.last()
        val inside = route.points.size<2 || project(position,route.points[segment],route.points[segment+1]).distance<=route.corridorWidths[segment]
        val near = position.distance(destination) <= radius && final && inside
        if (near) { if (arrivedSince == null) arrivedSince = timeMs } else arrivedSince = null
        val arrived = arrivedSince?.let { timeMs-it >= 1000 } ?: false
        if (route.points.size < 2) return Progress(0,position.distance(destination),0.0,"You are at your destination",arrived,true)
        val a=route.points[segment]; val b=route.points[segment+1]
        val projected=project(position,a,b)
        val left = projected.point.distance(b) + route.points.drop(segment+1).zipWithNext().sumOf { (x,y)->x.distance(y) }
        val instruction = if(final) "Continue to the entrance" else {
            val next=route.points[segment+2]-b; val current=b-a
            val angle=atan2(current.x*next.z-current.z*next.x,current.dot(next))*180/Math.PI
            when { abs(angle)>150 -> "Turn around"; angle>25 -> "Turn right"; angle < -25 -> "Turn left"; else -> "Continue straight" }
        }
        return Progress(segment,left,position.distance(b),instruction,arrived,projected.distance <= route.corridorWidths[segment])
    }
}
