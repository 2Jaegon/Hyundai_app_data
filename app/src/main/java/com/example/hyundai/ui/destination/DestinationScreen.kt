package com.example.hyundai.ui.destination

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hyundai.nav.NavPlace
import com.example.hyundai.nav.PlaceKind
import com.example.hyundai.nav.PlaceListMode
import com.example.hyundai.ui.common.ScreenFrame
import com.example.hyundai.ui.drive.formatDistance
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState

/** 목적지 고르기. 정차 중에만 열린다. 기본 내비의 검색·바로가기와 같은 일을 온길 안에서 한다. */
@Composable
fun DestinationScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onShowList: (PlaceListMode) -> Unit,
    onGoSaved: (PlaceKind) -> Unit,
    onGuide: (NavPlace) -> Unit,
    onHandOff: (NavPlace) -> Unit,
    onSave: (NavPlace, PlaceKind) -> Unit,
    onOpenNaviSearch: (String) -> Unit,
    onOpenNaviChargers: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ui = state.placeSearch
    val naviReady = state.sdkStatus.naviConnected
    var query by rememberSaveable { mutableStateOf(ui.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = {
        keyboard?.hide()
        onSearch(query)
    }

    ScreenFrame(title = "목적지", onBack = onBack, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(TodakColors.PanelDeep)
                        .border(1.dp, TodakColors.EdgeStrong, RoundedCornerShape(18.dp))
                        .padding(horizontal = 22.dp, vertical = 16.dp)
                ) {
                    if (query.isEmpty()) {
                        Text("어디로 갈까요?", color = TodakColors.TextDim, fontSize = 22.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
                        cursorBrush = SolidColor(TodakColors.Route),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ActionButton("검색", primary = true, onClick = submit)
                if (naviReady) ActionButton("기본 내비에서 검색", onClick = { onOpenNaviSearch(query) })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Shortcut("집", selected = false) { onGoSaved(PlaceKind.HOME) }
                Shortcut("회사", selected = false) { onGoSaved(PlaceKind.WORK) }
                Shortcut("즐겨찾기", selected = ui.mode == PlaceListMode.FAVORITES) { onShowList(PlaceListMode.FAVORITES) }
                Shortcut("충전소", selected = ui.mode == PlaceListMode.CHARGERS) { onShowList(PlaceListMode.CHARGERS) }
                Shortcut("최근", selected = ui.mode == PlaceListMode.RECENT) { onShowList(PlaceListMode.RECENT) }
                if (naviReady && ui.mode == PlaceListMode.CHARGERS) {
                    ActionButton("기본 내비 충전소", onClick = onOpenNaviChargers)
                }
            }

            val header = when {
                ui.loading -> "찾는 중"
                ui.message != null -> ui.message
                ui.offline -> "인터넷이 안 되어 기기에 있는 장소에서 찾았어요"
                else -> null
            }
            if (header != null) {
                Text(header, color = TodakColors.Sand, fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(ui.results, key = { "${it.kind}-${it.point.lat}-${it.point.lng}-${it.name}" }) { place ->
                    PlaceRow(
                        place = place,
                        naviReady = naviReady,
                        onGuide = { onGuide(place) },
                        onHandOff = { onHandOff(place) },
                        onSave = { kind -> onSave(place, kind) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: NavPlace,
    naviReady: Boolean,
    onGuide: () -> Unit,
    onHandOff: () -> Unit,
    onSave: (PlaceKind) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TodakColors.Panel)
            .border(1.dp, TodakColors.Edge, RoundedCornerShape(18.dp))
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                kindLabel(place.kind)?.let {
                    Text(it, color = TodakColors.Route, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    place.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            val sub = listOfNotNull(place.distanceM?.let { formatDistance(it) }, place.address.ifBlank { null })
                .joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, color = TodakColors.TextDim, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SmallButton("집으로") { onSave(PlaceKind.HOME) }
        SmallButton("회사로") { onSave(PlaceKind.WORK) }
        SmallButton("저장") { onSave(PlaceKind.FAVORITE) }
        if (naviReady) ActionButton("기본 내비", onClick = onHandOff)
        ActionButton("안내", primary = true, onClick = onGuide)
    }
}

private fun kindLabel(kind: PlaceKind): String? = when (kind) {
    PlaceKind.HOME -> "집"
    PlaceKind.WORK -> "회사"
    PlaceKind.CHARGER -> "충전"
    else -> null
}

@Composable
private fun Shortcut(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) TodakColors.RouteDeep else TodakColors.Panel)
            .border(1.dp, if (selected) TodakColors.Route else TodakColors.Edge, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ActionButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) TodakColors.RouteDeep else TodakColors.Neutral)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, TodakColors.EdgeStrong, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(text, color = TodakColors.Sand, fontSize = 16.sp)
    }
}
