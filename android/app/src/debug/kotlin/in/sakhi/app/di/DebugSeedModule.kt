package `in`.sakhi.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.sakhi.app.debug.DebugDataSeeder
import `in`.sakhi.app.debug.DebugDataSeederImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugSeedModule {
    @Binds
    @Singleton
    abstract fun bindDebugDataSeeder(impl: DebugDataSeederImpl): DebugDataSeeder
}
