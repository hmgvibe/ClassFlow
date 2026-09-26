package com.ray.classflow.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ray.classflow.ClassFlowApplication

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        (applicationContext as ClassFlowApplication).repository.performSync()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { if (runAttemptCount < 4) Result.retry() else Result.failure() },
    )
}

