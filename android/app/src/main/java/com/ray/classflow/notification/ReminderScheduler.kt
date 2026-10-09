package com.ray.classflow.notification

import com.ray.classflow.i18n.UiText

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ray.classflow.ClassFlowApplication
import com.ray.classflow.MainActivity
import com.ray.classflow.R
import com.ray.classflow.model.AgendaItem
import com.ray.classflow.model.AgendaStatus
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    fun schedule(context: Context, item: AgendaItem) {
        cancel(context, item.id)
        val reminderAt = item.reminderAt ?: return
        if (item.status == AgendaStatus.COMPLETED || reminderAt <= System.currentTimeMillis()) return
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(reminderAt - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("id", item.id).putString("title", item.title).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(item.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    fun rescheduleAll(context: Context, items: List<AgendaItem>) {
        items.forEach { schedule(context, it) }
    }

    private fun workName(id: String) = "classflow-reminder-${id}"
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (
            android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val id = inputData.getString("id") ?: return Result.failure()
        val title = inputData.getString("title") ?: UiText.TEXT_785FBB16DD.text()
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("agendaId", id)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, ClassFlowApplication.REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(UiText.TEXT_9962FC3D77.text())
            .setContentText(title)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
        return Result.success()
    }
}

