package app.araha.navigation.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NavigationTest {
    private fun map() = FloorMap.parse(File("src/main/assets/hotel-floor.json").readText())
    @Test fun bundledMapIsConnectedAndPreservesCorridorTurns() {
        val map=map()
        val route=Router(map).route(map.nodes.first().point(),"grand-ballroom")
        assertEquals(5,route.points.size)
        assertEquals(19.57,route.length,0.02)
        assertEquals(map.nodes.last().point(),route.points.last())
    }
    @Test fun routingStartsFromLivePositionWithoutReturningToQr() {
        val map=map();val from=map.nodes[2].point()
        val route=Router(map).route(from,"grand-ballroom")
        assertEquals(from,route.points.first())
        assertTrue(route.length<4)
        assertFalse(route.points.contains(map.nodes.first().point()))
    }
    @Test fun offCorridorPositionStillGetsMappedRoute() {
        val map=map()
        val from=map.nodes.first().point()+Vec(1.2,0.0)
        val route=Router(map).route(from,"grand-ballroom")
        assertEquals(map.nodes.last().point(),route.points.last())
        assertNotEquals(from,route.points.first())
        assertEquals(map.nodes.first().point(),route.points.first())
    }
    @Test fun corridorUncertaintyDoesNotBlockTurnProgress() {
        val tracker=RouteProgress(Route(listOf(Vec(0.0,0.0),Vec(0.0,5.0),Vec(5.0,5.0)),listOf(.32,.32)),.65)
        val before=tracker.update(Vec(.8,2.0),0)
        assertFalse(before.onRoute)
        assertEquals(0,before.segment)
        assertEquals(1,tracker.update(Vec(.8,5.0),100).segment)
    }
    @Test fun arrivingRequiresTraversingTurnsAndDwelling() {
        val r=Route(listOf(Vec(0.0,0.0),Vec(0.0,5.0),Vec(5.0,5.0)),listOf(.5,.5))
        val tracker=RouteProgress(r,.6)
        assertFalse(tracker.update(Vec(5.0,5.0),0).arrived)
        assertFalse(tracker.update(Vec(5.0,5.0),2000).arrived)
        tracker.update(Vec(0.0,5.0),3000)
        assertFalse(tracker.update(Vec(5.0,5.0),4000).arrived)
        assertTrue(tracker.update(Vec(5.0,5.0),5001).arrived)
    }
    @Test fun trackingInterruptionResetsArrivalDwell() {
        val tracker=RouteProgress(Route(listOf(Vec(0.0,0.0),Vec(0.0,5.0)),listOf(.5)),.6)
        tracker.update(Vec(0.0,5.0),0);tracker.interruptArrival()
        assertFalse(tracker.update(Vec(0.0,5.0),5000).arrived)
        assertTrue(tracker.update(Vec(0.0,5.0),6001).arrived)
    }
    @Test fun routeUsesShortestBranchInGraph() {
        val base=map()
        val nodes=listOf(Node("a",listOf(0.0,0.0,0.0)),Node("b",listOf(0.0,0.0,4.0)),Node("c",listOf(3.0,0.0,4.0)),Node("d",listOf(5.0,0.0,0.0)))
        val m=base.copy(startNodeId="a",nodes=nodes,edges=listOf(Edge("ab","a","b",.4),Edge("bc","b","c",.4),Edge("ad","a","d",.4),Edge("dc","d","c",.4)),rooms=listOf(Destination("goal","Goal","c","")))
        m.validate()
        val route=Router(m).route(Vec(0.0,0.0),"goal")
        assertEquals(7.0,route.length,.001)
        assertEquals(nodes[1].point(),route.points[1])
    }
    @Test fun disconnectedDestinationsAreInvalid() {
        val base=map();val bad=base.copy(edges=base.edges.dropLast(1))
        assertThrows(IllegalArgumentException::class.java) { bad.validate() }
    }
    @Test fun oppositeDoorImageFacesEastAndInverseIsCorrect() {
        val transform=RigidTransform(map().marker.floorFromImage.toDoubleArray());transform.validate()
        assertArrayEquals(doubleArrayOf(0.0,1.0,-1.0),transform.point(1.0,0.0,0.0),.00001)
        assertArrayEquals(doubleArrayOf(1.0,1.0,0.0),transform.point(0.0,1.0,0.0),.00001)
        assertArrayEquals(doubleArrayOf(0.0,0.0,0.0),transform.point(0.0,0.0,1.0),.00001)
        val identity=transform*transform.inverse()
        assertArrayEquals(doubleArrayOf(3.0,2.0,7.0),identity.point(3.0,2.0,7.0),.00001)
    }
    @Test fun calibrationCancelsArWorldOriginAndRotation() {
        val worldImage=RigidTransform(doubleArrayOf(0.0,0.0,-1.0,0.0,0.0,1.0,0.0,0.0,1.0,0.0,0.0,0.0,9.0,2.0,3.0,1.0))
        val floorImage=RigidTransform(map().marker.floorFromImage.toDoubleArray())
        val worldFloor=worldImage*floorImage.inverse()
        val worldPoint=worldFloor.point(-12.0,1.4,.7)
        assertArrayEquals(doubleArrayOf(-12.0,1.4,.7),worldFloor.inverse().point(worldPoint[0],worldPoint[1],worldPoint[2]),.00001)
    }
}
