package com.siddhant.ordertracker.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncAction: suspend () -> Result<Int>,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val result = syncAction()
            result.fold(
                onSuccess = { count ->
                    Log.i(TAG, "Synced $count pending updates")
                    Result.success()
                },
                onFailure = { e ->
                    Log.e(TAG, "Sync failed", e)
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val TAG = "SyncWorker"
        const val MAX_RETRIES = 3
        const val WORK_NAME = "order_sync"
    }
}
