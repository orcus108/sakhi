package `in`.sakhi.core.rag

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RagModule {

    @Provides
    @Singleton
    fun provideGuidelineDao(@ApplicationContext context: Context): GuidelineDao =
        GuidelineDao(GuidelinesDatabase.open(context))
}
