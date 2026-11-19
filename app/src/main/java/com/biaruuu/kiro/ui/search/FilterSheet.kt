package com.biaruuu.kiro.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biaruuu.kiro.data.SearchFilters
import com.biaruuu.kiro.ui.i18n.Text
import com.biaruuu.kiro.ui.theme.KiroPurple
import kotlinx.coroutines.launch
import java.util.Calendar

private val Dim = Color(0xFF8E8E93)

/** Genre, year, season, format and status in one place; the chosen filters combine. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(
    current: SearchFilters,
    genres: List<String>,
    onApply: (SearchFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    var f by remember { mutableStateOf(current) }
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val years = remember { (thisYear + 1 downTo 1990).toList() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { after(); onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161616),
        scrimColor = Color.Black.copy(alpha = 0.72f),
        shape = RectangleShape,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(top = 16.dp)) {
            Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    "Reset", color = if (f.count > 0) KiroPurple else Dim, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = f.count > 0) { f = SearchFilters() }.padding(6.dp),
                )
            }
            Column(
                modifier = Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            ) {
                Section("Genre")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    genres.forEach { g ->
                        Chip(g, g in f.genres) { f = f.copy(genres = if (g in f.genres) f.genres - g else f.genres + g) }
                    }
                }
                Section("Year")
                Row(modifier = Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Any", f.year == null) { f = f.copy(year = null) }
                    years.forEach { y -> Chip(y.toString(), f.year == y) { f = f.copy(year = if (f.year == y) null else y) } }
                }
                Section("Season")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Any", f.season == null) { f = f.copy(season = null) }
                    SearchFilters.SEASONS.forEach { (key, label) ->
                        Chip(label, f.season == key) { f = f.copy(season = if (f.season == key) null else key) }
                    }
                }
                Section("Format")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchFilters.FORMATS.forEach { (key, label) ->
                        Chip(label, key in f.formats) { f = f.copy(formats = if (key in f.formats) f.formats - key else f.formats + key) }
                    }
                }
                Section("Status")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Any", f.status == null) { f = f.copy(status = null) }
                    SearchFilters.STATUSES.forEach { (key, label) ->
                        Chip(label, f.status == key) { f = f.copy(status = if (f.status == key) null else key) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Box(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KiroPurple)
                    .clickable { close { onApply(f) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (f.count > 0) "Show results (${f.count} filters)" else "Show results",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) KiroPurple else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) KiroPurple else Color(0xFF3A3A3F)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))

/** The row under the genre tabs: opens the filter sheet and shows how many filters are on. */
@Composable
internal fun FilterBar(active: SearchFilters, onOpen: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (active.count > 0) KiroPurple else Color(0xFF26262A))
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (active.count > 0) "Filters (${active.count})" else "Filters",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            )
        }
        val parts = buildList {
            if (active.genres.size > 1) add(active.genres.joinToString(", "))
            active.year?.let { add(it.toString()) }
            active.season?.let { k -> add(SearchFilters.SEASONS.first { it.first == k }.second) }
            if (active.formats.isNotEmpty()) add(active.formats.joinToString("/") { k -> SearchFilters.FORMATS.firstOrNull { it.first == k }?.second ?: k })
            active.status?.let { k -> add(SearchFilters.STATUSES.first { it.first == k }.second) }
        }
        if (parts.isNotEmpty()) {
            Text(
                parts.joinToString(" · "), color = Dim, fontSize = 12.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text("Clear", color = KiroPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClear))
        }
    }
}
