package com.ray.classflow.ui.screens

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

private val weekNames = listOf("週一", "週二", "週三", "週四", "週五", "週六", "週日")
private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

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
                text = { Text("新增計劃") },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { choosingDate = true }, modifier = Modifier.weight(1f)) {
                        Text(date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月")))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    TextButton(
                        onClick = {
                            selectedDay = LocalDate.now().toEpochDay()
                            firstDay = selectedDay - 2
                        }
                    ) {
                        Text("今天")
                    }
                    IconButton(
                        onClick = {
                            firstDay -= 5
                            selectedDay -= 5
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "前五天")
                    }
                    IconButton(
                        onClick = {
                            firstDay += 5
                            selectedDay += 5
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "後五天")
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
                                if (day == LocalDate.now()) "今天"
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
                    "${date.monthValue}/${date.dayOfMonth} · ${plans.size} 個計劃",
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
                    item(key = "period-$period") {
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
                                Icon(Icons.Default.Add, "新增${period.label}計劃")
                            }
                        }
                    }
                    if (section.isEmpty()) {
                        item(key = "empty-$period") {
                            Text(
                                "尚未安排",
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
                                        "同步衝突 · 請到設定處理",
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
            state.courses.find { it.id == plan.linkedCourseId }?.let { "課程 · ${it.name}" }
                ?: "原連結課程已刪除"
        plan.linkedAgendaId != null ->
            state.agenda
                .find { it.id == plan.linkedAgendaId }
                ?.let { "${if (it.type == AgendaType.EXAM) "考試" else "作業"} · ${it.title}" }
                ?: "原連結日程已刪除"
        else -> null
    }

internal fun studyTimeHint(courseId: String?, agendaId: String?, state: ClassFlowState): String? {
    if (courseId != null) {
        if (state.courses.none { it.id == courseId }) return "原課程已刪除，可重新選擇或取消連結。"
        val slots =
            state.slots
                .filter { it.courseId == courseId }
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startMinutes }))
        return if (slots.isEmpty()) "這門課程尚未安排課堂。"
        else
            "每週課堂時間\n" +
                slots.joinToString("\n") {
                    "${weekNames[it.dayOfWeek - 1]} ${it.startTime.format(clockFormat)}–${it.endTime.format(clockFormat)}"
                }
    }
    if (agendaId != null) {
        val item = state.agenda.find { it.id == agendaId } ?: return "原日程已刪除，可重新選擇或取消連結。"
        val date = Instant.ofEpochMilli(item.occursAt).atZone(ZoneId.systemDefault())
        return "${if (item.type == AgendaType.EXAM) "考試時間" else "作業日程時間"}：" +
            date.format(DateTimeFormatter.ofPattern(if (item.allDay) "M/d（全天）" else "M/d HH:mm"))
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
            ?: if (selectedId != null) "原連結已刪除" else "選擇連結對象"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (plan == null) "新增計劃" else "編輯計劃") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("計劃標題") },
                    singleLine = true,
                    isError = title.length > 255,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { datePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(date.format(DateTimeFormatter.ofPattern("yyyy/M/d · EEEE")))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        start,
                        { start = it },
                        label = { Text("開始時間") },
                        placeholder = { Text("09:00") },
                        singleLine = true,
                        isError = startsAt == null,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        end,
                        { end = it },
                        label = { Text("結束時間") },
                        placeholder = { Text("10:00") },
                        singleLine = true,
                        isError = !validTimes,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!validTimes)
                    Text(
                        "請填 HH:mm；結束時間需晚於開始時間。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                Text("連結對象（選填）", style = MaterialTheme.typography.titleSmall)
                PlanMenu(
                    when (linkKind) {
                        "course" -> "課程"
                        "exam" -> "考試"
                        "homework" -> "作業"
                        else -> "不連結"
                    },
                    listOf("none" to "不連結", "course" to "課程", "homework" to "作業", "exam" to "考試"),
                ) {
                    linkKind = it
                    courseId = null
                    agendaId = null
                }
                if (linkKind != "none") {
                    PlanMenu(selectedLabel, listOf("" to "取消連結") + options) { id ->
                        courseId = if (linkKind == "course") id.ifBlank { null } else null
                        agendaId = if (linkKind != "course") id.ifBlank { null } else null
                    }
                    if (options.isEmpty())
                        Text(
                            "目前沒有可連結的${when(linkKind) { "course" -> "課程"
 "exam" -> "考試"
 else -> "作業" }}",
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
                        "以上時間僅供參考，不會自動更改計劃時間。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    notes,
                    { notes = it },
                    label = { Text("備註（選填）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (plan != null)
                    TextButton(onClick = { deleting = true }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text("刪除此計劃", color = MaterialTheme.colorScheme.error)
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
                Text("儲存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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
            title = { Text("刪除計劃？") },
            text = { Text("只會刪除「${plan.title}」，不會刪除連結的課程或日程。") },
            confirmButton = {
                TextButton(onClick = { onDelete(plan) }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
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
                Text("確定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(picker)
    }
}
