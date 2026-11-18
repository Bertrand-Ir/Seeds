package app.seeds.run

import app.seeds.domain.Geo
import app.seeds.domain.LatLng
import app.seeds.domain.PolylineCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Snaps a route to real footpaths using the public OSRM foot router run by FOSSGIS
 * (OpenStreetMap data). Falls back to straight lines if offline.
 */
object Router {
    private const val BASE = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/"

    /** Returns the path through [waypoints] along walkable ways, or null if routing failed. */
    suspend fun foot(waypoints: List<LatLng>): List<LatLng>? = withContext(Dispatchers.IO) {
        if (waypoints.size < 2) return@withContext waypoints
        runCatching {
            val coords = waypoints.joinToString(";") { "%.6f,%.6f".format(java.util.Locale.US, it.lon, it.lat) }
            val conn = (URL("$BASE$coords?overview=full&geometries=polyline&steps=false").openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "Seeds-Android (personal run planner)")
                connectTimeout = 12_000
                readTimeout = 15_000
            }
            try {
                if (conn.responseCode !in 200..299) return@runCatching null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (json.optString("code") != "Ok") return@runCatching null
                val geometry = json.getJSONArray("routes").getJSONObject(0).getString("geometry")
                PolylineCodec.decode(geometry).takeIf { it.size >= 2 }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** Straight segments between waypoints, densified so progress tracking stays smooth. */
    fun straight(waypoints: List<LatLng>): List<LatLng> {
        if (waypoints.size < 2) return waypoints
        val out = ArrayList<LatLng>()
        waypoints.zipWithNext { a, b ->
            val n = (Geo.distance(a, b) / 25).toInt().coerceIn(1, 400)
            for (i in 0 until n) out.add(LatLng(a.lat + (b.lat - a.lat) * i / n, a.lon + (b.lon - a.lon) * i / n))
        }
        out.add(waypoints.last())
        return out
    }

    /** Open the same route in Google Maps (walking directions) to double-check it. */
    fun googleMapsUrl(waypoints: List<LatLng>): String {
        fun f(p: LatLng) = "%.6f,%.6f".format(java.util.Locale.US, p.lat, p.lon)
        val origin = waypoints.first()
        val dest = waypoints.last()
        val mids = waypoints.drop(1).dropLast(1).take(9)
        return "https://www.google.com/maps/dir/?api=1&travelmode=walking&origin=${f(origin)}&destination=${f(dest)}" +
            if (mids.isNotEmpty()) "&waypoints=" + mids.joinToString("%7C") { f(it) } else ""
    }
}
