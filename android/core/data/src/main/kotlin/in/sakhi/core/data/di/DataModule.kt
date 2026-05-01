package `in`.sakhi.core.data.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.sakhi.core.data.db.DatabaseKeyManager
import `in`.sakhi.core.data.db.SakhiDatabase
import `in`.sakhi.core.data.db.dao.AncCheckupDao
import `in`.sakhi.core.data.db.dao.AncPatientDao
import `in`.sakhi.core.data.db.dao.AssessmentDao
import `in`.sakhi.core.data.db.dao.AuditLogDao
import `in`.sakhi.core.data.db.dao.ChatMessageDao
import `in`.sakhi.core.data.db.dao.NewbornPatientDao
import `in`.sakhi.core.data.db.dao.NewbornVisitDao
import `in`.sakhi.core.data.db.dao.SyncQueueDao
import `in`.sakhi.core.data.db.dao.WorkerProfileDao
import `in`.sakhi.core.data.repository.AssessmentRepositoryImpl
import `in`.sakhi.core.data.repository.ChatRepositoryImpl
import `in`.sakhi.core.data.repository.PatientRepositoryImpl
import `in`.sakhi.core.domain.repository.AssessmentRepository
import `in`.sakhi.core.domain.repository.ChatRepository
import `in`.sakhi.core.domain.repository.PatientRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {

    @Binds @Singleton
    abstract fun bindPatientRepository(impl: PatientRepositoryImpl): PatientRepository

    @Binds @Singleton
    abstract fun bindAssessmentRepository(impl: AssessmentRepositoryImpl): AssessmentRepository

    @Binds @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * Database is provided lazily — null until the user has authenticated and we have a userId
     * to derive the passphrase. Features must handle the null case (show loading/auth screen).
     *
     * In practice, the NavHost redirects to Onboarding before any feature screen can request
     * the DB, so this null is only visible during app startup before auth completes.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager,
        authPrefs: `in`.sakhi.core.data.auth.AuthPreferences
    ): SakhiDatabase {
        val userId = authPrefs.getUserId()
            ?: return SakhiDatabase.buildInMemory(context)  // Fallback until auth completes; replaced on login
        val passphrase = keyManager.derivePassphrase(userId)
        return SakhiDatabase.build(context, passphrase)
    }

    @Provides fun provideAncPatientDao(db: SakhiDatabase): AncPatientDao = db.ancPatientDao()
    @Provides fun provideNewbornPatientDao(db: SakhiDatabase): NewbornPatientDao = db.newbornPatientDao()
    @Provides fun provideAncCheckupDao(db: SakhiDatabase): AncCheckupDao = db.ancCheckupDao()
    @Provides fun provideNewbornVisitDao(db: SakhiDatabase): NewbornVisitDao = db.newbornVisitDao()
    @Provides fun provideAssessmentDao(db: SakhiDatabase): AssessmentDao = db.assessmentDao()
    @Provides fun provideChatMessageDao(db: SakhiDatabase): ChatMessageDao = db.chatMessageDao()
    @Provides fun provideSyncQueueDao(db: SakhiDatabase): SyncQueueDao = db.syncQueueDao()
    @Provides fun provideAuditLogDao(db: SakhiDatabase): AuditLogDao = db.auditLogDao()
    @Provides fun provideWorkerProfileDao(db: SakhiDatabase): WorkerProfileDao = db.workerProfileDao()
}
