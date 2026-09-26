package com.ray.classflow

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.ray.classflow.data.ClassFlowRepository
import com.ray.classflow.data.db.ClassFlowDatabase
import com.ray.classflow.sync.CredentialStore
import com.ray.classflow.sync.NextcloudApi

class ClassFlowApplication : Application() {
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL,
                "課業提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "作業、考試與活動提醒" },
        )
        repository.schedulePeriodicSync()
    }

    companion object {
        const val REMINDER_CHANNEL = "classflow_reminders"
    }
}