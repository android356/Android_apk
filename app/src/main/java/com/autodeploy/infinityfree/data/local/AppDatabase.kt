package com.autodeploy.infinityfree.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.autodeploy.infinityfree.data.local.dao.*
import com.autodeploy.infinityfree.data.local.entity.*

@Database(
    entities = [
        ProjectEntity::class,
        HostingConnectionEntity::class,
        FileMetadataEntity::class,
        SyncQueueEntity::class,
        TemporaryBackupEntity::class,
        SyncHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun hostingConnectionDao(): HostingConnectionDao
    abstract fun fileMetadataDao(): FileMetadataDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun temporaryBackupDao(): TemporaryBackupDao
    abstract fun syncHistoryDao(): SyncHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "auto_deploy_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
