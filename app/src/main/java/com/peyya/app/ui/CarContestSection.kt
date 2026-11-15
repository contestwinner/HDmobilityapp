package com.peyya.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peyya.app.domain.TripPhase
import com.peyya.app.ui.components.AdaptPanel
import com.peyya.app.ui.components.BookingPrepCard
import com.peyya.app.ui.components.CourseRecommendPanel
import com.peyya.app.ui.components.ReviewPanel
import com.peyya.app.ui.components.VoiceSimChips
import com.peyya.app.viewmodel.TripUiState

/** Callbacks for the contest panels (booking, adapt, voice, review, course) shared by phone and car layouts. */
data class TripPanelCallbacks(
    val onTogglePrep: (String) -> Unit = {},
    val onDismissAdapt: () -> Unit = {},
    val onAcceptParking: (String) -> Unit = {},
    val onVoiceChip: (String) -> Unit = {},
    val onContinueReviewIntro: () -> Unit = {},
    val onUpdateReview: (Int, String) -> Unit = { _, _ -> },
    val onNextReview: () -> Unit = {},
    val onSkipReview: () -> Unit = {},
    val onApplyCourse: (String) -> Unit = {},
)

/**
 * Phase-dependent panels for the car (landscape) layout, so Pleos head units show the same
 * booking prep, day-of adaptation, voice chips, review and course recommendation as the phone.
 */
@Composable
fun CarContestSection(state: TripUiState, panels: TripPanelCallbacks) {
    Column {
        when (state.phase) {
            TripPhase.Idle -> IdlePanels(state, panels)
            is TripPhase.Arrived -> {
                AdaptPanel(
                    suggestions = state.adaptSuggestions,
                    dismissed = state.adaptDismissed,
                    onDismiss = panels.onDismissAdapt,
                    onAcceptParking = panels.onAcceptParking,
                )
                Gap()
                if (!state.vehicleMoving) VoiceSimChips(panels.onVoiceChip)
            }
            is TripPhase.AwaitingNext -> if (!state.vehicleMoving) VoiceSimChips(panels.onVoiceChip)
            else -> Unit
        }
        Gap()
        ReviewPanel(
            phase = state.reviewPhase,
            drafts = state.reviewDrafts,
            index = state.reviewIndex,
            onContinueIntro = panels.onContinueReviewIntro,
            onUpdate = panels.onUpdateReview,
            onNext = panels.onNextReview,
            onSkip = panels.onSkipReview,
        )
    }
}

@Composable
private fun IdlePanels(state: TripUiState, panels: TripPanelCallbacks) {
    state.trip.stops.filter { it.needsBooking }.forEach { stop ->
        BookingPrepCard(stop, state.prepChecked, panels.onTogglePrep)
        Gap()
    }
    CourseRecommendPanel(state.courseCandidates, panels.onApplyCourse)
}

@Composable
private fun Gap() = Spacer(Modifier.height(10.dp))
