package com.ray.classflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ray.classflow.model.ClassFlowState
import com.ray.classflow.model.Course
import com.ray.classflow.model.TimetableSlot
import com.ray.classflow.ui.theme.courseColor
import com.ray.classflow.ui.theme.randomUnusedCourseColorKey
import com.ray.classflow.ui.theme.resolveDistinctCourseColors
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    state: ClassFlowState,
    contentPadding: PaddingValues,
    onSaveCourse: (Course) -> Unit,
    onDeleteCourse: (Course) -> Unit,
    onSaveSlot: (TimetableSlot) -> Unit,
    onDeleteSlot: (TimetableSlot) -> Unit,
) {
    var selectedDay by remember { mutableIntStateOf(LocalDate.now().dayOfWeek.value) }
    var editingSlot by remember { mutableStateOf<TimetableSlot?>(null) }
    var showSlotEditor by remember { mutableStateOf(false) }
    var showCourses by remember { mutableStateOf(false) }
    val displayCourses = remember(state.courses) { resolveDistinctCourseColors(state.courses) }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (displayCourses.isEmpty()) showCourses = true else {
                        editingSlot = null
                        showSlotEditor = true
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(if (displayCourses.isEmpty()) "建立課程" else "新增課堂") },
            )
        },
    ) { inner ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp),
        ) {
            val isWide = this.maxWidth >= 700.dp
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("每週課表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${displayCourses.size} 門課程 · ${state.slots.size} 個時段",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showCourses = true }) { Text("管理課程") }
                }

                if (isWide) {
                    WeeklyGrid(
                        state = state.copy(courses = displayCourses),
                        onEdit = { editingSlot = it; showSlotEditor = true },
                    )
                } else {
                    DaySelector(selectedDay = selectedDay, onSelected = { selectedDay = it })
                    Spacer(Modifier.height(16.dp))
                    DaySchedule(
                        slots = state.slots.filter { it.dayOfWeek == selectedDay },
                        courses = displayCourses,
                        onEdit = { editingSlot = it; showSlotEditor = true },
                    )
                }
            }
        }
    }

    if (showCourses) {
        CourseManagerDialog(
            courses = displayCourses,
            onSave = onSaveCourse,
            onDelete = onDeleteCourse,
            onDismiss = { showCourses = false },
        )
    }
    if (showSlotEditor) {
        SlotEditorDialog(
            slot = editingSlot,
            courses = displayCourses,
            defaultDay = selectedDay,
            onSave = { onSaveSlot(it); showSlotEditor = false },
            onDelete = editingSlot?.let { slot -> { onDeleteSlot(slot); showSlotEditor = false } },
            onDismiss = { showSlotEditor = false },
        )
    }
}

@Composable
private fun DaySelector(selectedDay: Int, onSelected: (Int) -> Unit) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedDay - 2).coerceAtLeast(0),
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(dayLabels) { index, label ->
            FilterChip(
                selected = selectedDay == index + 1,
                onClick = { onSelected(index + 1) },
                label = { Text("週$label") },
            )
        }
    }
}

@Composable
private fun DaySchedule(
    slots: List<TimetableSlot>,
    courses: List<Course>,
    onEdit: (TimetableSlot) -> Unit,
) {
    val courseMap = courses.associateBy { it.id }
    if (slots.isEmpty()) {
        EmptySchedule()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(slots.sortedBy { it.startMinutes }, key = { it.id }) { slot ->
            val course = courseMap[slot.courseId] ?: return@items
            ScheduleRow(slot, course, onEdit)
        }
    }
}

