package `in`.sakhi.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.sakhi.core.domain.repository.InferenceEngine
import `in`.sakhi.core.inference.NetworkInferenceEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class InferenceModule {
    @Binds
    @Singleton
    abstract fun bindInferenceEngine(network: NetworkInferenceEngine): InferenceEngine
}
