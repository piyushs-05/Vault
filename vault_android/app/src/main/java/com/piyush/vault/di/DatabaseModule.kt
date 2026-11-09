package com.piyush.vault.di

import android.content.Context
import androidx.room.Room
import com.piyush.vault.data.local.VaultDatabase
import com.piyush.vault.data.local.migration.MIGRATION_1_2
import com.piyush.vault.data.local.migration.MIGRATION_2_3
import com.piyush.vault.data.local.migration.MIGRATION_3_4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "vault.db"

    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4).build()
}
