package com.peyya.app.viewmodel

import com.peyya.app.domain.AdaptSuggestion
import com.peyya.app.domain.CourseCandidate
import com.peyya.app.domain.FamilyProfile
import com.peyya.app.domain.GearState
import com.peyya.app.domain.TripPhase
import com.peyya.app.domain.TripPlan

enum class ReviewUiPhase {
    None,
    Intro,
    Rating,
    Done,
}

data class StopRatingDraft(
    val stopId: String,
    val stopName: String,
    val stars: Int = 5,
    val comment: String = "",
)

data class TripUiState(
    val family: FamilyProfile,
    val trip: TripPlan,
    val phase: TripPhase = TripPhase.Idle,
    val mockMode: Boolean = true,
    val statusMessage: String = "경유지를 확인한 뒤 오늘 루트를 시작하세요.",
    val lastSpoken: String = "",
    val adaptSuggestions: List<AdaptSuggestion> = emptyList(),
    val adaptDismissed: Boolean = false,
    val prepChecked: Set<String> = emptySet(),
    val reviewPhase: ReviewUiPhase = ReviewUiPhase.None,
    val reviewDrafts: List<StopRatingDraft> = emptyList(),
    val reviewIndex: Int = 0,
    val courseCandidates: List<CourseCandidate> = emptyList(),
    /** Latest gear from the Vehicle SDK; stays [GearState.Unknown] on phones / mock. */
    val gear: GearState = GearState.Unknown,
) {
    /** True while the car is in D/R: editing UIs are locked (driver distraction). */
    val vehicleMoving: Boolean get() = gear.moving

    /** True once real gear data arrived, so the UI can explain the automatic flow. */
    val vehicleLive: Boolean get() = gear != GearState.Unknown
}
