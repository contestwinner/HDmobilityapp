package com.peyya.app.ui.my

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peyya.app.domain.FamilyProfile
import com.peyya.app.domain.PlacePins
import com.peyya.app.domain.VehicleProfile
import com.peyya.app.domain.VisitRecord
import com.peyya.app.ui.components.BackHeader
import com.peyya.app.ui.components.ScreenScaffold
import com.peyya.app.ui.components.SectionTitle
import com.peyya.app.ui.theme.PeyyaBrand

private enum class MyPage(val title: String) { Hub("마이"), Family("가족 정보"), Vehicle("차량 정보"), Places("집 · 회사") }

/** Everything the 마이 tab edits, bundled to keep the screen signature small. */
data class MyProfile(
    val family: FamilyProfile,
    val vehicle: VehicleProfile,
    val places: PlacePins,
)

data class MyActions(
    val onSaveFamily: (FamilyProfile) -> Unit,
    val onSaveVehicle: (VehicleProfile) -> Unit,
    val onSavePlaces: (PlacePins) -> Unit,
    val onLogout: () -> Unit,
)

@Composable
fun MyScreen(
    contentPadding: PaddingValues,
    profile: MyProfile,
    visits: List<VisitRecord>,
    actions: MyActions,
) {
    var page by rememberSaveable { mutableStateOf(MyPage.Hub) }
    val back = { page = MyPage.Hub }
    ScreenScaffold(contentPadding) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (page != MyPage.Hub) BackHeader(title = page.title, onBack = back, backLabel = "마이")
            when (page) {
                MyPage.Hub -> MyHub(profile, visits, actions.onLogout) { page = it }
                MyPage.Family -> FamilyDetail(profile.family) { actions.onSaveFamily(it); back() }
                MyPage.Vehicle -> VehicleDetail(profile.vehicle) { actions.onSaveVehicle(it); back() }
                MyPage.Places -> PlacesDetail(profile.places) { actions.onSavePlaces(it); back() }
            }
        }
    }
}

@Composable
private fun MyHub(profile: MyProfile, visits: List<VisitRecord>, onLogout: () -> Unit, open: (MyPage) -> Unit) {
    Text("다녀온 곳", style = MaterialTheme.typography.titleLarge, color = PeyyaBrand)
    Text(
        "도착한 곳이 지도에 쌓여요. 핀을 누르면 언제 갔고 어땠는지 보여 줍니다. · ${visits.size}곳 · ${visits.map { it.region }.distinct().size}개 지역",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    VisitedKoreaMap(visits)
    Spacer(Modifier.height(20.dp))
    SectionTitle("내 정보")
    Spacer(Modifier.height(8.dp))
    MyMenuRow("가족 정보", "${profile.family.familyName} · ${profile.family.members.size}명") { open(MyPage.Family) }
    Spacer(Modifier.height(8.dp))
    MyMenuRow("차량 정보", "${profile.vehicle.nickname} · ${profile.vehicle.typeHint}") { open(MyPage.Vehicle) }
    Spacer(Modifier.height(8.dp))
    MyMenuRow("집 · 회사", profile.places.homeAddress.ifBlank { "미등록" }) { open(MyPage.Places) }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onLogout) { Text("로그아웃", color = MaterialTheme.colorScheme.error) }
}
