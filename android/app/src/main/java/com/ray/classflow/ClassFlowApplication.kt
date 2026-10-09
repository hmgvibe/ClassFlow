package com.ray.classflow

import com.ray.classflow.i18n.UiText

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Configuration
import androidx.glance.appwidget.updateAll
import com.ray.classflow.data.ClassFlowRepository
import com.ray.classflow.data.db.ClassFlowDatabase
import com.ray.classflow.sync.CredentialStore
import com.ray.classflow.sync.NextcloudApi
import com.ray.classflow.widget.ClassFlowWidget
import com.ray.classflow.widget.TimetableWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClassFlowApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database by lazy { ClassFlowDatabase.get(this) }
    val repository by lazy {
        ClassFlowRepository(
            context = this,
            database = database,
            credentials = CredentialStore(this),
            api = NextcloudApi(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        UiText.initialize(this)
        updateReminderChannel()
        if (BuildConfig.CLOUD_SYNC_ENABLED) repository.schedulePeriodicSync()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        UiText.initialize(createConfigurationContext(newConfig))
        updateReminderChannel()
        applicationScope.launch {
            ClassFlowWidget().updateAll(this@ClassFlowApplication)
            TimetableWidget().updateAll(this@ClassFlowApplication)
        }
    }

    private fun updateReminderChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                        REMINDER_CHANNEL,
                        UiText.TEXT_41119406CC.text(),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                    .apply { description = UiText.TEXT_B8FC334B77.text() }
            )
    }

    companion object {
        const val REMINDER_CHANNEL = "classflow_reminders"
    }
}
