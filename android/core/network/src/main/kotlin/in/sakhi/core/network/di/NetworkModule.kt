package `in`.sakhi.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import `in`.sakhi.core.network.SupabaseSyncApi
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(
        @Named("supabase_url") url: String,
        @Named("supabase_anon_key") key: String
    ): SupabaseClient = createSupabaseClient(url, key) {
        install(Auth)
        install(Postgrest)
    }

    @Provides
    @Singleton
    fun provideAuth(client: SupabaseClient): Auth = client.auth

    @Provides
    @Singleton
    fun providePostgrest(client: SupabaseClient): Postgrest = client.postgrest

    @Provides
    @Singleton
    fun provideSupabaseSyncApi(
        client: SupabaseClient,
        @Named("supabase_url") supabaseUrl: String,
        @Named("supabase_anon_key") supabaseKey: String
    ): SupabaseSyncApi = SupabaseSyncApi(client, supabaseUrl, supabaseKey)
}
