package app.seeds.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lon: Double)

object Geo {
    private const val R = 6_371_000.0

    private fun rad(d: Double) = d * PI / 180.0

    /** Great-circle distance in metres. */
    fun distance(a: LatLng, b: LatLng): Double {
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * R * asin(min(1.0, sqrt(h)))
    }

    fun length(points: List<LatLng>): Double = points.zipWithNext { a, b -> distance(a, b) }.sum()

    /** Distance along the path to each vertex. */
    fun cumulative(points: List<LatLng>): DoubleArray {
        val out = DoubleArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + distance(points[i - 1], points[i])
        return out
    }

    /**
     * Where [p] sits relative to a path: how far off it is (metres) and how far along it the
     * closest point is (metres from the start). Local equirectangular projection — accurate at run scale.
     */
    fun project(p: LatLng, path: List<LatLng>, cum: DoubleArray = cumulative(path)): Pair<Double, Double> {
        if (path.isEmpty()) return Double.MAX_VALUE to 0.0
        if (path.size == 1) return distance(p, path[0]) to 0.0
        val kx = cos(rad(p.lat)) * R * PI / 180.0
        val ky = R * PI / 180.0
        var bestOff = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val ax = (a.lon - p.lon) * kx; val ay = (a.lat - p.lat) * ky
            val bx = (b.lon - p.lon) * kx; val by = (b.lat - p.lat) * ky
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / len2))
            val cx = ax + t * dx; val cy = ay + t * dy
            val off = sqrt(cx * cx + cy * cy)
            if (off < bestOff) {
                bestOff = off
                bestAlong = cum[i] + t * (cum[i + 1] - cum[i])
            }
        }
        return bestOff to bestAlong
    }

    /** "5:42" per km. */
    fun pace(secPerKm: Int): String = if (secPerKm <= 0 || secPerKm > 59 * 60) "–:––" else "%d:%02d".format(secPerKm / 60, secPerKm % 60)

    fun km(m: Double, decimals: Int = 2): String = "%.${decimals}f".format(m / 1000.0)
}

/** Google's encoded polyline format (precision 5) — compact storage for routes and tracks. */
object PolylineCodec {
    fun encode(points: List<LatLng>): String {
        val sb = StringBuilder()
        var pLat = 0L; var pLon = 0L
        for (p in points) {
            val lat = Math.round(p.lat * 1e5); val lon = Math.round(p.lon * 1e5)
            enc(lat - pLat, sb); enc(lon - pLon, sb)
            pLat = lat; pLon = lon
        }
        return sb.toString()
    }

    private fun enc(v: Long, sb: StringBuilder) {
        var x = if (v < 0) (v shl 1).inv() else v shl 1
        while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar()); x = x shr 5 }
        sb.append((x + 63).toInt().toChar())
    }

    fun decode(s: String): List<LatLng> {
        val out = ArrayList<LatLng>()
        var i = 0; var lat = 0L; var lon = 0L
        while (i < s.length) {
            var shift = 0; var result = 0L; var b: Int
            do { b = s[i++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20 && i < s.length)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            shift = 0; result = 0L
            if (i >= s.length) break
            do { b = s[i++].code - 63; result = result or ((b and 0x1f).toLong() shl shift); shift += 5 } while (b >= 0x20 && i < s.length)
            lon += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            out.add(LatLng(lat / 1e5, lon / 1e5))
        }
        return out
    }
}

/** Flexible windows of the day for planning — no exact times. */
enum class DaySlot(val label: String, val hint: String, val startMin: Int) {
    MORNING("Morning", "from waking", 0),
    NOON("Noon", "around midday", 11 * 60 + 30),
    AFTERNOON("Afternoon", "after lunch", 14 * 60),
    EVENING("Evening", "after work", 17 * 60 + 30),
    NIGHT("Night", "before bed", 21 * 60);

    companion object {
        /** Small hours (before 04:00) still belong to the night before. */
        fun at(minuteOfDay: Int): DaySlot =
            if (minuteOfDay < 4 * 60) NIGHT else entries.last { minuteOfDay >= it.startMin }
    }
}
