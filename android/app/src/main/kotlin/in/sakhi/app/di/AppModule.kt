package `in`.sakhi.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.sakhi.app.BuildConfig
import javax.inject.Named
import javax.inject.Singleton

/**
 * Provides app-level configuration values as named strings.
 *
 * Secrets (Supabase URL/key, model URL/SHA-256) are read from BuildConfig,
 * which in turn reads from local.properties at build time.
 *
 * local.properties (not committed to git) should contain:
 *   SUPABASE_URL=https://your-project.supabase.co
 *   SUPABASE_ANON_KEY=eyJh...
 *   MODEL_DOWNLOAD_URL=https://...gemma4-e2b.litertlm    (release only)
 *   MODEL_SHA256=abc123...                                (release only)
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton @Named("supabase_url")
    fun provideSupabaseUrl(): String = BuildConfig.SUPABASE_URL

    @Provides @Singleton @Named("supabase_anon_key")
    fun provideSupabaseAnonKey(): String = BuildConfig.SUPABASE_ANON_KEY

    @Provides @Singleton @Named("model_download_url")
    fun provideModelDownloadUrl(): String = BuildConfig.MODEL_DOWNLOAD_URL

    @Provides @Singleton @Named("model_sha256")
    fun provideModelSha256(): String = BuildConfig.MODEL_SHA256
}
