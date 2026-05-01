package `in`.sakhi.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import `in`.sakhi.core.data.db.dao.AncCheckupDao
import `in`.sakhi.core.data.db.dao.AncPatientDao
import `in`.sakhi.core.data.db.dao.AssessmentDao
import `in`.sakhi.core.data.db.dao.AuditLogDao
import `in`.sakhi.core.data.db.dao.ChatMessageDao
import `in`.sakhi.core.data.db.dao.NewbornPatientDao
import `in`.sakhi.core.data.db.dao.NewbornVisitDao
import `in`.sakhi.core.data.db.dao.SyncQueueDao
import `in`.sakhi.core.data.db.dao.WorkerProfileDao
import `in`.sakhi.core.data.db.entity.AncCheckupEntity
import `in`.sakhi.core.data.db.entity.AncPatientEntity
import `in`.sakhi.core.data.db.entity.AssessmentEntity
import `in`.sakhi.core.data.db.entity.AuditLogEntity
import `in`.sakhi.core.data.db.entity.ChatMessageEntity
import `in`.sakhi.core.data.db.entity.NewbornPatientEntity
import `in`.sakhi.core.data.db.entity.NewbornVisitEntity
import `in`.sakhi.core.data.db.entity.SyncQueueEntity
import `in`.sakhi.core.data.db.entity.WorkerProfileEntity

@Database(
    entities = [
        AncPatientEntity::class,
        NewbornPatientEntity::class,
        AncCheckupEntity::class,
        NewbornVisitEntity::class,
        AssessmentEntity::class,
        ChatMessageEntity::class,
        SyncQueueEntity::class,
        AuditLogEntity::class,
        WorkerProfileEntity::class,
    ],
    version = 1,
    exportSchema = false
)
abstract class SakhiDatabase : RoomDatabase() {
    abstract fun ancPatientDao(): AncPatientDao
    abstract fun newbornPatientDao(): NewbornPatientDao
    abstract fun ancCheckupDao(): AncCheckupDao
    abstract fun newbornVisitDao(): NewbornVisitDao
    abstract fun assessmentDao(): AssessmentDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun workerProfileDao(): WorkerProfileDao

    companion object {
        const val DB_NAME = "sakhi.db"

        /**
         * Build the encrypted Room database.
         *
         * Passphrase = SHA-256(userId + deviceSecret) as ByteArray.
         * IMPORTANT: passphrase must be ByteArray not String to avoid JVM string interning.
         * The caller is responsible for deriving and providing the passphrase.
         * See DatabaseKeyManager for derivation logic.
         */
        fun build(context: Context, passphraseBytes: ByteArray): SakhiDatabase {
            val factory = SupportFactory(passphraseBytes)
            return Room.databaseBuilder(context, SakhiDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration() // Replace with proper migrations before v1 → v2
                .build()
        }

        /** Unencrypted build for use in unit tests (Robolectric) only. */
        fun buildInMemory(context: Context): SakhiDatabase {
            return Room.inMemoryDatabaseBuilder(context, SakhiDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    }
}
