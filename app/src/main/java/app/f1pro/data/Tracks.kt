package app.f1pro.data

import org.json.JSONObject

/**
 * A circuit outline, already projected flat and scaled so its longer side is 1: [points] holds
 * x, y pairs in 0..1, y pointing down, [w] and [h] are the outline's width and height.
 */
class TrackShape(val w: Float, val h: Float, val points: FloatArray) {
    val size: Int get() = points.size / 2
}

/**
 * Circuit outlines keyed by the calendar's circuit id, from assets/tracks.json. The outlines
 * come from bacinger/f1-circuits (MIT): unofficial, traced from maps, good for a drawing.
 */
object Tracks {
    fun parse(text: String): Map<String, TrackShape> {
        val o = JSONObject(text)
        val out = HashMap<String, TrackShape>()
        for (id in o.keys()) {
            val t = o.optJSONObject(id) ?: continue
            val p = t.optJSONArray("p") ?: continue
            if (p.length() < 6 || p.length() % 2 != 0) continue
            val pts = FloatArray(p.length()) { p.getDouble(it).toFloat() }
            out[id] = TrackShape(t.optDouble("w", 1.0).toFloat(), t.optDouble("h", 1.0).toFloat(), pts)
        }
        return out
    }
}
