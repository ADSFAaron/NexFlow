/*
 * Copyright 2026 NexFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nexflow.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexflow.core.automation.repository.FlowRepository
import com.nexflow.prefs.LogRetentionPrefs
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class LogPrunerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: FlowRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val option = LogRetentionPrefs.get(applicationContext)
        val cutoff = System.currentTimeMillis() - option.days.toLong() * 24 * 60 * 60 * 1_000
        repository.deleteOldLogs(keepCount = option.maxCount, olderThanMs = cutoff)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nexflow_log_pruner"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LogPrunerWorker>(7, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
