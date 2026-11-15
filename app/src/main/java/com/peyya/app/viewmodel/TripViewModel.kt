package com.peyya.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.peyya.app.data.PresetTrips
import com.peyya.app.data.TripProgressStore
import com.peyya.app.data.VisitLog
import com.peyya.app.domain.AdaptEngine
import com.peyya.app.domain.DriveCue
import com.peyya.app.domain.DriveCueDetector
import com.peyya.app.domain.DriveCuePolicy
import com.peyya.app.domain.FamilyProfile
import com.peyya.app.domain.GearState
import com.peyya.app.domain.KoreanJosa
import com.peyya.app.domain.ReviewStore
import com.peyya.app.domain.Stop
import com.peyya.app.domain.TripCommand
import com.peyya.app.domain.TripPhase
import com.peyya.app.domain.TripPlan
import com.peyya.app.domain.VoiceCommand
import com.peyya.app.domain.VoiceCommandSimulator
import com.peyya.app.domain.isInProgress
import com.peyya.app.service.IgnitionGateway
import com.peyya.app.service.NaviEvent
import com.peyya.app.service.NaviGateway
import com.peyya.app.service.NaviLink
import com.peyya.app.service.SpeechGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TripViewModel(
    private val navi: NaviGateway,
    private val ignition: IgnitionGateway,
    private val speech: SpeechGuide,
    private val link: StateFlow<NaviLink>,
    private val progressStore: TripProgressStore? = null,
    reviewStore: ReviewStore = ReviewStore().also { it.loadSeed() },
    private val visitLog: VisitLog? = null,
) : ViewModel() {
    private val review = TripReviewReducer(reviewStore) { visitLog?.applyRatings(it) }
    private val cueDetector = DriveCueDetector()
    private var completedCallbackFired = false

    /** The plan as loaded, so 「일정 초기화」 restarts *this* route instead of the preset. */
    private var basePlan: TripPlan = PresetTrips.weekendMuseumTrip

    private val _uiState = MutableStateFlow(
        TripUiState(
            family = PresetTrips.defaultFamily,
            trip = PresetTrips.weekendMuseumTrip,
            mockMode = link.value == NaviLink.Mock,
            courseCandidates = review.courses(),
        ),
    )
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()

    init {
        restoreProgress()
        observeLink()
        observeVehicle()
        persistProgress()
    }

    fun updateFamilyName(name: String) {
        _uiState.update { state ->
            state.copy(family = state.family.copy(familyName = name.trim().ifEmpty { state.family.familyName }))
        }
    }

    fun syncFamily(family: FamilyProfile) {
        _uiState.update { it.copy(family = family) }
    }

    fun loadTrip(plan: TripPlan) {
        basePlan = plan
        completedCallbackFired = false
        _uiState.update { freshTrip(it, plan, "루트를 불러왔습니다. 예매·준비를 확인한 뒤 시작하세요.") }
    }

    fun toggleStop(stopId: String, selected: Boolean) {
        if (_uiState.value.phase !is TripPhase.Idle) return
        _uiState.update { it.copy(trip = it.trip.withStopSelection(stopId, selected)) }
    }

    fun togglePrepItem(item: String) {
        _uiState.update { state ->
            val next = state.prepChecked.toMutableSet()
            if (!next.add(item)) next.remove(item)
            state.copy(prepChecked = next)
        }
    }

    fun startRoute() {
        val first = _uiState.value.trip.selectedStops().firstOrNull()
        if (first == null) {
            setStatus("선택된 경유지가 없습니다.")
            return
        }
        routeTo(first)
    }

    fun markArrived() {
        val navigating = _uiState.value.phase as? TripPhase.Navigating ?: return
        findStop(navigating.stopId)?.let { visitLog?.recordArrival(it) }
        val hint = if (_uiState.value.vehicleLive) "기어를 D로 바꾸면" else "시동을 누르면"
        _uiState.update {
            it.copy(
                phase = TripPhase.Arrived(navigating.stopId),
                statusMessage = "도착했어요. 다시 출발할 때 $hint 다음 장소를 안내합니다.",
                adaptSuggestions = AdaptEngine.suggestionsOnArrive(navigating.stopId),
                adaptDismissed = false,
            )
        }
    }

    fun dismissAdapt() {
        _uiState.update { it.copy(adaptDismissed = true) }
    }

    fun acceptParkingSuggestion(suggestionId: String) {
        val suggestion = _uiState.value.adaptSuggestions.firstOrNull { it.id == suggestionId } ?: return
        val alt = suggestion.altStop ?: return
        val result = navi.requestRoute(alt)
        setStatus("[${modeLabel()}] ${alt.name} 주차 안내 · 시연용/추정")
        if (result.isSuccess) speak("${alt.name} 주차로 안내합니다.")
        _uiState.update { it.copy(adaptDismissed = true) }
    }

    fun onIgnition() {
        ignition.signalIgnition()
        when (val phase = _uiState.value.phase) {
            is TripPhase.Arrived -> proposeNextAfterArrived(phase.stopId)
            is TripPhase.AwaitingNext -> repeatProposal(phase.nextStopId)
            else -> setStatus("도착 처리 후 시동을 눌러 다음 안내를 받으세요.")
        }
    }

    fun applyVoiceChip(label: String) {
        when (VoiceCommandSimulator.parse(label)) {
            VoiceCommand.Next -> when (_uiState.value.phase) {
                is TripPhase.AwaitingNext -> confirmNext()
                is TripPhase.Arrived -> onIgnition()
                else -> setStatus("지금은 음성 시뮬로 확인할 단계가 아닙니다.")
            }
            VoiceCommand.Skip -> skipNext()
            VoiceCommand.Keep -> setStatus("현재 스톱을 유지합니다. (음성 시뮬)")
            VoiceCommand.Unknown -> setStatus("이해하지 못한 명령입니다.")
        }
    }

    fun resetTrip() {
        completedCallbackFired = false
        val plan = basePlan.copy(stops = basePlan.stops.map { it.copy(selected = true) })
        _uiState.update {
            freshTrip(it, plan, "일정을 초기화했습니다. 다시 시작할 수 있습니다.")
                .copy(courseCandidates = review.courses())
        }
    }

    fun continueReviewFromIntro() = _uiState.update(review::continueFromIntro)

    fun updateCurrentReview(stars: Int, comment: String) =
        _uiState.update { review.update(it, stars, comment) }

    fun nextReviewStop() = _uiState.update(review::next)

    fun skipReview() = _uiState.update(review::skip)

    fun applyCourse(candidateId: String) {
        val plan = review.planFor(_uiState.value, candidateId) ?: return
        loadTrip(plan)
        setStatus("추천 코스를 적용했습니다.")
    }

    fun confirmNext() {
        val awaiting = _uiState.value.phase as? TripPhase.AwaitingNext ?: return
        val stop = findStop(awaiting.nextStopId) ?: return
        routeTo(stop)
    }

    fun skipNext() {
        when (val phase = _uiState.value.phase) {
            is TripPhase.AwaitingNext -> skipFrom(phase.nextStopId)
            is TripPhase.Arrived -> {
                val next = findNextStopAfter(phase.stopId)
                if (next == null) completeTrip() else skipFrom(next.id)
            }
            else -> setStatus("건너뛸 다음 목적지가 없습니다.")
        }
    }

    override fun onCleared() {
        navi.release()
        ignition.release()
        speech.shutdown()
        super.onCleared()
    }

    // ---- vehicle / navigation signals -------------------------------------------------

    private fun observeLink() {
        viewModelScope.launch {
            var previous = link.value
            link.collect { value ->
                _uiState.update { it.copy(mockMode = value == NaviLink.Mock) }
                // Pleos bound late (after the mock fallback) while a leg is active: hand the
                // current destination to the real navigation so the car is actually guided.
                val navigating = _uiState.value.phase as? TripPhase.Navigating
                if (previous == NaviLink.Mock && value == NaviLink.Pleos && navigating != null) {
                    findStop(navigating.stopId)?.let { navi.requestRoute(it) }
                }
                previous = value
            }
        }
    }

    private fun observeVehicle() {
        navi.setEventListener { event ->
            onMain { if (event == NaviEvent.Arrived) handleCue(DriveCue.NaviArrived) }
        }
        ignition.observeGear { gear -> onMain { handleGear(gear) } }
    }

    private fun handleGear(gear: GearState) {
        _uiState.update { it.copy(gear = gear) }
        cueDetector.onGear(gear)?.let(::handleCue)
    }

    private fun handleCue(cue: DriveCue) {
        when (DriveCuePolicy.commandFor(_uiState.value.phase, cue, naviLive = link.value == NaviLink.Pleos)) {
            TripCommand.MarkArrived -> markArrived()
            TripCommand.StartNext -> departToNext()
            null -> Unit
        }
    }

    /** Gear P→D: the driver is already moving, so start guidance by voice instead of asking for a tap. */
    private fun departToNext() {
        val next = when (val phase = _uiState.value.phase) {
            is TripPhase.Arrived -> findNextStopAfter(phase.stopId) ?: return completeTrip()
            is TripPhase.AwaitingNext -> findStop(phase.nextStopId) ?: return
            else -> return
        }
        speak("다음 목적지, ${KoreanJosa.euroRo(next.name)} 안내를 시작합니다.")
        routeTo(next)
    }

    private fun onMain(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.Main.immediate) { block() }
    }

    // ---- persistence ------------------------------------------------------------------

    private fun restoreProgress() {
        val saved = progressStore?.load() ?: return
        if (!saved.phase.isInProgress) return
        basePlan = saved.plan
        _uiState.update {
            it.copy(trip = saved.plan, phase = saved.phase, statusMessage = "이전 루트를 이어서 진행합니다.")
        }
        when (val phase = saved.phase) {
            // Relaunch after a stop == the car was switched back on.
            is TripPhase.Arrived -> proposeNextAfterArrived(phase.stopId)
            is TripPhase.AwaitingNext -> repeatProposal(phase.nextStopId)
            else -> Unit
        }
    }

    private fun persistProgress() {
        val store = progressStore ?: return
        viewModelScope.launch {
            _uiState.map { it.trip to it.phase }.distinctUntilChanged().collect { (trip, phase) ->
                if (phase.isInProgress) store.save(trip, phase) else store.clear()
            }
        }
    }

    // ---- trip state machine -----------------------------------------------------------

    private fun freshTrip(state: TripUiState, plan: TripPlan, message: String): TripUiState = state.copy(
        trip = plan,
        phase = TripPhase.Idle,
        statusMessage = message,
        lastSpoken = "",
        adaptSuggestions = emptyList(),
        adaptDismissed = false,
        prepChecked = emptySet(),
        reviewPhase = ReviewUiPhase.None,
        reviewDrafts = emptyList(),
        reviewIndex = 0,
    )

    private fun proposeNextAfterArrived(currentId: String) {
        val next = findNextStopAfter(currentId)
        if (next == null) {
            completeTrip()
            return
        }
        val message = proposalFor(next)
        speak(message)
        _uiState.update {
            it.copy(phase = TripPhase.AwaitingNext(next.id), statusMessage = message, adaptSuggestions = emptyList())
        }
    }

    private fun repeatProposal(nextStopId: String) {
        val stop = findStop(nextStopId) ?: return
        val message = proposalFor(stop)
        speak(message)
        setStatus(message)
    }

    private fun proposalFor(stop: Stop) = "다음 목적지는 ${stop.name} 입니다. 안내를 시작할까요?"

    private fun routeTo(stop: Stop) {
        val result = navi.requestRoute(stop)
        if (result.isFailure) {
            setStatus("경로 요청 실패: ${result.exceptionOrNull()?.message}")
            return
        }
        _uiState.update {
            it.copy(
                phase = TripPhase.Navigating(stop.id),
                statusMessage = "[${modeLabel()}] ${stop.name} 경로 요청 완료",
                adaptSuggestions = emptyList(),
            )
        }
    }

    private fun modeLabel(): String = when (link.value) {
        NaviLink.Pleos -> "NaviHelper"
        NaviLink.Mock -> "Mock"
        NaviLink.Connecting -> "내비 연결 대기"
    }

    private fun findNextStopAfter(stopId: String): Stop? {
        val selected = _uiState.value.trip.selectedStops()
        val index = selected.indexOfFirst { it.id == stopId }
        if (index < 0 || index >= selected.lastIndex) return null
        return selected[index + 1]
    }

    private fun findStop(stopId: String): Stop? = _uiState.value.trip.stops.firstOrNull { it.id == stopId }

    private fun skipFrom(stopId: String) {
        val next = findNextStopAfter(stopId)
        if (next == null) {
            completeTrip()
            return
        }
        _uiState.update {
            it.copy(phase = TripPhase.AwaitingNext(next.id), statusMessage = "다음 ${next.name} 안내를 건너뛰고 대기합니다.")
        }
    }

    private fun completeTrip() {
        if (!completedCallbackFired) {
            completedCallbackFired = true
            _uiState.update(review::begin)
        }
        _uiState.update {
            it.copy(phase = TripPhase.Completed, statusMessage = "오늘 루트를 모두 마쳤습니다. 리뷰를 남겨 주세요.")
        }
        speak("오늘 루트를 모두 마쳤습니다.")
    }

    private fun speak(message: String) {
        speech.speak(message)
        _uiState.update { it.copy(lastSpoken = message) }
    }

    private fun setStatus(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }
}
