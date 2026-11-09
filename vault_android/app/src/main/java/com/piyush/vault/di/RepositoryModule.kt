package com.piyush.vault.di

import com.piyush.vault.data.repository.RoomCategoryRepository
import com.piyush.vault.data.repository.RoomTransactionRepository
import com.piyush.vault.domain.repository.CategoryRepository
import com.piyush.vault.domain.repository.TransactionRepository
import com.piyush.vault.domain.repository.AccountRepository
import com.piyush.vault.data.repository.RoomAccountRepository
import com.piyush.vault.domain.repository.CsvCodec
import com.piyush.vault.data.csv.LocalCsvCodec
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindOnboardingRepository(implementation: com.piyush.vault.data.preferences.OnboardingPreferences): com.piyush.vault.domain.repository.OnboardingRepository
    @Binds abstract fun bindSmsScheduler(implementation: com.piyush.vault.data.sms.SmsScheduler): com.piyush.vault.data.sms.SmsWorkScheduler
    @Binds abstract fun bindSmsRepository(implementation: com.piyush.vault.data.sms.SmsCoordinator): com.piyush.vault.domain.repository.SmsRepository
    @Binds abstract fun bindParser(implementation: com.piyush.vault.data.sms.parsing.GenericFinancialSmsParser): com.piyush.vault.domain.model.FinancialSmsParser
    @Binds abstract fun bindInstitutions(implementation: com.piyush.vault.data.directory.BundledInstitutionDirectory): com.piyush.vault.domain.model.InstitutionDirectory
    @Binds abstract fun bindCsvCodec(implementation: LocalCsvCodec): CsvCodec
    @Binds
    @Singleton
    abstract fun bindAccountRepository(implementation: RoomAccountRepository): AccountRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        implementation: RoomTransactionRepository,
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(
        implementation: RoomCategoryRepository,
    ): CategoryRepository
}
