package com.piyush.vault.di

import com.piyush.vault.data.seed.DevelopmentDataSeeder
import com.piyush.vault.data.seed.NoOpDevelopmentDataSeeder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DevelopmentDataSeederModule {
    @Binds
    abstract fun bindDevelopmentDataSeeder(
        implementation: NoOpDevelopmentDataSeeder,
    ): DevelopmentDataSeeder
}
