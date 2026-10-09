package com.ray.classflow.ui

import com.ray.classflow.i18n.UiText

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
        composeRule.onNodeWithText(UiText.TEXT_7769DE4349.text()).assertIsDisplayed()
        composeRule.onNodeWithText(UiText.TEXT_4A2345DCE0.text()).assertIsDisplayed()
    }
}

