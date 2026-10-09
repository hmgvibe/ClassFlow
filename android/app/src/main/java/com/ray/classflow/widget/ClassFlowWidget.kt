package com.ray.classflow.widget

import com.ray.classflow.i18n.UiText

import android.appwidget.AppWidgetManager
import android.content.ComponentName
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
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.color.ColorProvider
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
import androidx.glance.appwidget.action.actionStartActivity
import com.ray.classflow.ClassFlowApplication
import com.ray.classflow.MainActivity
import com.ray.classflow.R
import com.ray.classflow.data.db.AgendaWithLinks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val WidgetPrimary = ColorProvider(day = Color(0xFF006B63), night = Color(0xFF81D5CC))
internal val WidgetText = ColorProvider(day = Color(0xFF191C1B), night = Color(0xFFE1E3E1))
internal val WidgetMuted = ColorProvider(day = Color(0xFF52615E), night = Color(0xFFB7C7C3))
private val AgendaAccent = Color(0xFFC15C48)

private data class WidgetData(
    val agenda: List<AgendaWithLinks>,
)

private data class WidgetEntry(
    val leading: String,
    val title: String,
    val accent: Color,
)

class ClassFlowWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
            DpSize(320.dp, 250.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as ClassFlowApplication
        val data = WidgetData(
            agenda = app.database.dao().allAgenda(),
        )
        provideContent { WidgetContent(context, data) }
    }

    companion object {
        suspend fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val glanceManager = GlanceAppWidgetManager(context)
            // Use Android's receiver ownership, including after an upgrade from
            // a release where Glance cached both widget types under one class.
            val receivers = listOf(ClassFlowWidgetReceiver(), TimetableWidgetReceiver())
            for (receiver in receivers) {
                val component = ComponentName(context, receiver.javaClass)
                for (appWidgetId in appWidgetManager.getAppWidgetIds(component)) {
                    receiver.glanceAppWidget.update(context, glanceManager.getGlanceIdBy(appWidgetId))
                }
            }
        }
    }
}

class ClassFlowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClassFlowWidget()
}

@Composable
private fun WidgetContent(context: Context, data: WidgetData) {
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    val widgetHeight = LocalSize.current.height
    val maxRows = when {
        widgetHeight < 145.dp -> 2
        widgetHeight < 220.dp -> 5
        else -> 6
    }
    val upcoming = data.agenda
        .filter { it.agenda.status == "PENDING" && it.agenda.occursAt >= now }
        .sortedBy { it.agenda.occursAt }
    val entries = widgetEntries(today, upcoming, maxRows)
    val summary = if (upcoming.isEmpty()) UiText.TEXT_7523E10472.text() else UiText.TEXT_4DAA9CCDA4.text(upcoming.size)

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
                    text = UiText.TEXT_B4E2598569.text(),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = WidgetText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = summary,
                    style = TextStyle(
                        color = WidgetMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            if (entries.isEmpty()) {
                Text(
                    text = UiText.TEXT_DA21202001.text(),
                    style = TextStyle(color = WidgetText, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    text = UiText.TEXT_4E8F09C59A.text(),
                    style = TextStyle(color = WidgetMuted, fontSize = 12.sp),
                )
            } else {
                entries.forEach { entry ->
                    WidgetEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun WidgetEntryRow(entry: WidgetEntry) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(vertical = 3.dp),
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(22.dp)
                .background(ColorProvider(day = entry.accent, night = entry.accent)),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = entry.leading,
            modifier = GlanceModifier.width(48.dp),
            style = TextStyle(color = WidgetPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(
            text = entry.title,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = WidgetText, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

private fun widgetEntries(
    today: LocalDate,
    upcoming: List<AgendaWithLinks>,
    maxRows: Int,
): List<WidgetEntry> {
    return upcoming.take(maxRows).map { item ->
        val date = Instant.ofEpochMilli(item.agenda.occursAt).atZone(ZoneId.systemDefault()).toLocalDate()
        WidgetEntry(
            leading = when (date) {
                today -> UiText.TEXT_17E83CC25E.text()
                today.plusDays(1) -> UiText.TEXT_B76CE230D3.text()
                else -> date.format(DateTimeFormatter.ofPattern("M/d").withLocale(UiText.displayLocale()))
            },
            title = item.agenda.title,
            accent = AgendaAccent,
        )
    }
}

