package com.ray.classflow.ui

import com.ray.classflow.i18n.UiText

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
        composeRule.onNodeWithText(UiText.TEXT_1C16B9D2A8.text()).assertIsDisplayed()
        composeRule.onNodeWithText(UiText.TEXT_4363945A7E.text()).assertIsDisplayed()
        composeRule.onNodeWithText(UiText.TEXT_7769DE4349.text()).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(UiText.TEXT_6329F21C41.text()).performClick()
        composeRule.onNodeWithText(UiText.TEXT_5E7D6AE4CE.text()).assertIsDisplayed()
        composeRule.onNodeWithText(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(com.ray.classflow.R.string.app_name)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(UiText.TEXT_098D06ECAC.text()).assertDoesNotExist()
        composeRule.onNodeWithText(UiText.TEXT_8FA2CB0892.text()).assertDoesNotExist()
    }
}
