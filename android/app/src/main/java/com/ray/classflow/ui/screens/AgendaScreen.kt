package com.ray.classflow.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ray.classflow.model.AgendaItem
import com.ray.classflow.model.AgendaStatus
import com.ray.classflow.model.AgendaType
import com.ray.classflow.model.ClassFlowState
import com.ray.classflow.model.Course
import com.ray.classflow.model.TimetableSlot
import com.ray.classflow.ui.theme.CoursePalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

private val typeLabels = mapOf(
    AgendaType.HOMEWORK to "作業",
    AgendaType.EXAM to "考試",
    AgendaType.ACTIVITY to "活動",
    AgendaType.OTHER to "其他",
)

private val agendaDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

private val typeColors = mapOf(
    AgendaType.HOMEWORK to Color(0xFF3B6EA8),
    AgendaType.EXAM to Color(0xFFC15C48),
    AgendaType.ACTIVITY to Color(0xFF0C7C73),
    AgendaType.OTHER to Color(0xFF776B82),
)

@Composable
fun AgendaScreen(
    state: ClassFlowState,
    contentPadding: PaddingValues,
    onSave: (AgendaItem) -> Unit,
    onDelete: (AgendaItem) -> Unit,
    onSetCompleted: (AgendaItem, Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<AgendaType?>(null) }
    var showCompleted by remember { mutableStateOf(false) }
    var editorItem by remember { mutableStateOf<AgendaItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val filtered = state.agenda.filter { item ->
        (showCompleted || item.status == AgendaStatus.PENDING) &&
            (selectedType == null || item.type == selectedType) &&
            (query.isBlank() || item.title.contains(query, ignoreCase = true) || item.notes.contains(query, ignoreCase = true))
    }
    val grouped = filtered.groupBy { it.localDate() }.toSortedMap()

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editorItem = null; showEditor = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新增日程") },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜尋標題或備註") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("全部") })
                    AgendaType.entries.forEach { type ->
                        FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(typeLabels.getValue(type)) })
                    }
                    FilterChip(selected = showCompleted, onClick = { showCompleted = !showCompleted }, label = { Text("含已完成") })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (grouped.isEmpty()) {
                EmptyAgenda(hasFilters = query.isNotBlank() || selectedType != null || showCompleted)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                ) {
                    grouped.forEach { (date, itemsForDate) ->
                        item(key = "header-$date") {
                            Text(
                                dateLabel(date),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                            )
                        }
                        items(itemsForDate.sortedBy { it.occursAt }, key = { it.id }) { item ->
                            AgendaRow(
                                item = item,
                                state = state,
                                onCompleted = { onSetCompleted(item, it) },
                                onClick = { editorItem = item; showEditor = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        AgendaEditorDialog(
            item = editorItem,
            courses = state.courses,
            slots = state.slots,
            onSave = { onSave(it); showEditor = false },
            onDelete = editorItem?.let { item -> { onDelete(item); showEditor = false } },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun AgendaRow(
    item: AgendaItem,
    state: ClassFlowState,
    onCompleted: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val courseMap = state.courses.associateBy { it.id }
    val slotMap = state.slots.associateBy { it.id }
    val linkedNames = item.linkedSlotIds.mapNotNull { slotMap[it]?.courseId?.let(courseMap::get)?.name }.distinct()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = item.status == AgendaStatus.COMPLETED, onCheckedChange = onCompleted)
        Column(modifier = Modifier.weight(1f).padding(top = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(9.dp).clip(CircleShape).background(typeColors.getValue(item.type)),
                )
                Spacer(Modifier.width(8.dp))
                Text(typeLabels.getValue(item.type), style = MaterialTheme.typography.labelMedium, color = typeColors.getValue(item.type))
                if (!item.allDay) {
                    Text(
                        " · ${Instant.ofEpochMilli(item.occursAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    textDecoration = if (item.status == AgendaStatus.COMPLETED) TextDecoration.LineThrough else null,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (linkedNames.isNotEmpty()) {
                Text(linkedNames.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun EmptyAgenda(hasFilters: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(if (hasFilters) "沒有符合條件的日程" else "目前沒有待辦日程", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(if (hasFilters) "調整搜尋或篩選條件" else "加入作業、考試或活動", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaEditorDialog(
    item: AgendaItem?,
    courses: List<Course>,
    slots: List<TimetableSlot>,
    onSave: (AgendaItem) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initialDateTime = item?.let { Instant.ofEpochMilli(it.occursAt).atZone(zone).toLocalDateTime() }
        ?: LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(16, 0))
    var title by remember(item) { mutableStateOf(item?.title.orEmpty()) }
    var type by remember(item) { mutableStateOf(item?.type ?: AgendaType.HOMEWORK) }
    var selectedDate by remember(item) { mutableStateOf(initialDateTime.toLocalDate()) }
    var time by remember(item) { mutableStateOf(initialDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var allDay by remember(item) { mutableStateOf(item?.allDay ?: false) }
    var notes by remember(item) { mutableStateOf(item?.notes.orEmpty()) }
    var linkedSlots by remember(item) { mutableStateOf(item?.linkedSlotIds?.toSet() ?: emptySet()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var reminderMinutes by remember(item) {
        val diff = item?.reminderAt?.let { (item.occursAt - it) / 60_000 }
        mutableIntStateOf(diff?.toInt() ?: 0)
    }
    val parsedTime = if (allDay) LocalTime.of(9, 0) else runCatching { LocalTime.parse(time) }.getOrNull()
    val occursAt = parsedTime?.let { selectedDate.atTime(it).atZone(zone).toInstant().toEpochMilli() }
    val courseMap = courses.associateBy { it.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) "新增日程" else "編輯日程") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(title, { title = it }, label = { Text("標題") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                item {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AgendaType.entries.forEach { option ->
                            FilterChip(selected = type == option, onClick = { type = option }, label = { Text(typeLabels.getValue(option)) })
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
                        }
                        if (!allDay) {
                            OutlinedTextField(time, { time = it }, label = { Text("時間") }, placeholder = { Text("16:00") }, singleLine = true, modifier = Modifier.width(104.dp))
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("全天", style = MaterialTheme.typography.bodyLarge)
                            Text("不顯示指定時間", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = allDay, onCheckedChange = { allDay = it })
                    }
                }
                item {
                    Text("提醒", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "不提醒", 60 to "1 小時前", 1440 to "1 天前").forEach { (minutes, label) ->
                            FilterChip(selected = reminderMinutes == minutes, onClick = { reminderMinutes = minutes }, label = { Text(label) })
                        }
                    }
                }
                if (slots.isNotEmpty()) {
                    item {
                        Text("連結課堂", style = MaterialTheme.typography.labelLarge)
                        Text("可複選", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            slots.sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes })).forEach { slot ->
                                val course = courseMap[slot.courseId]
                                FilterChip(
                                    selected = slot.id in linkedSlots,
                                    onClick = {
                                        linkedSlots = if (slot.id in linkedSlots) linkedSlots - slot.id else linkedSlots + slot.id
                                    },
                                    label = { Text("${course?.name ?: "課程"} · 週${agendaDayLabels[slot.dayOfWeek - 1]}") },
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(notes, { notes = it }, label = { Text("備註（選填）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
                if (onDelete != null) {
                    item {
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text("刪除此日程", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && occursAt != null,
                onClick = {
                    val timestamp = requireNotNull(occursAt)
                    onSave(AgendaItem(
                        id = item?.id ?: UUID.randomUUID().toString(),
                        type = type,
                        title = title.trim(),
                        occursAt = timestamp,
                        allDay = allDay,
                        status = item?.status ?: AgendaStatus.PENDING,
                        notes = notes.trim(),
                        reminderAt = if (reminderMinutes == 0) null else timestamp - reminderMinutes * 60_000L,
                        linkedSlotIds = linkedSlots.toList(),
                        version = item?.version ?: 0,
                    ))
                },
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        selectedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) { DatePicker(state = pickerState) }
    }
}

private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "今天 · ${date.monthValue}/${date.dayOfMonth}"
        today.plusDays(1) -> "明天 · ${date.monthValue}/${date.dayOfMonth}"
        else -> date.format(DateTimeFormatter.ofPattern("M 月 d 日 · EEEE"))
    }
}
