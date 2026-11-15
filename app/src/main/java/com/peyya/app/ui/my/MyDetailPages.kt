package com.peyya.app.ui.my

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peyya.app.domain.AgeBand
import com.peyya.app.domain.FamilyMember
import com.peyya.app.domain.FamilyProfile
import com.peyya.app.domain.MockVehicleLookup
import com.peyya.app.domain.PlacePins
import com.peyya.app.domain.VehicleProfile
import com.peyya.app.ui.components.FamilyAvatars
import com.peyya.app.ui.components.PlaceSearchField
import com.peyya.app.ui.components.PrimaryCta
import com.peyya.app.ui.components.SoftCard
import com.peyya.app.ui.theme.PeyyaBrand

/** One tappable row on the 마이 hub that opens a detail page. */
@Composable
fun MyMenuRow(title: String, summary: String, onClick: () -> Unit) {
    SoftCard(Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = PeyyaBrand)
        }
    }
}

@Composable
fun FamilyDetail(family: FamilyProfile, onSave: (FamilyProfile) -> Unit) {
    var familyName by remember(family.familyName) { mutableStateOf(family.familyName) }
    var members by remember(family.members) { mutableStateOf(family.members) }
    var newMember by remember { mutableStateOf("") }
    SoftCard {
        FamilyAvatars(members)
        Spacer(Modifier.height(10.dp))
        Field("가족 이름", familyName) { familyName = it }
        Spacer(Modifier.height(8.dp))
        members.forEach { member ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${member.name} · ${member.ageBand.labelKo}")
                TextButton(onClick = { if (members.size > 1) members = members.filterNot { it == member } }) { Text("삭제") }
            }
        }
        Field("멤버 이름 추가", newMember) { newMember = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgeBand.entries.forEach { band ->
                AssistChip(
                    onClick = {
                        val name = newMember.trim()
                        if (name.isNotEmpty()) {
                            members = members + FamilyMember(name, band)
                            newMember = ""
                        }
                    },
                    label = { Text(band.labelKo) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryCta("가족 저장", true) {
            onSave(FamilyProfile(familyName = familyName.trim().ifEmpty { family.familyName }, members = members))
        }
    }
}

@Composable
fun VehicleDetail(vehicle: VehicleProfile, onSave: (VehicleProfile) -> Unit) {
    var nick by remember(vehicle.nickname) { mutableStateOf(vehicle.nickname) }
    var typeHint by remember(vehicle.typeHint) { mutableStateOf(vehicle.typeHint) }
    var plate by remember(vehicle.plate) { mutableStateOf(vehicle.plate) }
    var modelName by remember(vehicle.modelName) { mutableStateOf(vehicle.modelName) }
    var lookupNote by remember { mutableStateOf<String?>(null) }
    SoftCard {
        OutlinedTextField(
            value = plate,
            onValueChange = { plate = it; lookupNote = null },
            label = { Text("차량 번호") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                TextButton(onClick = {
                    val hit = MockVehicleLookup.lookup(plate)
                    lookupNote = hit?.let { "${it.modelName} (Mock)" } ?: "형식을 확인해 주세요"
                    if (hit != null) {
                        modelName = hit.modelName
                        typeHint = hit.typeHint
                        if (nick.isBlank()) nick = hit.modelName
                    }
                }) { Text("조회") }
            },
        )
        lookupNote?.let { Text(it, color = PeyyaBrand, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(8.dp))
        Field("별명", nick) { nick = it }
        Spacer(Modifier.height(8.dp))
        Field("차종 힌트", typeHint) { typeHint = it }
        Spacer(Modifier.height(10.dp))
        PrimaryCta("차량 저장", true) {
            onSave(
                vehicle.copy(
                    nickname = nick.trim().ifEmpty { vehicle.nickname },
                    typeHint = typeHint.trim().ifEmpty { vehicle.typeHint },
                    plate = plate.replace(" ", ""),
                    modelName = modelName,
                ),
            )
        }
    }
}

@Composable
fun PlacesDetail(places: PlacePins, onSave: (PlacePins) -> Unit) {
    var home by remember(places.homeAddress) { mutableStateOf(places.homeAddress) }
    var work by remember(places.workAddress) { mutableStateOf(places.workAddress) }
    SoftCard {
        PlaceSearchField(label = "집 주소 검색", value = home, onValueChange = { home = it })
        Spacer(Modifier.height(12.dp))
        PlaceSearchField(label = "회사 주소 검색", value = work, onValueChange = { work = it })
        Spacer(Modifier.height(10.dp))
        PrimaryCta("장소 저장", true) { onSave(PlacePins(homeAddress = home.trim(), workAddress = work.trim())) }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        singleLine = true,
    )
}
