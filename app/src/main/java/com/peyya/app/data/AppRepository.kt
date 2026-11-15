package com.peyya.app.data

import android.content.Context
import com.peyya.app.domain.AuthProfile
import com.peyya.app.domain.CommunityRoute
import com.peyya.app.domain.FamilyMember
import com.peyya.app.domain.FamilyProfile
import com.peyya.app.domain.PlacePins
import com.peyya.app.domain.PlanEngine
import com.peyya.app.domain.PlanPhase
import com.peyya.app.domain.PlanSession
import com.peyya.app.domain.RouteSource
import com.peyya.app.domain.RouteStatus
import com.peyya.app.domain.SavedRoute
import com.peyya.app.domain.VehicleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class AppSnapshot(
    val onboardingDone: Boolean,
    val auth: AuthProfile,
    val family: FamilyProfile,
    val vehicle: VehicleProfile,
    val places: PlacePins,
    val routes: List<SavedRoute>,
    val community: List<CommunityRoute>,
    val plan: PlanSession,
    val activeRouteId: String?,
)

class AppRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(initialSnapshot())
    val state: StateFlow<AppSnapshot> = _state.asStateFlow()

    fun completeOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
        _state.update { it.copy(onboardingDone = true) }
    }

    fun login(email: String, password: String): Boolean {
        val savedEmail = prefs.getString(KEY_EMAIL, null)
        val savedPass = prefs.getString(KEY_PASSWORD, null)
        val ok = when {
            email.equals(DEMO_EMAIL, ignoreCase = true) && password == DEMO_PASSWORD -> true
            savedEmail != null &&
                email.equals(savedEmail, ignoreCase = true) &&
                password == savedPass -> true
            else -> false
        }
        if (!ok) return false
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putBoolean(KEY_ONBOARDING, true)
            .putString(KEY_EMAIL, email.trim())
            .apply()
        _state.update {
            it.copy(
                onboardingDone = true,
                auth = AuthProfile(email = email.trim(), loggedIn = true),
            )
        }
        return true
    }

    fun signup(
        email: String,
        password: String,
        family: FamilyProfile,
        vehicle: VehicleProfile,
        places: PlacePins,
    ): Boolean {
        if (email.isBlank() || password.length < 4) return false
        prefs.edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_PASSWORD, password)
            .putBoolean(KEY_LOGGED_IN, true)
            .putBoolean(KEY_ONBOARDING, true)
            .putString(KEY_FAMILY_NAME, family.familyName)
            .putString(KEY_FAMILY_MEMBERS, encodeMembers(family.members))
            .putString(KEY_VEHICLE_NICK, vehicle.nickname)
            .putString(KEY_VEHICLE_TYPE, vehicle.typeHint)
            .putString(KEY_VEHICLE_PLATE, vehicle.plate)
            .putString(KEY_VEHICLE_MODEL, vehicle.modelName)
            .putString(KEY_HOME, places.homeAddress)
            .putString(KEY_WORK, places.workAddress)
            .apply()
        _state.update {
            it.copy(
                onboardingDone = true,
                auth = AuthProfile(email = email.trim(), loggedIn = true),
                family = family,
                vehicle = vehicle,
                places = places,
            )
        }
        return true
    }

    fun logout() {
        prefs.edit().putBoolean(KEY_LOGGED_IN, false).apply()
        _state.update {
            it.copy(
                onboardingDone = false,
                auth = it.auth.copy(loggedIn = false),
            )
        }
    }

    fun updateFamily(family: FamilyProfile) {
        prefs.edit()
            .putString(KEY_FAMILY_NAME, family.familyName)
            .putString(KEY_FAMILY_MEMBERS, encodeMembers(family.members))
            .apply()
        _state.update { it.copy(family = family) }
    }

    fun updateVehicle(vehicle: VehicleProfile) {
        prefs.edit()
            .putString(KEY_VEHICLE_NICK, vehicle.nickname)
            .putString(KEY_VEHICLE_TYPE, vehicle.typeHint)
            .putString(KEY_VEHICLE_PLATE, vehicle.plate)
            .putString(KEY_VEHICLE_MODEL, vehicle.modelName)
            .apply()
        _state.update { it.copy(vehicle = vehicle) }
    }

    fun updatePlaces(places: PlacePins) {
        prefs.edit()
            .putString(KEY_HOME, places.homeAddress)
            .putString(KEY_WORK, places.workAddress)
            .apply()
        _state.update { it.copy(places = places) }
    }

    fun resetPlanSession() {
        _state.update { it.copy(plan = PlanSession()) }
    }

    fun answerPlan(answer: String) {
        _state.update { it.copy(plan = PlanEngine.advanceSlots(it.plan, answer)) }
    }

    fun applyPlanChip(answer: String) = answerPlan(answer)

    fun quickPlan(destination: String) {
        _state.update { it.copy(plan = PlanEngine.quickFromDestination(destination)) }
    }

    fun planBack() {
        _state.update { snap ->
            val plan = when (snap.plan.phase) {
                PlanPhase.Confirm -> PlanEngine.backToCandidates(snap.plan)
                PlanPhase.Candidates -> PlanEngine.backToCollecting(snap.plan)
                PlanPhase.Collecting -> snap.plan
            }
            snap.copy(plan = plan)
        }
    }

    fun selectPlanCandidate(id: String) {
        _state.update { snap ->
            snap.copy(plan = PlanEngine.selectCandidate(snap.plan, id))
        }
    }

    fun confirmPlan(scheduledAtEpochMs: Long) {
        val session = _state.value.plan
        val candidate = session.candidates.firstOrNull { it.id == session.selectedCandidateId } ?: return
        val route = PlanEngine.toScheduledRoute(candidate, scheduledAtEpochMs)
        _state.update {
            it.copy(
                routes = listOf(route) + it.routes,
                plan = PlanSession(),
            )
        }
    }

    fun adoptCommunity(routeId: String, scheduledAtEpochMs: Long?) {
        val community = _state.value.community.firstOrNull { it.id == routeId } ?: return
        val saved = SavedRoute(
            id = "route~${community.id}~${UUID.randomUUID().toString().take(8)}",
            title = community.title,
            stops = community.stops.map { it.copy(selected = true) },
            status = if (scheduledAtEpochMs == null) RouteStatus.Draft else RouteStatus.Scheduled,
            source = RouteSource.Community,
            region = community.region,
            scheduledAtEpochMs = scheduledAtEpochMs,
            note = community.satisfactionNote,
        )
        _state.update { it.copy(routes = listOf(saved) + it.routes) }
        persistAdopted()
    }

    /**
     * 추천·테마 코스로 잡은 일정을 저장한다. 휴대폰에서 미리 잡은 계획이
     * 앱을 다시 켤 때(차에 시동을 걸 때)도 남아 있어야 하기 때문이다.
     */
    private fun persistAdopted() {
        val raw = _state.value.routes
            .filter { it.id.startsWith("route~") }
            .joinToString("\n") { "${it.id}|${it.scheduledAtEpochMs ?: ""}|${it.status.name}" }
        prefs.edit().putString(KEY_ADOPTED, raw).apply()
    }

    private fun restoreAdopted(community: List<CommunityRoute>): List<SavedRoute> {
        val raw = prefs.getString(KEY_ADOPTED, null).orEmpty()
        return raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 3) return@mapNotNull null
            val communityId = parts[0].split("~").getOrNull(1) ?: return@mapNotNull null
            val source = community.firstOrNull { it.id == communityId } ?: return@mapNotNull null
            val status = runCatching { RouteStatus.valueOf(parts[2]) }.getOrNull() ?: return@mapNotNull null
            SavedRoute(
                id = parts[0],
                title = source.title,
                stops = source.stops.map { it.copy(selected = true) },
                // 앱이 꺼졌다 켜지면 진행 중이던 루트는 TripProgressStore가 복원하므로 예정으로 되돌린다.
                status = if (status == RouteStatus.Active) RouteStatus.Scheduled else status,
                source = RouteSource.Community,
                region = source.region,
                scheduledAtEpochMs = parts[1].toLongOrNull(),
                note = source.satisfactionNote,
            )
        }
    }

    fun toggleLike(routeId: String) {
        _state.update { snap ->
            snap.copy(
                community = snap.community.map { route ->
                    if (route.id != routeId) route
                    else {
                        val liked = !route.likedByMe
                        route.copy(
                            likedByMe = liked,
                            likes = (route.likes + if (liked) 1 else -1).coerceAtLeast(0),
                        )
                    }
                },
            )
        }
    }

    fun addComment(routeId: String, body: String, imageModel: String? = null) {
        val text = body.trim()
        if (text.isEmpty()) return
        val author = _state.value.family.familyName
        _state.update { snap ->
            snap.copy(
                community = snap.community.map { route ->
                    if (route.id != routeId) route
                    else route.copy(
                        comments = route.comments + com.peyya.app.domain.CommunityComment(
                            id = "local-${UUID.randomUUID().toString().take(6)}",
                            authorLabel = author,
                            body = text,
                            createdAtEpochMs = System.currentTimeMillis(),
                            imageModel = imageModel,
                        ),
                    )
                },
            )
        }
    }

    fun startRoute(routeId: String): SavedRoute? {
        val started = _state.value.routes.firstOrNull { it.id == routeId }
            ?.copy(status = RouteStatus.Active)
            ?: return null
        _state.update { snap ->
            val routes = snap.routes.map { route ->
                when {
                    route.id == routeId -> started
                    route.status == RouteStatus.Active -> route.copy(status = RouteStatus.Scheduled)
                    else -> route
                }
            }
            snap.copy(routes = routes, activeRouteId = routeId)
        }
        persistAdopted()
        return started
    }

    /** Marks the active route done; [fallbackRouteId] covers a trip restored after a relaunch. */
    fun completeActiveRoute(fallbackRouteId: String? = null) {
        val fallback = fallbackRouteId?.takeIf { id ->
            _state.value.routes.any { it.id == id && it.status != RouteStatus.Done }
        }
        val activeId = _state.value.activeRouteId ?: fallback ?: return
        _state.update { snap ->
            snap.copy(
                activeRouteId = null,
                routes = snap.routes.map { route ->
                    if (route.id == activeId) {
                        route.copy(
                            status = RouteStatus.Done,
                            completedAtEpochMs = System.currentTimeMillis(),
                        )
                    } else route
                },
            )
        }
        persistAdopted()
    }

    private fun initialSnapshot(): AppSnapshot {
        val loggedIn = prefs.getBoolean(KEY_LOGGED_IN, false)
        val onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false) && loggedIn
        val familyName = prefs.getString(KEY_FAMILY_NAME, null) ?: PresetTrips.defaultFamily.familyName
        val members = decodeMembers(prefs.getString(KEY_FAMILY_MEMBERS, null))
            ?: PresetTrips.defaultFamily.members
        val vehicle = VehicleProfile(
            nickname = prefs.getString(KEY_VEHICLE_NICK, null) ?: SeedCatalog.vehicleDefault.nickname,
            typeHint = prefs.getString(KEY_VEHICLE_TYPE, null) ?: SeedCatalog.vehicleDefault.typeHint,
            plate = prefs.getString(KEY_VEHICLE_PLATE, null).orEmpty(),
            modelName = prefs.getString(KEY_VEHICLE_MODEL, null).orEmpty(),
        )
        val places = PlacePins(
            homeAddress = prefs.getString(KEY_HOME, null).orEmpty(),
            workAddress = prefs.getString(KEY_WORK, null).orEmpty(),
        )
        val community = SeedCatalog.communitySeed()
        return AppSnapshot(
            onboardingDone = onboardingDone,
            auth = AuthProfile(
                email = prefs.getString(KEY_EMAIL, null).orEmpty(),
                loggedIn = loggedIn,
            ),
            family = FamilyProfile(familyName, members),
            vehicle = vehicle,
            places = places,
            // Demo course so Execute shows leave/home times + photo route board.
            routes = restoreAdopted(community) + SeedCatalog.presetSaved(),
            community = community,
            plan = PlanSession(),
            activeRouteId = null,
        )
    }

    private fun encodeMembers(members: List<FamilyMember>): String =
        members.joinToString("|") { "${it.name},${it.ageBand.name}" }

    private fun decodeMembers(raw: String?): List<FamilyMember>? {
        if (raw.isNullOrBlank()) return null
        return raw.split("|").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size < 2) return@mapNotNull null
            val band = runCatching { com.peyya.app.domain.AgeBand.valueOf(parts[1]) }.getOrNull()
                ?: return@mapNotNull null
            FamilyMember(parts[0], band)
        }.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val PREFS = "peyya_product"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_FAMILY_NAME = "family_name"
        private const val KEY_FAMILY_MEMBERS = "family_members"
        private const val KEY_VEHICLE_NICK = "vehicle_nick"
        private const val KEY_VEHICLE_TYPE = "vehicle_type"
        private const val KEY_VEHICLE_PLATE = "vehicle_plate"
        private const val KEY_VEHICLE_MODEL = "vehicle_model"
        private const val KEY_ADOPTED = "adopted_routes"
        private const val KEY_HOME = "home_address"
        private const val KEY_WORK = "work_address"
        const val DEMO_EMAIL = "demo@peyya.app"
        const val DEMO_PASSWORD = "peyya"
    }
}
