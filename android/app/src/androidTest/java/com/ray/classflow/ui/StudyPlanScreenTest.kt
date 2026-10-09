package com.ray.classflow.ui

import com.ray.classflow.i18n.UiText
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
        composeRule.onNodeWithText(UiText.TEXT_E214EE22DD.text(), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(UiText.TEXT_1DFAAB6548.text(), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(UiText.TEXT_76A4159492.text(), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun selectingExamShowsReferenceWithoutChangingManualTimes() {
        val examTime =
            LocalDate.now().atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val data =
            ClassFlowState(agenda = listOf(AgendaItem("exam", AgendaType.EXAM, "期中考", examTime)))
        composeRule.setContent { ClassFlowTheme { StudyPlanScreen(data, PaddingValues(), {}, {}) } }
        composeRule.onNodeWithText(UiText.TEXT_AEE8B65355.text(), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText(UiText.TEXT_6958A7063E.text(), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText(UiText.TEXT_2AA23AAED8.text(), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText(UiText.TEXT_A4C0BB8817.text(), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("期中考").performClick()
        composeRule.onNodeWithText(UiText.TEXT_BD559F688E.text(), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("14:00").assertExists()
        composeRule.onNodeWithText("15:00").assertExists()
    }
}
