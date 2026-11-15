package com.peyya.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.peyya.app.domain.RouteStatus
import com.peyya.app.domain.SavedRoute
import com.peyya.app.domain.TripPhase
import com.peyya.app.domain.isInProgress
import com.peyya.app.ui.auth.AuthScreen
import com.peyya.app.ui.components.DrivingLockBanner
import com.peyya.app.ui.components.PeyyaTopBar
import com.peyya.app.ui.execute.ExecuteScreen
import com.peyya.app.ui.my.MyActions
import com.peyya.app.ui.my.MyProfile
import com.peyya.app.ui.my.MyScreen
import com.peyya.app.ui.navigation.PeyyaTab
import com.peyya.app.ui.plan.PlanScreen
import com.peyya.app.ui.recommend.RecommendScreen
import com.peyya.app.ui.theme.PeyyaTheme
import com.peyya.app.viewmodel.AppViewModel
import com.peyya.app.viewmodel.AppViewModelFactory
import com.peyya.app.viewmodel.ReviewUiPhase
import com.peyya.app.viewmodel.TripViewModel
import com.peyya.app.viewmodel.TripViewModelFactory

@Composable
fun PeyyaApp(
    forceMock: Boolean = false,
) {
    val context = LocalContext.current
    val appVm: AppViewModel = viewModel(factory = AppViewModelFactory(context.applicationContext))
    val tripVm: TripViewModel = viewModel(
        factory = TripViewModelFactory(
            appContext = context.applicationContext,
            forceMock = forceMock,
        ),
    )
    val snapshot by appVm.snapshot.collectAsStateWithLifecycle()
    val tripState by tripVm.uiState.collectAsStateWithLifecycle()
    val visits by appVm.visits.collectAsStateWithLifecycle()
    val carMode = isCarLayout()
    var tab by rememberSaveable { mutableStateOf(PeyyaTab.Execute) }
    // A restored in-progress route (app relaunched after a stop) reopens straight into driving view.
    var driving by rememberSaveable {
        val restored = tripVm.uiState.value
        // Also open when a restore finished the last stop, so the review and completion still happen.
        mutableStateOf(restored.phase.isInProgress || restored.reviewPhase != ReviewUiPhase.None)
    }
    var showSettings by remember { mutableStateOf(false) }
    var notifyMsg by remember { mutableStateOf<String?>(null) }
    // 계획을 확정하거나 코스를 가져오면 바로 드라이브를 시작할지 묻는다.
    var drivePrompt by remember { mutableStateOf<SavedRoute?>(null) }
    // 차에서 앱이 켜질 때(시동 ON) 예정된 코스가 있으면 묻는 팝업인지 여부.
    var ignitionPrompt by remember { mutableStateOf(false) }
    var ignitionChecked by rememberSaveable { mutableStateOf(false) }
    // 휴대폰에서 계획을 확정하면 "차에 시동을 걸면 안내" 알림만 보여 준다.
    var savedNotice by remember { mutableStateOf<SavedRoute?>(null) }
    val afterPlan: () -> Unit = {
        val route = appVm.snapshot.value.routes.firstOrNull()
        if (carMode) {
            ignitionPrompt = false
            drivePrompt = route
        } else {
            savedNotice = route
        }
    }
    val startDrive: (SavedRoute) -> Unit = { route ->
        val started = appVm.startRoute(route.id) ?: route
        tripVm.loadTrip(started.toTripPlan())
        tab = PeyyaTab.Execute
        driving = true
    }

    LaunchedEffect(snapshot.auth.loggedIn, snapshot.onboardingDone) {
        if (ignitionChecked || !carMode || driving) return@LaunchedEffect
        if (!snapshot.auth.loggedIn || !snapshot.onboardingDone) return@LaunchedEffect
        ignitionChecked = true
        snapshot.routes
            .firstOrNull { it.status == RouteStatus.Scheduled && it.scheduledAtEpochMs != null }
            ?.let {
                ignitionPrompt = true
                drivePrompt = it
            }
    }
    LaunchedEffect(snapshot.family) {
        tripVm.syncFamily(snapshot.family)
    }
    LaunchedEffect(tripState.vehicleMoving) {
        if (tripState.vehicleMoving) tab = PeyyaTab.Execute
    }
    LaunchedEffect(tripState.phase, driving) {
        if (driving && tripState.phase is TripPhase.Completed) {
            appVm.completeActiveRoute(tripState.trip.id)
        }
    }

    PeyyaTheme(carMode = carMode) {
        if (!snapshot.onboardingDone || !snapshot.auth.loggedIn) {
            AuthScreen(
                onLogin = { email, pass -> appVm.login(email, pass) },
                onSignup = { email, pass, family, vehicle, places ->
                    appVm.signup(email, pass, family, vehicle, places)
                },
                onDemo = { appVm.loginDemo() },
            )
            return@PeyyaTheme
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!driving) {
                    Column {
                        PeyyaTopBar(
                            onNotifications = {
                                notifyMsg = "새 알림이 없습니다. 예정 루트가 생기면 알려 드릴게요."
                            },
                            onSettings = { showSettings = true },
                        )
                        if (tripState.vehicleMoving) DrivingLockBanner()
                    }
                }
            },
            bottomBar = {
                if (!driving) {
                    NavigationBar {
                        PeyyaTab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                // Driver-distraction lock: only the execute tab while in D/R.
                                enabled = !tripState.vehicleMoving || item == PeyyaTab.Execute,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize()) {
                when (tab) {
                    PeyyaTab.Plan -> PlanScreen(
                        contentPadding = padding,
                        session = snapshot.plan,
                        onChip = appVm::answerPlan,
                        onAnswer = appVm::answerPlan,
                        onSelectCandidate = appVm::selectCandidate,
                        onConfirm = {
                            appVm.confirmPlan(it)
                            tab = PeyyaTab.Execute
                            afterPlan()
                        },
                        onReset = appVm::resetPlan,
                        onBack = appVm::planBack,
                        themeCourses = snapshot.community,
                        onAdoptCourse = { id ->
                            // 휴대폰에서 고른 주말 코스는 이번 주 토요일 오전으로, 차에서는 오늘로 잡는다.
                            appVm.adoptCommunity(id, if (carMode) laterToday() else nextSaturdayMorning())
                            tab = PeyyaTab.Execute
                            afterPlan()
                        },
                    )
                    PeyyaTab.Execute -> ExecuteScreen(
                        contentPadding = padding,
                        routes = snapshot.routes,
                        vehicleNickname = snapshot.vehicle.nickname,
                        tripState = tripState,
                        driving = driving,
                        onPlanCta = { tab = PeyyaTab.Plan },
                        onStartRoute = startDrive,
                        onExitDriving = {
                            driving = false
                            if (tripState.phase is TripPhase.Completed) {
                                appVm.completeActiveRoute(tripState.trip.id)
                            }
                        },
                        onToggleStop = tripVm::toggleStop,
                        onStartDriving = tripVm::startRoute,
                        onMarkArrived = tripVm::markArrived,
                        onIgnition = tripVm::onIgnition,
                        onConfirmNext = tripVm::confirmNext,
                        onSkipNext = tripVm::skipNext,
                        onResetTrip = tripVm::resetTrip,
                        onFamilyNameChange = tripVm::updateFamilyName,
                        onTogglePrep = tripVm::togglePrepItem,
                        onDismissAdapt = tripVm::dismissAdapt,
                        onAcceptParking = tripVm::acceptParkingSuggestion,
                        onVoiceChip = tripVm::applyVoiceChip,
                        onContinueReviewIntro = tripVm::continueReviewFromIntro,
                        onUpdateReview = tripVm::updateCurrentReview,
                        onNextReview = tripVm::nextReviewStop,
                        onSkipReview = tripVm::skipReview,
                        onApplyCourse = tripVm::applyCourse,
                    )
                    PeyyaTab.My -> MyScreen(
                        contentPadding = padding,
                        profile = MyProfile(snapshot.family, snapshot.vehicle, snapshot.places),
                        visits = visits,
                        actions = MyActions(
                            onSaveFamily = appVm::updateFamily,
                            onSaveVehicle = appVm::updateVehicle,
                            onSavePlaces = appVm::updatePlaces,
                            onLogout = {
                                appVm.logout()
                                tab = PeyyaTab.Execute
                            },
                        ),
                    )
                    PeyyaTab.Recommend -> RecommendScreen(
                        contentPadding = padding,
                        routes = snapshot.community,
                        onLike = appVm::toggleLike,
                        onComment = appVm::addComment,
                        onAdopt = { id, at ->
                            appVm.adoptCommunity(id, at)
                            tab = PeyyaTab.Execute
                            afterPlan()
                        },
                    )
                }
            }
        }

        drivePrompt?.let { route ->
            val first = route.stops.firstOrNull()?.name
            AlertDialog(
                onDismissRequest = { drivePrompt = null },
                confirmButton = {
                    TextButton(onClick = {
                        drivePrompt = null
                        startDrive(route)
                    }) { Text("출발하기") }
                },
                dismissButton = {
                    TextButton(onClick = { drivePrompt = null }) { Text("나중에") }
                },
                title = {
                    Text(if (ignitionPrompt) "계획한 드라이브가 있어요" else "드라이브를 시작할까요?")
                },
                text = {
                    Text(
                        "「${route.title}」 코스를 계획한 순서대로 안내해 드릴게요." +
                            (first?.let { "\n첫 목적지: $it" } ?: "") +
                            if (ignitionPrompt) "\n지금 드라이브를 시작할까요?" else "",
                    )
                },
            )
        }

        savedNotice?.let { route ->
            AlertDialog(
                onDismissRequest = { savedNotice = null },
                confirmButton = {
                    TextButton(onClick = { savedNotice = null }) { Text("확인") }
                },
                title = { Text("계획을 저장했어요") },
                text = {
                    Text(
                        (route.scheduledAtEpochMs?.let { scheduleLabel(it) + " · " } ?: "") +
                            "「${route.title}」\n그날 차에 시동을 걸면 차비스가 첫 목적지부터 바로 안내해 드려요.",
                    )
                },
            )
        }

        notifyMsg?.let { msg ->
            AlertDialog(
                onDismissRequest = { notifyMsg = null },
                confirmButton = {
                    TextButton(onClick = { notifyMsg = null }) { Text("확인") }
                },
                title = { Text("알림") },
                text = { Text(msg) },
            )
        }
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                confirmButton = {
                    TextButton(onClick = {
                        showSettings = false
                        tab = PeyyaTab.My
                    }) { Text("마이로 이동") }
                },
                dismissButton = {
                    TextButton(onClick = { showSettings = false }) { Text("닫기") }
                },
                title = { Text("설정") },
                text = {
                    Text(
                        "${snapshot.vehicle.nickname} · ${snapshot.auth.email.ifBlank { "계정" }}\n" +
                            "집: ${snapshot.places.homeAddress.ifBlank { "미등록" }}\n" +
                            "회사: ${snapshot.places.workAddress.ifBlank { "미등록" }}",
                    )
                },
            )
        }
    }
}

private fun scheduleLabel(epochMs: Long): String =
    java.text.SimpleDateFormat("M월 d일(E) a h시", java.util.Locale.KOREAN).format(java.util.Date(epochMs))

/** A course picked from a recommendation is scheduled a few hours from now (same as 추천 → 담기). */
private fun laterToday(): Long = System.currentTimeMillis() + ADOPT_LEAD_MS

private fun nextSaturdayMorning(): Long = java.util.Calendar.getInstance().apply {
    val days = (java.util.Calendar.SATURDAY - get(java.util.Calendar.DAY_OF_WEEK) + 7) % 7
    add(java.util.Calendar.DAY_OF_YEAR, if (days == 0) 7 else days)
    set(java.util.Calendar.HOUR_OF_DAY, 10)
    set(java.util.Calendar.MINUTE, 0)
    set(java.util.Calendar.SECOND, 0)
}.timeInMillis

private const val ADOPT_LEAD_MS = 4 * 60 * 60 * 1000L
