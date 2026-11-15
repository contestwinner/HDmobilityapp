package com.peyya.app.viewmodel

import com.peyya.app.data.PresetTrips
import com.peyya.app.data.SeedCatalog
import com.peyya.app.domain.CourseCandidate
import com.peyya.app.domain.CourseRecommender
import com.peyya.app.domain.ReviewStore
import com.peyya.app.domain.SentimentLabeler
import com.peyya.app.domain.Stop
import com.peyya.app.domain.StopReview
import com.peyya.app.domain.TripPlan
import com.peyya.app.domain.TripReviewSession

/** Post-trip review flow as pure state transitions (store writes are the only side effect). */
class TripReviewReducer(
    private val reviewStore: ReviewStore,
    /** Notified with the saved ratings (e.g. to pin them on 마이's visit map). */
    private val onCommitted: (List<StopReview>) -> Unit = {},
) {
    fun catalog(): List<Stop> = SeedCatalog.pois.map { it.stop } + PresetTrips.weekendMuseumTrip.stops

    fun courses(): List<CourseCandidate> = CourseRecommender.recommend(reviewStore, catalog())

    fun begin(state: TripUiState): TripUiState {
        val drafts = state.trip.selectedStops().map { StopRatingDraft(stopId = it.id, stopName = it.name) }
        if (drafts.isEmpty()) return skip(state)
        return state.copy(reviewPhase = ReviewUiPhase.Intro, reviewDrafts = drafts, reviewIndex = 0)
    }

    fun continueFromIntro(state: TripUiState): TripUiState =
        state.copy(reviewPhase = ReviewUiPhase.Rating)

    fun update(state: TripUiState, stars: Int, comment: String): TripUiState {
        val idx = state.reviewIndex
        if (idx !in state.reviewDrafts.indices) return state
        val drafts = state.reviewDrafts.toMutableList()
        drafts[idx] = drafts[idx].copy(stars = stars.coerceIn(1, 5), comment = comment)
        return state.copy(reviewDrafts = drafts)
    }

    fun next(state: TripUiState): TripUiState =
        if (state.reviewIndex >= state.reviewDrafts.lastIndex) commit(state)
        else state.copy(reviewIndex = state.reviewIndex + 1)

    fun skip(state: TripUiState): TripUiState {
        reviewStore.save(TripReviewSession(tripId = state.trip.id, reviews = emptyList(), skipped = true))
        return state.copy(reviewPhase = ReviewUiPhase.Done, courseCandidates = courses())
    }

    /** Returns the plan for a recommended course, or null if none of its stops are known. */
    fun planFor(state: TripUiState, candidateId: String): TripPlan? {
        val candidate = state.courseCandidates.firstOrNull { it.id == candidateId } ?: return null
        val byId = catalog().associateBy { it.id }
        val stops = candidate.stopIds.mapNotNull { byId[it]?.copy(selected = true) }
        if (stops.isEmpty()) return null
        return TripPlan(id = candidate.id, title = candidate.title, stops = stops)
    }

    private fun commit(state: TripUiState): TripUiState {
        val reviews = state.reviewDrafts.map {
            StopReview(
                stopId = it.stopId,
                stopName = it.stopName,
                stars = it.stars,
                comment = it.comment,
                sentiment = SentimentLabeler.label(it.stars, it.comment),
            )
        }
        reviewStore.save(TripReviewSession(tripId = state.trip.id, reviews = reviews))
        onCommitted(reviews)
        return state.copy(
            reviewPhase = ReviewUiPhase.Done,
            courseCandidates = courses(),
            statusMessage = "리뷰를 저장했습니다. 코스 추천을 확인해 보세요.",
        )
    }
}
