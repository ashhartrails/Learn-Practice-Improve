package com.example.learnanything.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.learnanything.core.data.backup.BackupOutcome
import com.example.learnanything.core.data.backup.BackupPrefs
import com.example.learnanything.core.data.backup.BackupRepository
import com.example.learnanything.core.data.backup.BackupScheduler
import com.example.learnanything.core.data.repository.LearningRepository
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Runs one Drive backup. Retries later on network or server errors; does nothing if backup is off. */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backup: BackupRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val outcome = backup.backupNow()) {
        is BackupOutcome.Done, BackupOutcome.NeedsAccess -> Result.success()
        is BackupOutcome.Failed -> if (outcome.retryable && runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

@Singleton
class WorkManagerBackupScheduler @Inject constructor(@ApplicationContext private val context: Context) : BackupScheduler {
    private val work get() = WorkManager.getInstance(context)

    /** Debounced: finishing several chapters in a row gives one backup a couple of minutes later. */
    override fun chapterFinished() {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(2, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        work.enqueueUniqueWork(CHAPTER_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** Daily, on an unmetered network (Wi-Fi) and not on low battery. */
    override fun setDaily(enabled: Boolean) {
        if (!enabled) {
            work.cancelUniqueWork(DAILY_WORK)
            work.cancelUniqueWork(CHAPTER_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build(),
            )
            .build()
        work.enqueueUniquePeriodicWork(DAILY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val CHAPTER_WORK = "backup-after-chapter"
        const val DAILY_WORK = "backup-daily"
    }
}

/** Watches progress: when the number of finished chapters goes up and backup is on, schedules a backup. */
@Singleton
class ChapterBackupTrigger @Inject constructor(
    private val learning: LearningRepository,
    private val prefs: BackupPrefs,
    private val scheduler: BackupScheduler,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            var last: Int? = null
            learning.observeProgress().map { it.chaptersPassed }.distinctUntilChanged().collect { passed ->
                val before = last
                last = passed
                if (before != null && passed > before && prefs.isEnabled()) scheduler.chapterFinished()
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds abstract fun scheduler(impl: WorkManagerBackupScheduler): BackupScheduler
}
