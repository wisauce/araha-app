package app.araha.navigation.core

import com.google.gson.Gson
import kotlin.math.*

data class Vec(val x: Double, val z: Double) {
    operator fun plus(v: Vec) = Vec(x + v.x, z + v.z)
    operator fun minus(v: Vec) = Vec(x - v.x, z - v.z)
    operator fun times(t: Double) = Vec(x * t, z * t)
    fun dot(v: Vec) = x * v.x + z * v.z
    fun length() = hypot(x, z)
    fun distance(v: Vec) = (this - v).length()
}
data class Node(val id: String, val position: List<Double>) { fun point() = Vec(position[0], position[2]) }
data class Edge(val id: String, val fromNodeId: String, val toNodeId: String, val halfWidthMeters: Double)
data class Destination(val id: String, val label: String, val destinationNodeId: String, val description: String)
data class Marker(val id: String, val referenceImageName: String, val physicalWidthMeters: Double, val floorFromImage: List<Double>)
data class MapImage(val asset: String, val widthPixels: Int, val heightPixels: Int, val originPixel: List<Double>, val metersPerPixel: Double)
data class FloorMap(
    val schemaVersion: Int, val floorId: String, val floorName: String,
    val calibrationStatus: String, val marker: Marker, val mapImage: MapImage,
    val startNodeId: String, val nodes: List<Node>, val edges: List<Edge>,
    val rooms: List<Destination>, val positionSnapMaxMeters: Double, val arrivalRadiusMeters: Double
) {
    fun validate() {
        require(schemaVersion == 2) { "Unsupported map schema" }
        require(nodes.size >= 2 && edges.isNotEmpty() && rooms.isNotEmpty()) { "Map is empty" }
        require(nodes.map { it.id }.distinct().size == nodes.size) { "Duplicate node" }
        require(edges.map { it.id }.distinct().size == edges.size) { "Duplicate edge" }
        require(rooms.map { it.id }.distinct().size == rooms.size) { "Duplicate destination" }
        require(nodes.all { it.position.size == 3 && it.position.all(Double::isFinite) && abs(it.position[1]) < 0.001 }) { "Invalid floor coordinates" }
        require(marker.physicalWidthMeters.isFinite() && marker.physicalWidthMeters > 0) { "Invalid marker width" }
        require(marker.id.isNotBlank() && marker.referenceImageName.isNotBlank())
        RigidTransform(marker.floorFromImage.toDoubleArray()).validate()
        require(positionSnapMaxMeters.isFinite() && positionSnapMaxMeters > 0 && arrivalRadiusMeters.isFinite() && arrivalRadiusMeters > 0)
        require(mapImage.metersPerPixel.isFinite() && mapImage.metersPerPixel > 0 && mapImage.originPixel.size == 2)
        val byId = nodes.associateBy { it.id }
        require(startNodeId in byId) { "Missing start node" }
        edges.forEach {
            require(it.fromNodeId in byId && it.toNodeId in byId) { "Missing edge endpoint" }
            require(it.halfWidthMeters.isFinite() && it.halfWidthMeters > 0)
            require(byId.getValue(it.fromNodeId).point().distance(byId.getValue(it.toNodeId).point()) > 0.001) { "Zero-length edge" }
        }
        val visited = mutableSetOf(startNodeId)
        var changed = true
        while (changed) {
            changed = false
            for (e in edges) {
                if (e.fromNodeId in visited) changed = visited.add(e.toNodeId) || changed
                if (e.toNodeId in visited) changed = visited.add(e.fromNodeId) || changed
            }
        }
        require(rooms.all { it.destinationNodeId in visited && it.label.isNotBlank() }) { "Destination is disconnected" }
    }
    companion object { fun parse(json: String) = Gson().fromJson(json, FloorMap::class.java).also { it.validate() } }
}

/** Column-major rigid transform; pure Kotlin so alignment can be tested without ARCore. */
class RigidTransform(val m: DoubleArray) {
    fun validate() {
        require(m.size == 16 && m.all(Double::isFinite)) { "Invalid marker transform" }
        require(abs(m[3]) < 1e-5 && abs(m[7]) < 1e-5 && abs(m[11]) < 1e-5 && abs(m[15] - 1) < 1e-5)
        for (a in 0..2) for (b in 0..2) {
            val dot = (0..2).sumOf { m[a * 4 + it] * m[b * 4 + it] }
            require(abs(dot - if (a == b) 1.0 else 0.0) < 1e-4) { "Non-rigid marker transform" }
        }
        val det = m[0]*(m[5]*m[10]-m[9]*m[6])-m[4]*(m[1]*m[10]-m[9]*m[2])+m[8]*(m[1]*m[6]-m[5]*m[2])
        require(abs(det - 1) < 1e-4) { "Reflected marker transform" }
    }
    fun inverse(): RigidTransform {
        val r = DoubleArray(16); r[15] = 1.0
        for (c in 0..2) for (row in 0..2) r[c*4+row] = m[row*4+c]
        for (row in 0..2) r[12+row] = -(0..2).sumOf { r[it*4+row]*m[12+it] }
        return RigidTransform(r)
    }
    operator fun times(other: RigidTransform): RigidTransform = RigidTransform(DoubleArray(16) { i ->
        val row = i % 4; val col = i / 4
        (0..3).sumOf { m[it*4+row]*other.m[col*4+it] }
    })
    fun point(x: Double, y: Double, z: Double) = doubleArrayOf(
        m[0]*x+m[4]*y+m[8]*z+m[12], m[1]*x+m[5]*y+m[9]*z+m[13], m[2]*x+m[6]*y+m[10]*z+m[14])
    fun floats() = FloatArray(16) { m[it].toFloat() }
}