@Composable
private fun ScheduleRow(slot: TimetableSlot, course: Course, onEdit: (TimetableSlot) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onEdit(slot) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(66.dp)) {
            Text(formatTime(slot.startMinutes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(formatTime(slot.endMinutes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier.padding(top = 3.dp).width(4.dp).height(54.dp)
                .clip(CircleShape).background(courseColor(course.colorKey)),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            val detail = listOf(slot.roomOverride.ifBlank { course.room }, course.teacher).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.Edit, contentDescription = "編輯", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@Composable
private fun EmptySchedule() {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("這天還沒有課", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Text("使用右下角按鈕加入第一堂課", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeeklyGrid(state: ClassFlowState, onEdit: (TimetableSlot) -> Unit) {
    val courseMap = state.courses.associateBy { it.id }
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { day ->
            Column(modifier = Modifier.weight(1f)) {
                Text("週${dayLabels[day - 1]}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(8.dp))
                state.slots.filter { it.dayOfWeek == day }.sortedBy { it.startMinutes }.forEach { slot ->
                    courseMap[slot.courseId]?.let { course ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onEdit(slot) },
                            color = courseColor(course.colorKey).copy(alpha = 0.12f),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(course.name, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatTime(slot.startMinutes)}–${formatTime(slot.endMinutes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseManagerDialog(
    courses: List<Course>,
    onSave: (Course) -> Unit,
    onDelete: (Course) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf<Course?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理課程") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (courses.isEmpty()) {
                    Text("先建立一門課程，再把它安排到每週課表。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    courses.forEach { course ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { editing = course; showEditor = true }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(courseColor(course.colorKey)))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(course.name, fontWeight = FontWeight.Medium)
                                val secondary = listOf(course.teacher, course.room).filter { it.isNotBlank() }.joinToString(" · ")
                                if (secondary.isNotEmpty()) Text(secondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.Edit, contentDescription = "編輯")
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { editing = null; showEditor = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("新增課程")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
    if (showEditor) {
        CourseEditorDialog(
            course = editing,
            usedColorKeys = courses.filter { it.id != editing?.id }.mapTo(mutableSetOf(), Course::colorKey),
            onSave = { onSave(it); showEditor = false },
            onDelete = editing?.let { course -> { onDelete(course); showEditor = false } },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun CourseEditorDialog(
    course: Course?,
    usedColorKeys: Set<Int>,
    onSave: (Course) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember(course) { mutableStateOf(course?.name.orEmpty()) }
    var teacher by remember(course) { mutableStateOf(course?.teacher.orEmpty()) }
    var room by remember(course) { mutableStateOf(course?.room.orEmpty()) }
    var notes by remember(course) { mutableStateOf(course?.notes.orEmpty()) }
    val colorKey = remember(course, usedColorKeys) {
        course?.colorKey ?: randomUnusedCourseColorKey(usedColorKeys)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (course == null) "新增課程" else "編輯課程") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("課程名稱") }, singleLine = true)
                OutlinedTextField(teacher, { teacher = it }, label = { Text("教師（選填）") }, singleLine = true)
                OutlinedTextField(room, { room = it }, label = { Text("教室（選填）") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("備註（選填）") }, minLines = 2)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(courseColor(colorKey)))
                    Column {
                        Text("課程色彩", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "新增時會自動分配未使用的顏色",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("刪除課程", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(Course(
                        id = course?.id ?: UUID.randomUUID().toString(),
                        name = name.trim(),
                        teacher = teacher.trim(),
                        room = room.trim(),
                        notes = notes.trim(),
                        colorKey = colorKey,
                        version = course?.version ?: 0,
                    ))
                },
                enabled = name.isNotBlank(),
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SlotEditorDialog(
    slot: TimetableSlot?,
    courses: List<Course>,
    defaultDay: Int,
    onSave: (TimetableSlot) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var courseId by remember(slot) { mutableStateOf(slot?.courseId ?: courses.firstOrNull()?.id.orEmpty()) }
    var day by remember(slot) { mutableIntStateOf(slot?.dayOfWeek ?: defaultDay) }
    var start by remember(slot) { mutableStateOf(formatTime(slot?.startMinutes ?: 8 * 60)) }
    var end by remember(slot) { mutableStateOf(formatTime(slot?.endMinutes ?: 9 * 60)) }
    var room by remember(slot) { mutableStateOf(slot?.roomOverride.orEmpty()) }
    var menuOpen by remember { mutableStateOf(false) }
    val startMinutes = parseTime(start)
    val endMinutes = parseTime(end)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (slot == null) "新增課堂" else "編輯課堂") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(min = 300.dp)
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(courses.firstOrNull { it.id == courseId }?.name ?: "選擇課程")
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        courses.forEach { course ->
                            DropdownMenuItem(
                                text = { Text(course.name) },
                                onClick = { courseId = course.id; menuOpen = false },
                            )
                        }
                    }
                }
                DaySelector(selectedDay = day, onSelected = { day = it })

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("開始") }, placeholder = { Text("08:00") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("結束") }, placeholder = { Text("09:00") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(room, { room = it }, label = { Text("教室覆寫（選填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("刪除此課堂", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(TimetableSlot(
                        id = slot?.id ?: UUID.randomUUID().toString(),
                        courseId = courseId,
                        dayOfWeek = day,
                        startMinutes = startMinutes ?: 0,
                        endMinutes = endMinutes ?: 0,
                        roomOverride = room.trim(),
                        version = slot?.version ?: 0,
                    ))
                },
                enabled = courseId.isNotBlank() && startMinutes != null && endMinutes != null && startMinutes < endMinutes,
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

private fun parseTime(value: String): Int? = runCatching {
    val time = LocalTime.parse(value.trim())
    time.hour * 60 + time.minute
}.getOrNull()
