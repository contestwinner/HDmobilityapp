package com.peyya.app.viewmodel

import androidx.lifecycle.ViewModel
import com.peyya.app.data.AppRepository
import com.peyya.app.data.AppSnapshot
import com.peyya.app.data.VisitLog
import com.peyya.app.domain.VisitRecord
import com.peyya.app.domain.FamilyProfile
import com.peyya.app.domain.PlacePins
import com.peyya.app.domain.VehicleProfile
import kotlinx.coroutines.flow.StateFlow

class AppViewModel(
    private val repository: AppRepository,
    visitLog: VisitLog,
) : ViewModel() {
    val snapshot: StateFlow<AppSnapshot> = repository.state

    /** Arrivals (and their ratings) for 마이's national visit map. */
    val visits: StateFlow<List<VisitRecord>> = visitLog.visits

    fun completeOnboarding() = repository.completeOnboarding()
    fun login(email: String, password: String) = repository.login(email, password)

    /** One-tap entry for judges/demo: signs in with the built-in demo family. */
    fun loginDemo() = repository.login(AppRepository.DEMO_EMAIL, AppRepository.DEMO_PASSWORD)
    fun signup(
        email: String,
        password: String,
        family: FamilyProfile,
        vehicle: VehicleProfile,
        places: PlacePins,
    ) = repository.signup(email, password, family, vehicle, places)

    fun logout() = repository.logout()
    fun updateFamily(family: FamilyProfile) = repository.updateFamily(family)
    fun updateVehicle(vehicle: VehicleProfile) = repository.updateVehicle(vehicle)
    fun updatePlaces(places: PlacePins) = repository.updatePlaces(places)
    fun resetPlan() = repository.resetPlanSession()
    fun answerPlan(text: String) = repository.answerPlan(text)
    fun quickPlan(destination: String) = repository.quickPlan(destination)
    fun planBack() = repository.planBack()
    fun selectCandidate(id: String) = repository.selectPlanCandidate(id)
    fun confirmPlan(atEpochMs: Long) = repository.confirmPlan(atEpochMs)
    fun toggleLike(id: String) = repository.toggleLike(id)
    fun addComment(id: String, body: String, imageModel: String? = null) =
        repository.addComment(id, body, imageModel)
    fun adoptCommunity(id: String, atEpochMs: Long?) = repository.adoptCommunity(id, atEpochMs)
    fun startRoute(id: String) = repository.startRoute(id)
    fun completeActiveRoute(fallbackRouteId: String? = null) = repository.completeActiveRoute(fallbackRouteId)
}
