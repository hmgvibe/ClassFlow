package com.ray.classflow.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ray.classflow.ui.theme.ClassFlowTheme
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loginScreenShowsPrimaryActions() {
        composeRule.setContent {
            ClassFlowTheme {
                LoginScreen(isLoading = false, onConnect = {}, onOffline = {})
            }
        }
        composeRule.onNodeWithText("連接 Nextcloud").assertIsDisplayed()
        composeRule.onNodeWithText("先離線使用").assertIsDisplayed()
    }
}

