package com.piyush.vault.data.sms.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.piyush.vault.data.sms.SmsCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class SmsDrainWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val coordinator: SmsCoordinator,
) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try {
        coordinator.drain(); Result.success()
    } catch (e: CancellationException) { throw e }
      catch (_: SecurityException) { coordinator.failure("SMS access revoked. Re-enable it in SMS settings."); Result.failure() }
      catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry()
        else { coordinator.failure("Couldn't process SMS. Open SMS settings to retry."); Result.failure() }
    }
}

@HiltWorker
class SmsHistoryWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val coordinator: SmsCoordinator,
) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try {
        val token = inputData.getString("scan_token") ?: return Result.failure()
        coordinator.readHistory(token); Result.success()
    } catch (e: CancellationException) { throw e }
      catch (_: SecurityException) { coordinator.failure("SMS access revoked. Re-enable it in SMS settings."); Result.failure() }
      catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry()
        else { coordinator.failure("Couldn't scan SMS. Open SMS settings to retry."); Result.failure() }
    }
}

@HiltWorker
class SmsCleanupWorker @AssistedInject constructor(
    @Assisted context: Context, @Assisted params: WorkerParameters,
    private val coordinator: SmsCoordinator,
) : CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try {
        coordinator.cleanup(); Result.success()
    } catch (e: CancellationException) { throw e }
      catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
}
