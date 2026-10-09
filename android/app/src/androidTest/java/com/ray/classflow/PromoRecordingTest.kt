package com.ray.classflow

import android.app.LocaleManager
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.ray.classflow.model.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in production-Activity recording. Run only on the isolated ClassFlow_Promo AVD. */
class PromoRecordingTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ClassFlowApplication
    private val repo get() = app.repository
    private var recordingStarted = 0L
    private val touches = org.json.JSONArray()
    private fun pause(ms: Long = 1200) { ui.waitForIdle(); SystemClock.sleep(ms) }
    private fun touch(node: SemanticsNodeInteraction, label: String) {
        val semantics = node.fetchSemanticsNode()
        val center = semantics.positionOnScreen + androidx.compose.ui.geometry.Offset(
            semantics.size.width / 2f, semantics.size.height / 2f)
        if (recordingStarted != 0L) touches.put(org.json.JSONObject()
            .put("time", (SystemClock.elapsedRealtime() - recordingStarted) / 1000.0)
            .put("x", center.x).put("y", center.y).put("label", label))
        node.performTouchInput { click() }
    }
    private fun tap(text: String) { touch(ui.onAllNodesWithText(text, useUnmergedTree = true).onLast(), text); pause() }
    private fun at(hour: Int, days: Long = 0) = LocalDate.now().plusDays(days).atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recordPromo") == "true")
        require(app.packageName.endsWith(".debug"))
        val avd = instrumentation.uiAutomation.executeShellCommand("getprop ro.boot.qemu.avd_name")
        require(ParcelFileDescriptor.AutoCloseInputStream(avd).bufferedReader().use { it.readText().trim() } == "ClassFlow_Promo")
        app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("zh-CN")
        require(repo.account() == null) { "Recording must use an unconnected disposable test app" }
        runBlocking {
            repo.state.first().studyPlans.filter { it.title == "复习数学第二章" }.forEach { repo.deleteStudyPlan(it) }
            val names = listOf("高等数学", "大学英语", "程序设计")
            val teachers = listOf("陈老师", "林老师", "王老师")
            val rooms = listOf("教学楼 A201", "教学楼 B302", "计算机教室")
            names.forEachIndexed { n, name -> repo.saveCourse(Course("promo-course-$n", name, teachers[n], rooms[n], listOf(0,1,3)[n])) }
            for (day in 1..5) for (n in 0..2) repo.saveSlot(TimetableSlot("promo-slot-$day-$n", "promo-course-$n", day, listOf(480,600,840)[n], listOf(570,690,930)[n]))
            repo.saveAgenda(AgendaItem("promo-homework", AgendaType.HOMEWORK, "完成数学第二章练习", at(17), notes="整理不熟悉的题型"))
            repo.saveAgenda(AgendaItem("promo-exam", AgendaType.EXAM, "数学期中考试", at(9,3), endsAt=at(11,3), notes="第一章至第三章"))
            repo.saveAgenda(AgendaItem("promo-activity", AgendaType.ACTIVITY, "英语小组讨论", at(19), endsAt=at(20)))
            repo.saveStudyPlan(StudyPlan("promo-notes", "整理课堂笔记", at(9), at(10), linkedCourseId="promo-course-0", notes="梳理关键公式与例题"))
        }
    }

    private fun record(name: String, action: () -> Unit) {
        val fd = instrumentation.uiAutomation.executeShellCommand("screenrecord --size 1080x1920 --bit-rate 12000000 --time-limit 120 /sdcard/classflow-$name.mp4")
        val reader = Thread { ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.copyTo(java.io.ByteArrayOutputStream()) } }
        reader.start()
        recordingStarted = SystemClock.elapsedRealtime()
        while (touches.length() > 0) touches.remove(0)
        pause(1000)
        try { action() } finally {
            instrumentation.uiAutomation.executeShellCommand("pkill -2 screenrecord").close()
            reader.join(5000)
            java.io.File(app.filesDir, "promo-$name-touches.json").writeText(touches.toString(2))
            recordingStarted = 0L
        }
    }

    @Test fun recordStudentWorkflow() {
        prepare()
        ActivityScenario.launch(MainActivity::class.java).use {
            pause()
            if (BuildConfig.CLOUD_SYNC_ENABLED) tap("先离线使用")
            record(if (BuildConfig.CLOUD_SYNC_ENABLED) "timetable" else "offline") {
                pause(2000)
                if (!BuildConfig.CLOUD_SYNC_ENABLED) {
                    touch(ui.onNodeWithContentDescription("设置"), "设置")
                    pause(2500)
                    return@record
                }
                tap("周四"); tap("周五")
                tap("管理课程"); tap("高等数学"); pause(1800)
                tap("取消"); tap("完成")
                pause(1800)
            }
            if (!BuildConfig.CLOUD_SYNC_ENABLED) return
            tap("日程")
            record("agenda") {
                pause(1800)
                tap("含已完成")
                touch(ui.onAllNodes(isToggleable()).onFirst(), "完成作业")
                pause(2500)
                runBlocking { withTimeout(5000) { repo.state.first { state -> state.agenda.any { it.id=="promo-homework" && it.status==AgendaStatus.COMPLETED } } } }
                touch(ui.onAllNodesWithText("考试").onFirst(), "考试"); pause(1800); tap("全部"); pause(1500)
            }
            tap("学习计划")
            record("study") {
                pause(1500); tap("添加计划")
                ui.onNodeWithText("计划标题").performClick()
                for (part in listOf("复习", "数学", "第二章")) { ui.onNodeWithText("计划标题").performTextInput(part); pause(400) }
                closeSoftKeyboard(); pause(800)
                ui.onNodeWithText("开始时间").performTextReplacement("14:00")
                ui.onNodeWithText("结束时间").performTextReplacement("15:00")
                closeSoftKeyboard(); pause(800)
                ui.onNodeWithText("不关联").performScrollTo(); tap("不关联"); tap("考试")
                ui.onNodeWithText("选择关联对象").performScrollTo(); tap("选择关联对象"); tap("数学期中考试")
                pause(1500); tap("保存"); pause(2500)
                ui.onNodeWithText("复习数学第二章").assertIsDisplayed()
                runBlocking { withTimeout(5000) { repo.state.first { state -> state.studyPlans.any { it.title=="复习数学第二章" && it.linkedAgendaId=="promo-exam" && it.startsAt==at(14) && it.endsAt==at(15) } } } }
                tap("复习数学第二章"); pause(1500); tap("取消"); pause(1500)
            }
        }
    }

    @Test fun pinTimetableWidget() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("pinPromoWidget") == "true")
        prepare()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            pause()
            activity.onActivity {
                val manager = android.appwidget.AppWidgetManager.getInstance(it)
                assertTrue(manager.isRequestPinAppWidgetSupported)
                assertTrue(manager.requestPinAppWidget(android.content.ComponentName(it, com.ray.classflow.widget.TimetableWidgetReceiver::class.java), null, null))
            }
            SystemClock.sleep(2000)
        }
    }
}
