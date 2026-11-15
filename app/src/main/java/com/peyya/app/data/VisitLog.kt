package com.peyya.app.data

import android.content.Context
import android.util.Log
import com.peyya.app.domain.RegionOf
import com.peyya.app.domain.Stop
import com.peyya.app.domain.StopReview
import com.peyya.app.domain.VisitPins
import com.peyya.app.domain.VisitRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Places the family actually arrived at (plus a marked demo history on first run),
 * shared by the trip screen (writes arrivals and ratings) and 마이 (reads the map pins).
 */
class VisitLog private constructor(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _visits = MutableStateFlow(load())
    val visits: StateFlow<List<VisitRecord>> = _visits.asStateFlow()

    fun recordArrival(stop: Stop, atEpochMs: Long = System.currentTimeMillis()) {
        val record = VisitRecord(
            stopId = stop.id,
            placeName = stop.name,
            region = RegionOf.address(stop.address),
            latitude = stop.latitude,
            longitude = stop.longitude,
            visitedAtEpochMs = atEpochMs,
        )
        change { listOf(record) + it }
    }

    fun applyRatings(reviews: List<StopReview>) {
        if (reviews.isNotEmpty()) change { VisitPins.withRatings(it, reviews) }
    }

    private fun change(transform: (List<VisitRecord>) -> List<VisitRecord>) {
        _visits.update(transform)
        prefs.edit().putString(KEY_VISITS, encode(_visits.value)).apply()
    }

    private fun load(): List<VisitRecord> {
        val raw = prefs.getString(KEY_VISITS, null) ?: return DemoVisits.records()
        return runCatching { decode(raw) }.getOrElse {
            Log.w(TAG, "visit log unreadable; starting from demo history", it)
            DemoVisits.records()
        }
    }

    private fun encode(visits: List<VisitRecord>): String = JSONArray().apply {
        visits.forEach { v ->
            put(
                JSONObject()
                    .put("id", v.stopId).put("name", v.placeName).put("region", v.region)
                    .put("lat", v.latitude).put("lon", v.longitude).put("at", v.visitedAtEpochMs)
                    .put("stars", v.stars ?: 0).put("comment", v.comment).put("demo", v.demo),
            )
        }
    }.toString()

    private fun decode(raw: String): List<VisitRecord> {
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            VisitRecord(
                stopId = o.getString("id"),
                placeName = o.getString("name"),
                region = o.getString("region"),
                latitude = o.getDouble("lat"),
                longitude = o.getDouble("lon"),
                visitedAtEpochMs = o.getLong("at"),
                stars = o.optInt("stars", 0).takeIf { it in 1..5 },
                comment = o.optString("comment"),
                demo = o.optBoolean("demo"),
            )
        }
    }

    companion object {
        private const val TAG = "PeyyaVisitLog"
        private const val PREFS = "peyya_visits"
        private const val KEY_VISITS = "visits"

        @Volatile private var instance: VisitLog? = null

        fun get(context: Context): VisitLog = instance ?: synchronized(this) {
            instance ?: VisitLog(context.applicationContext).also { instance = it }
        }
    }
}

/** Sample history so the national map reads well in a demo; every record is labelled 데모. */
object DemoVisits {
    private val seoul = ZoneId.of("Asia/Seoul")

    fun records(): List<VisitRecord> {
        val museum = SeedCatalog.pois[0].stop
        val restaurant = SeedCatalog.pois[1].stop
        val n = NationwideCatalog
        return listOf(
            visit(museum, 2026, 11, 1, 5, "아이가 제일 좋아한 곳. 공룡보다 금관이 인기였어요."),
            visit(restaurant, 2026, 11, 1, 4, "주차가 편하고 아이 메뉴가 있어요."),
            visit(n.gyeongpoBeach, 2026, 10, 18, 5, "해 질 무렵 산책이 최고."),
            visit(n.anmokCafe, 2026, 10, 18, 4, "커피는 좋은데 주말 주차가 빡빡해요."),
            visit(n.bulguksa, 2026, 10, 4, 5, "가을 단풍과 다보탑. 사진 맛집."),
            visit(n.jeonjuHanok, 2026, 9, 20, 4, "한복 입고 사진. 오후엔 사람이 많아요."),
            visit(n.seongsanIlchulbong, 2026, 8, 15, 5, "여름휴가 첫날 일출. 아이도 끝까지 올라감."),
            visit(n.haeundaeBeach, 2026, 7, 26, 3, "물놀이는 좋았지만 너무 붐볐어요."),
        )
    }

    private fun visit(stop: Stop, y: Int, m: Int, d: Int, stars: Int, comment: String) = VisitRecord(
        stopId = stop.id,
        placeName = stop.name,
        region = RegionOf.address(stop.address),
        latitude = stop.latitude,
        longitude = stop.longitude,
        visitedAtEpochMs = LocalDateTime.of(y, m, d, 14, 0).atZone(seoul).toInstant().toEpochMilli(),
        stars = stars,
        comment = comment,
        demo = true,
    )
}
