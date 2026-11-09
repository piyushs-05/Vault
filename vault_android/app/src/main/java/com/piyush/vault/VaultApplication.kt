package com.piyush.vault

import android.app.Application
import com.piyush.vault.data.seed.DevelopmentDataSeeder
import com.piyush.vault.domain.usecase.SeedDefaultCategoriesUseCase
import com.piyush.vault.data.repository.LedgerStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class VaultApplication : Application(), androidx.work.Configuration.Provider {

    @Inject
    lateinit var seedDefaultCategoriesProvider: Provider<SeedDefaultCategoriesUseCase>

    @Inject
    lateinit var developmentDataSeeder: DevelopmentDataSeeder

    @Inject lateinit var ledgerStore: LedgerStore

    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory
    @Inject lateinit var sms: com.piyush.vault.data.sms.SmsCoordinator
    @Inject @com.piyush.vault.di.ApplicationScope lateinit var applicationScope: CoroutineScope
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            ledgerStore.seed()
            seedDefaultCategoriesProvider.get()()
            developmentDataSeeder.seedIfNeeded()
            sms.recover()
        }
    }
}
