package com.peyya.app.data

import android.content.Context
import android.util.Log
import com.peyya.app.domain.Stop
import com.peyya.app.domain.TripPhase
import com.peyya.app.domain.TripPhaseCodec
import com.peyya.app.domain.TripPlan
import org.json.JSONArray
import org.json.JSONObject

data class SavedProgress(
    val plan: TripPlan,
    val phase: TripPhase,
)

/**
 * Persists the in-progress route so it survives the head unit powering off.
 * Re-launching the app after a stop is the closest thing to "시동 ON" we can observe.
 */
class TripProgressStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(plan: TripPlan, phase: TripPhase) {
        val json = runCatching { encodePlan(plan).toString() }
            .onFailure { Log.e(TAG, "encode failed; progress not saved", it) }
            .getOrNull() ?: return
        prefs.edit()
            .putString(KEY_PLAN, json)
            .putString(KEY_PHASE, TripPhaseCodec.encode(phase))
            .apply()
    }

    fun load(): SavedProgress? {
        val phase = TripPhaseCodec.decode(prefs.getString(KEY_PHASE, null)) ?: return null
        val raw = prefs.getString(KEY_PLAN, null) ?: return null
        val plan = runCatching { decodePlan(JSONObject(raw)) }
            .onFailure { Log.w(TAG, "stored plan unreadable; discarding", it) }
            .getOrNull() ?: return null
        if (!TripPhaseCodec.isConsistent(phase, plan)) {
            Log.w(TAG, "stored phase references unknown stop; discarding")
            return null
        }
        return SavedProgress(plan, phase)
    }

    fun clear() {
        prefs.edit().remove(KEY_PLAN).remove(KEY_PHASE).apply()
    }

    private fun encodePlan(plan: TripPlan): JSONObject = JSONObject()
        .put("id", plan.id)
        .put("title", plan.title)
        .put("stops", JSONArray(plan.stops.map(::encodeStop)))

    private fun encodeStop(stop: Stop): JSONObject = JSONObject()
        .put("id", stop.id)
        .put("name", stop.name)
        .put("address", stop.address)
        .put("lat", stop.latitude)
        .put("lng", stop.longitude)
        .put("poiId", stop.poiId)
        .put("poiSubId", stop.poiSubId)
        .put("selected", stop.selected)
        .put("needsBooking", stop.needsBooking)
        .put("bookingUrl", stop.bookingUrl ?: "")
        .put("prep", JSONArray(stop.prepChecklist))

    private fun decodePlan(json: JSONObject): TripPlan {
        val stops = json.getJSONArray("stops")
        return TripPlan(
            id = json.getString("id"),
            title = json.getString("title"),
            stops = (0 until stops.length()).map { decodeStop(stops.getJSONObject(it)) },
        )
    }

    private fun decodeStop(json: JSONObject): Stop {
        val prep = json.optJSONArray("prep") ?: JSONArray()
        return Stop(
            id = json.getString("id"),
            name = json.getString("name"),
            address = json.optString("address"),
            latitude = json.getDouble("lat"),
            longitude = json.getDouble("lng"),
            poiId = json.optString("poiId"),
            poiSubId = json.optString("poiSubId", "0"),
            selected = json.optBoolean("selected", true),
            needsBooking = json.optBoolean("needsBooking"),
            bookingUrl = json.optString("bookingUrl").takeIf { it.isNotBlank() },
            prepChecklist = (0 until prep.length()).map { prep.getString(it) },
        )
    }

    companion object {
        private const val TAG = "PeyyaTripProgress"
        private const val PREFS = "peyya_trip_progress"
        private const val KEY_PLAN = "plan_json"
        private const val KEY_PHASE = "phase"
    }
}
