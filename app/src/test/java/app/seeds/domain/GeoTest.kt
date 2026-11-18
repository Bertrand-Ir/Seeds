package app.seeds.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test fun polylineRoundTrip() {
        val pts = listOf(LatLng(-1.94412, 30.06190), LatLng(-1.94501, 30.06305), LatLng(-1.95020, 30.07011), LatLng(-1.94412, 30.06190))
        val back = PolylineCodec.decode(PolylineCodec.encode(pts))
        assertEquals(pts.size, back.size)
        pts.zip(back).forEach { (a, b) ->
            assertEquals(a.lat, b.lat, 1e-5)
            assertEquals(a.lon, b.lon, 1e-5)
        }
    }

    @Test fun knownPolyline() {
        // Google's documented example
        val pts = PolylineCodec.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, pts.size)
        assertEquals(38.5, pts[0].lat, 1e-5); assertEquals(-120.2, pts[0].lon, 1e-5)
        assertEquals(43.252, pts[2].lat, 1e-5); assertEquals(-126.453, pts[2].lon, 1e-5)
    }

    @Test fun distanceAndProjection() {
        // ~1.11 km due north along a meridian
        val a = LatLng(0.0, 30.0); val b = LatLng(0.01, 30.0)
        assertEquals(1112.0, Geo.distance(a, b), 2.0)
        // a point 50 m east of the midpoint: 50 m off route, ~556 m along it
        val p = LatLng(0.005, 30.0 + 50.0 / 111_320.0)
        val (off, along) = Geo.project(p, listOf(a, b))
        assertEquals(50.0, off, 1.0)
        assertEquals(556.0, along, 2.0)
    }

    @Test fun pace() {
        assertEquals("5:42", Geo.pace(342))
        assertTrue(Geo.pace(0).startsWith("–"))
    }

    @Test fun daySlots() {
        assertEquals(DaySlot.NIGHT, DaySlot.at(60))          // 01:00 still last night
        assertEquals(DaySlot.MORNING, DaySlot.at(7 * 60))
        assertEquals(DaySlot.NOON, DaySlot.at(12 * 60))
        assertEquals(DaySlot.AFTERNOON, DaySlot.at(15 * 60))
        assertEquals(DaySlot.EVENING, DaySlot.at(19 * 60))
        assertEquals(DaySlot.NIGHT, DaySlot.at(22 * 60))
    }
}
