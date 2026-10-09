package com.ray.classflow

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import com.ray.classflow.i18n.UiText
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationTest {
    private fun text(language: String, message: UiText): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(config).getString(message.resourceId)
    }

    @Test fun scriptsAndUnsupportedLocaleUseCorrectResources() {
        assertEquals("课表", text("zh-CN", UiText.TEXT_3833660DD9))
        assertEquals("课表", text("zh-SG", UiText.TEXT_3833660DD9))
        assertEquals("課表", text("zh-TW", UiText.TEXT_3833660DD9))
        assertEquals("課表", text("zh-HK", UiText.TEXT_3833660DD9))
        assertEquals("課表", text("en-US", UiText.TEXT_3833660DD9))
        assertEquals("学习计划", text("zh-CN", UiText.TEXT_4363945A7E))
        assertEquals(Locale.SIMPLIFIED_CHINESE, UiText.displayLocale(Locale.forLanguageTag("zh-SG")))
        assertEquals(Locale.TRADITIONAL_CHINESE, UiText.displayLocale(Locale.US))
        assertEquals(Locale.TRADITIONAL_CHINESE, UiText.displayLocale(Locale.forLanguageTag("zh-Hant-CN")))
    }
}
