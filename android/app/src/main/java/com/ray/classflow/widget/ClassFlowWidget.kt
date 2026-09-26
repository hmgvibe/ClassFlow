package com.ray.classflow.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.appwidget.action.actionStartActivity
import com.ray.classflow.ClassFlowApplication
import com.ray.classflow.MainActivity
import com.ray.classflow.data.db.AgendaWithLinks
import com.ray.classflow.data.db.CourseEntity
import com.ray.classflow.data.db.TimetableSlotEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class WidgetData(
    val courses: List<CourseEntity>,
    val slots: List<TimetableSlotEntity>,
    val agenda: List<AgendaWithLinks>,
)

class ClassFlowWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ClassFlowApplication
        val data = WidgetData(
            courses = app.database.dao().allCourses(),
            slots = app.database.dao().allSlots(),
            agenda = app.database.dao().allAgenda(),
        )
        provideContent { WidgetContent(context, data) }
    }

    companion object {
        suspend fun updateAll(context: Context) = ClassFlowWidget().updateAll(context)
    }
}

class ClassFlowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClassFlowWidget()
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData) {
    val today = LocalDate.now()
    val courseMap = data.courses.associateBy { it.id }
    val todaySlots = data.slots.filter { it.dayOfWeek == today.dayOfWeek.value }.take(3)
    val upcoming = data.agenda.filter { it.agenda.status == "PENDING" && it.agenda.occursAt >= System.currentTimeMillis() }
        .sortedBy { it.agenda.occursAt }
        .take(2)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(day = Color(0xFFF7FAF9), night = Color(0xFF17201F)))
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
            .padding(16.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "今天 · ${today.monthValue}/${today.dayOfMonth}",
                style = TextStyle(
                    color = ColorProvider(day = Color(0xFF123F3B), night = Color(0xFFE2F3F0)),
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(GlanceModifier.height(10.dp))
        if (todaySlots.isEmpty() && upcoming.isEmpty()) {
            Text(
                "今天沒有課程或待辦",
                style = TextStyle(color = ColorProvider(day = Color(0xFF60706E), night = Color(0xFFAAB9B7))),
            )
        } else {
            todaySlots.forEach { slot ->
                val course = courseMap[slot.courseId]
                Text(
                    text = "%02d:%02d  %s".format(slot.startMinutes / 60, slot.startMinutes % 60, course?.name ?: "課程"),
                    style = TextStyle(color = ColorProvider(day = Color(0xFF1C2E2C), night = Color(0xFFF0F5F4))),
                )
                Spacer(GlanceModifier.height(4.dp))
            }
            upcoming.forEach { row ->
                val date = Instant.ofEpochMilli(row.agenda.occursAt).atZone(ZoneId.systemDefault()).toLocalDate()
                Text(
                    text = "${date.format(DateTimeFormatter.ofPattern("M/d"))}  ${row.agenda.title}",
                    style = TextStyle(color = ColorProvider(day = Color(0xFF52625F), night = Color(0xFFB5C5C2))),
                )
                Spacer(GlanceModifier.height(4.dp))
            }
        }
    }
}

