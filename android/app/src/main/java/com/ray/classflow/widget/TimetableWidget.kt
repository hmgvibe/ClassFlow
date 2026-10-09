package com.ray.classflow.widget

import com.ray.classflow.i18n.UiText

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ray.classflow.ClassFlowApplication
import com.ray.classflow.MainActivity
import com.ray.classflow.R
import com.ray.classflow.data.db.CourseEntity
import com.ray.classflow.data.db.TimetableSlotEntity
import com.ray.classflow.ui.theme.courseColor
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private data class TimetableWidgetData(
    val courses: List<CourseEntity>,
    val slots: List<TimetableSlotEntity>,
)

internal data class UpcomingClass(
    val slot: TimetableSlotEntity,
    val date: LocalDate,
    val startsAt: ZonedDateTime,
    val endsAt: ZonedDateTime,
)

class TimetableWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp),
            DpSize(320.dp, 180.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ClassFlowApplication
        val data = TimetableWidgetData(
            courses = app.database.dao().allCourses(),
            slots = app.database.dao().allSlots(),
        )
        provideContent { TimetableWidgetContent(context, data) }
    }
}

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}

@Composable
private fun TimetableWidgetContent(context: Context, data: TimetableWidgetData) {
    val now = ZonedDateTime.now()
    val courseMap = data.courses.associateBy { it.id }
    val upcoming = nextTimetableClasses(data.slots, now)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(3.dp),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = UiText.TEXT_80573F129F.text(),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = WidgetText, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    text = now.format(DateTimeFormatter.ofPattern("M/d EEEE").withLocale(UiText.displayLocale())),
                    style = TextStyle(color = WidgetMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (upcoming.isEmpty()) {
                Text(
                    text = UiText.TEXT_CEC0BD4932.text(),
                    style = TextStyle(color = WidgetText, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = UiText.TEXT_1BF0752269.text(),
                    style = TextStyle(color = WidgetMuted, fontSize = 12.sp),
                )
            } else {
                upcoming.forEachIndexed { index, item ->
                    val course = courseMap[item.slot.courseId]
                    TimetableWidgetRow(
                        item = item,
                        course = course,
                        now = now,
                        label = when {
                            index == 0 && !now.isBefore(item.startsAt) -> UiText.TEXT_A3EAF71626.text()
                            index == 0 -> UiText.TEXT_8C73D90ECA.text()
                            else -> UiText.TEXT_CF5BBD9B33.text()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimetableWidgetRow(
    item: UpcomingClass,
    course: CourseEntity?,
    now: ZonedDateTime,
    label: String,
) {
    val room = item.slot.roomOverride.ifBlank { course?.room.orEmpty() }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val accent = course?.let { courseColor(it.colorKey) } ?: Color(0xFF006B63)
        Box(
            modifier = GlanceModifier
                .width(4.dp)
                .height(52.dp)
                .background(ColorProvider(day = accent, night = accent)),
        ) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = "${label} · ${classTimeLabel(item, now)}",
                style = TextStyle(color = WidgetPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = course?.name ?: UiText.TEXT_80573F129F.text(),
                style = TextStyle(color = WidgetText, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        if (room.isNotBlank()) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = room,
                style = TextStyle(color = WidgetMuted, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

internal fun nextTimetableClasses(
    slots: List<TimetableSlotEntity>,
    now: ZonedDateTime,
    limit: Int = 2,
): List<UpcomingClass> {
    if (limit <= 0 || slots.isEmpty()) return emptyList()
    val zone = now.zone
    return (0..14).flatMap { offset ->
        val date = now.toLocalDate().plusDays(offset.toLong())
        slots.asSequence()
            .filter { it.dayOfWeek == date.dayOfWeek.value }
            .map { slot ->
                val startsAt = date.atTime(slot.startMinutes / 60, slot.startMinutes % 60).atZone(zone)
                val endsAt = date.atTime(slot.endMinutes / 60, slot.endMinutes % 60).atZone(zone)
                UpcomingClass(slot, date, startsAt, endsAt)
            }
            .filter { it.endsAt.isAfter(now) }
            .toList()
    }.sortedBy { it.startsAt }.take(limit)
}

private fun classTimeLabel(item: UpcomingClass, now: ZonedDateTime): String {
    val daysAway = ChronoUnit.DAYS.between(now.toLocalDate(), item.date)
    val dayPrefix = when (daysAway) {
        0L -> ""
        in 1L..6L -> UiText.TEXT_014FD5DFEA.text(listOf(UiText.TEXT_D274EEE8A1.text(), UiText.TEXT_1D5639F716.text(), UiText.TEXT_49DDB069D5.text(), UiText.TEXT_4F88740B34.text(), UiText.TEXT_8F07F53D63.text(), UiText.TEXT_3D72C724E0.text(), UiText.TEXT_15917F3B32.text())[item.date.dayOfWeek.value - 1])
        else -> "${item.date.format(DateTimeFormatter.ofPattern("M/d").withLocale(UiText.displayLocale()))} "
    }
    return "${dayPrefix}${formatMinutes(item.slot.startMinutes)}–${formatMinutes(item.slot.endMinutes)}"
}

private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
