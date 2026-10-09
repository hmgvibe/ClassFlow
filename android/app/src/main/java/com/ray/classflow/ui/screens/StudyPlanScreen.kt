package com.ray.classflow.ui.screens

import com.ray.classflow.i18n.UiText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ray.classflow.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private val weekNames
    get() = listOf(UiText.TEXT_A0B21BDF86.text(), UiText.TEXT_898FD5BE1A.text(), UiText.TEXT_538556D98F.text(), UiText.TEXT_313FBD0219.text(), UiText.TEXT_6EAAE7141B.text(), UiText.TEXT_E6839E3C6A.text(), UiText.TEXT_084801F47D.text())
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm").withLocale(UiText.displayLocale())

@Composable
fun StudyPlanScreen(
    state: ClassFlowState,
    contentPadding: PaddingValues,
    onSave: (StudyPlan) -> Unit,
    onDelete: (StudyPlan) -> Unit,
) {
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var firstDay by rememberSaveable { mutableStateOf(LocalDate.now().minusDays(2).toEpochDay()) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    val editor = state.studyPlans.find { it.id == editorId }
    var editorPeriod by remember { mutableStateOf(StudyPeriod.AFTERNOON) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(selectedDay)
    val plans = state.studyPlans.filter { it.localDate() == date }.sortedBy { it.startsAt }
    fun add(period: StudyPeriod) {
        editorId = null
        editorPeriod = period
        editing = true
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { add(StudyPeriod.AFTERNOON) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(UiText.TEXT_AEE8B65355.text()) },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { choosingDate = true }, modifier = Modifier.weight(1f)) {
                        Text(date.format(DateTimeFormatter.ofPattern(UiText.TEXT_0852D35571.text()).withLocale(UiText.displayLocale())))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    TextButton(
                        onClick = {
                            selectedDay = LocalDate.now().toEpochDay()
                            firstDay = selectedDay - 2
                        }
                    ) {
                        Text(UiText.TEXT_17E83CC25E.text())
                    }
                    IconButton(
                        onClick = {
                            firstDay -= 5
                            selectedDay -= 5
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, UiText.TEXT_A09D6C1B87.text())
                    }
                    IconButton(
                        onClick = {
                            firstDay += 5
                            selectedDay += 5
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, UiText.TEXT_D1D80C3899.text())
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) { offset ->
                        val day = LocalDate.ofEpochDay(firstDay + offset)
                        val selected = day == date
                        Column(
                            Modifier.weight(1f)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(12.dp),
                                )
                                .selectable(
                                    selected,
                                    onClick = { selectedDay = day.toEpochDay() },
                                    role = Role.Tab,
                                )
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                if (day == LocalDate.now()) UiText.TEXT_17E83CC25E.text()
                                else weekNames[day.dayOfWeek.value - 1],
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color =
                                    if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    UiText.TEXT_C345410DA4.text(date.monthValue, date.dayOfMonth, plans.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
            ) {
                StudyPeriod.entries.forEach { period ->
                    val section = plans.filter { it.period() == period }
                    item(key = "period-${period}") {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                period.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                period.range,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { add(period) }) {
                                Icon(Icons.Default.Add, UiText.TEXT_C34E11C136.text(period.label))
                            }
                        }
                    }
                    if (section.isEmpty()) {
                        item(key = "empty-${period}") {
                            Text(
                                UiText.TEXT_B60BAE39F7.text(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp),
                            )
                        }
                    }
                    items(section, key = { it.id }) { plan ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    editorId = plan.id
                                    editing = true
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Column(Modifier.width(52.dp)) {
                                Text(
                                    plan.timeLabel(plan.startsAt),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    plan.timeLabel(plan.endsAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(plan.title, style = MaterialTheme.typography.titleMedium)
                                studyLinkLabel(plan, state)?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (plan.notes.isNotBlank())
                                    Text(
                                        plan.notes,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                if (plan.syncState == SyncState.CONFLICT)
                                    Text(
                                        UiText.TEXT_233F250F01.text(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
    if (choosingDate)
        PlanDatePicker(
            date,
            {
                selectedDay = it.toEpochDay()
                firstDay = selectedDay - 2
                choosingDate = false
            },
            { choosingDate = false },
        )
    if (editing)
        StudyPlanEditor(
            editor,
            date,
            editorPeriod,
            state,
            onSave = {
                onSave(it)
                selectedDay = it.localDate().toEpochDay()
                if (selectedDay !in firstDay..firstDay + 4) firstDay = selectedDay - 2
                editing = false
            },
            onDelete = {
                onDelete(it)
                editing = false
            },
            onDismiss = { editing = false },
        )
}

private fun StudyPlan.timeLabel(timestamp: Long) =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(clockFormat)

internal fun studyLinkLabel(plan: StudyPlan, state: ClassFlowState): String? =
    when {
        plan.linkedCourseId != null ->
            state.courses.find { it.id == plan.linkedCourseId }?.let { UiText.TEXT_C6AB5020BE.text(it.name) }
                ?: UiText.TEXT_C2404CE96F.text()
        plan.linkedAgendaId != null ->
            state.agenda
                .find { it.id == plan.linkedAgendaId }
                ?.let { "${if (it.type == AgendaType.EXAM) UiText.TEXT_2AA23AAED8.text() else UiText.TEXT_631A2479D9.text()} · ${it.title}" }
                ?: UiText.TEXT_F217F20658.text()
        else -> null
    }

internal fun studyTimeHint(courseId: String?, agendaId: String?, state: ClassFlowState): String? {
    if (courseId != null) {
        if (state.courses.none { it.id == courseId }) return UiText.TEXT_C08D869E94.text()
        val slots =
            state.slots
                .filter { it.courseId == courseId }
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes }))
        return if (slots.isEmpty()) UiText.TEXT_AD6D77B5DF.text()
        else
            UiText.TEXT_638AA4573A.text() +
                slots.joinToString("\n") {
                    "${weekNames[it.dayOfWeek - 1]} ${it.startTime.format(clockFormat)}–${it.endTime.format(clockFormat)}"
                }
    }
    if (agendaId != null) {
        val item = state.agenda.find { it.id == agendaId } ?: return UiText.TEXT_BD24D751F5.text()
        val date = Instant.ofEpochMilli(item.occursAt).atZone(ZoneId.systemDefault())
        return "${if (item.type == AgendaType.EXAM) UiText.TEXT_E9AF7DC999.text() else UiText.TEXT_4B7B0D6C8E.text()}：" +
            date.format(DateTimeFormatter.ofPattern(if (item.allDay) UiText.TEXT_F2ABF68D2B.text() else "M/d HH:mm").withLocale(UiText.displayLocale()))
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyPlanEditor(
    plan: StudyPlan?,
    initialDate: LocalDate,
    period: StudyPeriod,
    state: ClassFlowState,
    onSave: (StudyPlan) -> Unit,
    onDelete: (StudyPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = plan?.let { Instant.ofEpochMilli(it.startsAt).atZone(zone) }
    var title by rememberSaveable(plan?.id) { mutableStateOf(plan?.title.orEmpty()) }
    var day by
        rememberSaveable(plan?.id) {
            mutableStateOf((initial?.toLocalDate() ?: initialDate).toEpochDay())
        }
    var start by
        rememberSaveable(plan?.id) {
            mutableStateOf(
                initial?.format(clockFormat)
                    ?: "%02d:00".format(java.util.Locale.ROOT, period.defaultHour)
            )
        }
    var end by
        rememberSaveable(plan?.id) {
            mutableStateOf(
                plan?.timeLabel(plan.endsAt)
                    ?: "%02d:00".format(java.util.Locale.ROOT, period.defaultHour + 1)
            )
        }
    var notes by rememberSaveable(plan?.id) { mutableStateOf(plan?.notes.orEmpty()) }
    var courseId by rememberSaveable(plan?.id) { mutableStateOf(plan?.linkedCourseId) }
    var agendaId by rememberSaveable(plan?.id) { mutableStateOf(plan?.linkedAgendaId) }
    var linkKind by
        rememberSaveable(plan?.id) {
            mutableStateOf(
                when {
                    courseId != null -> "course"
                    agendaId != null &&
                        state.agenda.find { it.id == agendaId }?.type == AgendaType.EXAM -> "exam"
                    agendaId != null -> "homework"
                    else -> "none"
                }
            )
        }
    var datePicker by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(day)
    fun timestamp(value: String) = runCatching {
        date.atTime(LocalTime.parse(value)).atZone(zone).toInstant().toEpochMilli()
    }
        .getOrNull()
    val startsAt = timestamp(start)
    val endsAt = timestamp(end)
    val validTimes = startsAt != null && endsAt != null && endsAt > startsAt
    val options =
        when (linkKind) {
            "course" -> state.courses.map { it.id to it.name }
            "homework",
            "exam" ->
                state.agenda
                    .filter {
                        it.type == if (linkKind == "exam") AgendaType.EXAM else AgendaType.HOMEWORK
                    }
                    .sortedBy { it.occursAt }
                    .map { it.id to it.title }
            else -> emptyList()
        }
    val selectedId = courseId ?: agendaId
    val selectedLabel =
        options.find { it.first == selectedId }?.second
            ?: if (selectedId != null) UiText.TEXT_E7FFF94FA4.text() else UiText.TEXT_A4C0BB8817.text()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) UiText.TEXT_AEE8B65355.text() else UiText.TEXT_03592148F7.text()) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(UiText.TEXT_6AF10BF3C9.text()) },
                    singleLine = true,
                    isError = title.length > 255,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { datePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(date.format(DateTimeFormatter.ofPattern("yyyy/M/d · EEEE").withLocale(UiText.displayLocale())))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        start,
                        { start = it },
                        label = { Text(UiText.TEXT_D89B413B59.text()) },
                        placeholder = { Text("09:00") },
                        singleLine = true,
                        isError = startsAt == null,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        end,
                        { end = it },
                        label = { Text(UiText.TEXT_4F44D6A6A3.text()) },
                        placeholder = { Text("10:00") },
                        singleLine = true,
                        isError = !validTimes,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!validTimes)
                    Text(
                        UiText.TEXT_50F0893ED9.text(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                Text(UiText.TEXT_02AC4612F8.text(), style = MaterialTheme.typography.titleSmall)
                PlanMenu(
                    when (linkKind) {
                        "course" -> UiText.TEXT_ECA9598BA5.text()
                        "exam" -> UiText.TEXT_2AA23AAED8.text()
                        "homework" -> UiText.TEXT_631A2479D9.text()
                        else -> UiText.TEXT_6958A7063E.text()
                    },
                    listOf("none" to UiText.TEXT_6958A7063E.text(), "course" to UiText.TEXT_ECA9598BA5.text(), "homework" to UiText.TEXT_631A2479D9.text(), "exam" to UiText.TEXT_2AA23AAED8.text()),
                ) {
                    linkKind = it
                    courseId = null
                    agendaId = null
                }
                if (linkKind != "none") {
                    PlanMenu(selectedLabel, listOf("" to UiText.TEXT_0246045609.text()) + options) { id ->
                        courseId = if (linkKind == "course") id.ifBlank { null } else null
                        agendaId = if (linkKind != "course") id.ifBlank { null } else null
                    }
                    if (options.isEmpty())
                        Text(
                            UiText.TEXT_E2CC02F8B1.text(when(linkKind) { "course" -> UiText.TEXT_ECA9598BA5.text()
 "exam" -> UiText.TEXT_2AA23AAED8.text()
 else -> UiText.TEXT_631A2479D9.text() }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    studyTimeHint(courseId, agendaId, state)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        UiText.TEXT_BD559F688E.text(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text(UiText.TEXT_45DC68DB57.text()) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (plan != null)
                    TextButton(onClick = { deleting = true }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text(UiText.TEXT_25B0160426.text(), color = MaterialTheme.colorScheme.error)
                    }
            }
        },
        confirmButton = {
            Button(
                enabled =
                    title.isNotBlank() &&
                        title.length <= 255 &&
                        notes.length <= 10000 &&
                        validTimes,
                onClick = {
                    onSave(
                        StudyPlan(
                            plan?.id ?: UUID.randomUUID().toString(),
                            title.trim(),
                            requireNotNull(startsAt),
                            requireNotNull(endsAt),
                            courseId,
                            agendaId,
                            notes.trim(),
                            plan?.version ?: 0,
                        )
                    )
                },
            ) {
                Text(UiText.TEXT_C7B0321049.text())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.TEXT_4D0B4688C7.text()) } },
    )
    if (datePicker)
        PlanDatePicker(
            date,
            {
                day = it.toEpochDay()
                datePicker = false
            },
            { datePicker = false },
        )
    if (deleting && plan != null)
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(UiText.TEXT_81ED8FE11D.text()) },
            text = { Text(UiText.TEXT_9DBCA18FF2.text(plan.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(plan) }) {
                    Text(UiText.TEXT_A48F5D05A6.text(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(UiText.TEXT_4D0B4688C7.text()) } },
        )
}

@Composable
private fun PlanMenu(
    label: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanDatePicker(date: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val picker =
        rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = picker.selectedDateMillis != null,
                onClick = {
                    picker.selectedDateMillis?.let {
                        onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            ) {
                Text(UiText.TEXT_C02292DD59.text())
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(UiText.TEXT_4D0B4688C7.text()) } },
    ) {
        DatePicker(picker)
    }
}
