package com.ray.classflow.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.ray.classflow.model.*
import com.ray.classflow.ui.screens.StudyPlanScreen
import com.ray.classflow.ui.theme.ClassFlowTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class StudyPlanScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun narrowScreenOffersFiveDatesAndAllThreePeriods() {
        composeRule.setContent {
            ClassFlowTheme {
                Box(Modifier.width(320.dp)) {
                    StudyPlanScreen(ClassFlowState(), PaddingValues(), {}, {})
                }
            }
        }
        composeRule.onAllNodes(isSelectable()).assertCountEquals(5)
        composeRule.onNodeWithText("上午").assertIsDisplayed()
        composeRule.onNodeWithText("下午").assertIsDisplayed()
        composeRule.onNodeWithText("晚上").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun selectingExamShowsReferenceWithoutChangingManualTimes() {
        val examTime =
            LocalDate.now().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val data =
            ClassFlowState(agenda = listOf(AgendaItem("exam", AgendaType.EXAM, "期中考", examTime)))
        composeRule.setContent { ClassFlowTheme { StudyPlanScreen(data, PaddingValues(), {}, {}) } }
        composeRule.onNodeWithText("新增計劃").performClick()
        composeRule.onNodeWithText("不連結").performClick()
        composeRule.onNodeWithText("考試").performClick()
        composeRule.onNodeWithText("選擇連結對象").performClick()
        composeRule.onNodeWithText("期中考").performClick()
        composeRule.onNodeWithText("以上時間僅供參考，不會自動更改計劃時間。").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("14:00").assertExists()
        composeRule.onNodeWithText("15:00").assertExists()
    }
}
