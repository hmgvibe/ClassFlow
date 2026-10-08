package com.ray.classflow.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ray.classflow.ui.theme.ClassFlowTheme
import org.junit.Rule
import org.junit.Test

class OfflineAppTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun startsWithCorePagesAndSettingsHaveNoCloudActions() {
        composeRule.setContent {
            ClassFlowTheme {
                val model: ClassFlowViewModel = viewModel()
                ClassFlowApp(model)
            }
        }
        composeRule.onNodeWithText("日程").assertIsDisplayed()
        composeRule.onNodeWithText("學習計劃").assertIsDisplayed()
        composeRule.onNodeWithText("連接 Nextcloud").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("設定").performClick()
        composeRule.onNodeWithText("本機儲存").assertIsDisplayed()
        composeRule.onNodeWithText("ClassFlow 離線版").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("立即同步").assertDoesNotExist()
        composeRule.onNodeWithText("登出此裝置").assertDoesNotExist()
    }
}
