package com.ray.classflow.ui.screens

import com.ray.classflow.i18n.UiText

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

private val typeLabels
    get() = mapOf(
    AgendaType.HOMEWORK to UiText.TEXT_631A2479D9.text(),
    AgendaType.EXAM to UiText.TEXT_2AA23AAED8.text(),
    AgendaType.ACTIVITY to UiText.TEXT_47E6C61BB4.text(),
    AgendaType.OTHER to UiText.TEXT_1A26EDF94A.text(),
)

private val agendaDayLabels
    get() = listOf(UiText.TEXT_D274EEE8A1.text(), UiText.TEXT_1D5639F716.text(), UiText.TEXT_49DDB069D5.text(), UiText.TEXT_4F88740B34.text(), UiText.TEXT_8F07F53D63.text(), UiText.TEXT_3D72C724E0.text(), UiText.TEXT_15917F3B32.text())

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
                text = { Text(UiText.TEXT_1DA7A50ADC.text()) },
            )
        },
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(UiText.TEXT_C073BBD4DC.text()) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text(UiText.TEXT_778FC8F994.text()) })
                    AgendaType.entries.forEach { type ->
                        FilterChip(selected = selectedType == type, onClick = { selectedType = type }, label = { Text(typeLabels.getValue(type)) })
                    }
                    FilterChip(selected = showCompleted, onClick = { showCompleted = !showCompleted }, label = { Text(UiText.TEXT_A9B8D1F7FD.text()) })
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
                        item(key = "header-${date}") {
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
                        " · ${Instant.ofEpochMilli(item.occursAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm").withLocale(UiText.displayLocale()))}",
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
        Text(if (hasFilters) UiText.TEXT_86BF71ABED.text() else UiText.TEXT_10DF5CCE16.text(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text(if (hasFilters) UiText.TEXT_1434342812.text() else UiText.TEXT_7F7366D312.text(), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var time by remember(item) { mutableStateOf(initialDateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm").withLocale(UiText.displayLocale()))) }
    var allDay by remember(item) { mutableStateOf(item?.allDay ?: false) }
    var notes by remember(item) { mutableStateOf(item?.notes.orEmpty()) }
    val initialLinkedSlot = remember(item, slots) {
        slots.sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes }))
            .firstOrNull { it.id in item?.linkedSlotIds.orEmpty() }
    }
    var linkedDay by remember(item, slots) {
        mutableIntStateOf(initialLinkedSlot?.dayOfWeek ?: initialDateTime.dayOfWeek.value)
    }
    var linkedSlotId by remember(item, slots) { mutableStateOf(initialLinkedSlot?.id) }
    var dayMenuOpen by remember { mutableStateOf(false) }
    var slotMenuOpen by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var reminderMinutes by remember(item) {
        val diff = item?.reminderAt?.let { (item.occursAt - it) / 60_000 }
        mutableIntStateOf(diff?.toInt() ?: 0)
    }
    val parsedTime = if (allDay) LocalTime.of(9, 0) else runCatching { LocalTime.parse(time) }.getOrNull()
    val occursAt = parsedTime?.let { selectedDate.atTime(it).atZone(zone).toInstant().toEpochMilli() }
    val courseMap = courses.associateBy { it.id }
    val linkedDaySlots = slots.filter { it.dayOfWeek == linkedDay }.sortedBy { it.startMinutes }
    val linkedSlot = slots.firstOrNull { it.id == linkedSlotId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item == null) UiText.TEXT_1DA7A50ADC.text() else UiText.TEXT_A0E9E32E0B.text()) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(title, { title = it }, label = { Text(UiText.TEXT_BEC815141B.text()) }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                            Text(selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(UiText.displayLocale())))
                        }
                        if (!allDay) {
                            OutlinedTextField(time, { time = it }, label = { Text(UiText.TEXT_09558B2CC6.text()) }, placeholder = { Text("16:00") }, singleLine = true, modifier = Modifier.width(104.dp))
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(UiText.TEXT_79B62069F5.text(), style = MaterialTheme.typography.bodyLarge)
                            Text(UiText.TEXT_237C71FDDE.text(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = allDay, onCheckedChange = { allDay = it })
                    }
                }
                item {
                    Text(UiText.TEXT_81944E48A3.text(), style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to UiText.TEXT_0D85DE063E.text(), 60 to UiText.TEXT_15B2B923CE.text(), 1440 to UiText.TEXT_FBA0FE4862.text()).forEach { (minutes, label) ->
                            FilterChip(selected = reminderMinutes == minutes, onClick = { reminderMinutes = minutes }, label = { Text(label) })
                        }
                    }
                }
                item {
                    Text(UiText.TEXT_1E4D4234B2.text(), style = MaterialTheme.typography.labelLarge)
                    Text(UiText.TEXT_66D4A36DA4.text(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { dayMenuOpen = true },
                                enabled = slots.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(UiText.TEXT_21ED347F10.text(agendaDayLabels[linkedDay - 1]))
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = dayMenuOpen, onDismissRequest = { dayMenuOpen = false }) {
                                agendaDayLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(
                                        text = { Text(UiText.TEXT_21ED347F10.text(label)) },
                                        onClick = {
                                            linkedDay = index + 1
                                            if (linkedSlotId !in slots.filter { it.dayOfWeek == linkedDay }.map { it.id }) {
                                                linkedSlotId = null
                                            }
                                            dayMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        Box(Modifier.weight(1.4f)) {
                            OutlinedButton(
                                onClick = { slotMenuOpen = true },
                                enabled = linkedDaySlots.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    linkedSlot?.let { courseMap[it.courseId]?.name ?: UiText.TEXT_ECA9598BA5.text() }
                                        ?: if (linkedDaySlots.isEmpty()) UiText.TEXT_08E84CA954.text() else UiText.TEXT_7F59354F24.text(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = slotMenuOpen, onDismissRequest = { slotMenuOpen = false }) {
                                linkedDaySlots.forEach { slot ->
                                    DropdownMenuItem(
                                        text = { Text("${courseMap[slot.courseId]?.name ?: UiText.TEXT_ECA9598BA5.text()} · ${String.format("%02d:%02d", slot.startMinutes / 60, slot.startMinutes % 60)}") },
                                        onClick = {
                                            linkedSlotId = slot.id
                                            time = String.format("%02d:%02d", slot.startMinutes / 60, slot.startMinutes % 60)
                                            allDay = false
                                            slotMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(notes, { notes = it }, label = { Text(UiText.TEXT_45DC68DB57.text()) }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
                if (onDelete != null) {
                    item {
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(UiText.TEXT_F922787907.text(), color = MaterialTheme.colorScheme.error)
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
                        linkedSlotIds = listOfNotNull(linkedSlotId),
                        version = item?.version ?: 0,
                    ))
                },
            ) { Text(UiText.TEXT_C7B0321049.text()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.TEXT_4D0B4688C7.text()) } },
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
                }) { Text(UiText.TEXT_C02292DD59.text()) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(UiText.TEXT_4D0B4688C7.text()) } },
        ) { DatePicker(state = pickerState) }
    }
}

private fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> UiText.TEXT_83D8B625E8.text(date.monthValue, date.dayOfMonth)
        today.plusDays(1) -> UiText.TEXT_BF2F4467C3.text(date.monthValue, date.dayOfMonth)
        else -> date.format(DateTimeFormatter.ofPattern(UiText.TEXT_D07B357C8C.text()).withLocale(UiText.displayLocale()))
    }
}
