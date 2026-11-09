package com.piyush.vault.di

import com.piyush.vault.domain.repository.CategoryRepository
import com.piyush.vault.domain.repository.TransactionRepository
import com.piyush.vault.domain.usecase.ArchiveCategoryUseCase
import com.piyush.vault.domain.usecase.CountCategoryTransactionsUseCase
import com.piyush.vault.domain.usecase.DeleteTransactionUseCase
import com.piyush.vault.domain.usecase.GetTransactionUseCase
import com.piyush.vault.domain.usecase.ObserveAvailableCategoriesUseCase
import com.piyush.vault.domain.usecase.ObserveCategoriesUseCase
import com.piyush.vault.domain.usecase.ObserveCategorySpendingUseCase
import com.piyush.vault.domain.usecase.ObservePeriodTotalsUseCase
import com.piyush.vault.domain.usecase.ObserveTransactionsUseCase
import com.piyush.vault.domain.usecase.PermanentlyDeleteCategoryUseCase
import com.piyush.vault.domain.usecase.SaveCategoryUseCase
import com.piyush.vault.domain.usecase.SaveCsvImportUseCase
import com.piyush.vault.domain.usecase.SaveTransactionUseCase
import com.piyush.vault.domain.usecase.SeedDefaultCategoriesUseCase
import com.piyush.vault.domain.usecase.UndoDeleteTransactionUseCase
import com.piyush.vault.domain.usecase.WasFileImportedUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.piyush.vault.domain.repository.AccountRepository
import com.piyush.vault.domain.usecase.ObserveAccountsUseCase
import com.piyush.vault.domain.usecase.SaveAccountUseCase
import com.piyush.vault.domain.usecase.ArchiveAccountUseCase
import com.piyush.vault.domain.usecase.AssignLegacyTransactionsUseCase
import java.time.Clock

/** Keeps the domain use cases free of Hilt and injection annotations. */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {
    @Provides fun provideReconcile(repository: TransactionRepository) = com.piyush.vault.domain.usecase.ReconcileTransferUseCase(repository)
    @Provides fun provideSms(repository: com.piyush.vault.domain.repository.SmsRepository) = com.piyush.vault.domain.usecase.SmsUseCases(repository)
    @Provides fun provideClock(): Clock = Clock.systemDefaultZone()
    @Provides fun provideObserveAccounts(repository: AccountRepository) = ObserveAccountsUseCase(repository)
    @Provides fun provideSaveAccount(repository: AccountRepository) = SaveAccountUseCase(repository)
    @Provides fun provideArchiveAccount(repository: AccountRepository) = ArchiveAccountUseCase(repository)
    @Provides fun provideAssignLegacy(repository: AccountRepository) = AssignLegacyTransactionsUseCase(repository)

    @Provides
    fun provideObserveTransactions(repository: TransactionRepository) =
        ObserveTransactionsUseCase(repository)

    @Provides
    fun provideGetTransaction(repository: TransactionRepository) =
        GetTransactionUseCase(repository)

    @Provides
    fun provideObservePeriodTotals(repository: TransactionRepository) =
        ObservePeriodTotalsUseCase(repository)

    @Provides
    fun provideObserveCategorySpending(repository: TransactionRepository) =
        ObserveCategorySpendingUseCase(repository)

    @Provides
    fun provideSaveTransaction(
        repository: TransactionRepository,
        categoryRepository: CategoryRepository,
    ) = SaveTransactionUseCase(repository, categoryRepository)

    @Provides
    fun provideDeleteTransaction(repository: TransactionRepository) =
        DeleteTransactionUseCase(repository)

    @Provides
    fun provideUndoDeleteTransaction(repository: TransactionRepository) =
        UndoDeleteTransactionUseCase(repository)

    @Provides
    fun provideObserveCategories(repository: CategoryRepository) =
        ObserveCategoriesUseCase(repository)

    @Provides
    fun provideObserveAvailableCategories(repository: CategoryRepository) =
        ObserveAvailableCategoriesUseCase(repository)

    @Provides
    fun provideSaveCategory(repository: CategoryRepository) =
        SaveCategoryUseCase(repository)

    @Provides
    fun provideArchiveCategory(repository: CategoryRepository) =
        ArchiveCategoryUseCase(repository)

    @Provides
    fun provideCountCategoryTransactions(repository: CategoryRepository) =
        CountCategoryTransactionsUseCase(repository)

    @Provides
    fun providePermanentlyDeleteCategory(repository: CategoryRepository) =
        PermanentlyDeleteCategoryUseCase(repository)

    @Provides
    fun provideSeedDefaultCategories(repository: CategoryRepository) =
        SeedDefaultCategoriesUseCase(repository)

    @Provides
    fun provideWasFileImported(repository: TransactionRepository) =
        WasFileImportedUseCase(repository)

    @Provides
    fun provideSaveCsvImport(
        repository: TransactionRepository,
        categoryRepository: CategoryRepository,
    ) = SaveCsvImportUseCase(repository, categoryRepository)
}
