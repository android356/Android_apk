package com.autodeploy.infinityfree.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.autodeploy.infinityfree.data.local.dao.*
import com.autodeploy.infinityfree.data.local.entity.*

@Database(
    entities = [
        ProjectEntity::class,
        GitHubConnectionEntity::class,
        HostingConnectionEntity::class,
        ShrotiHostConnectionEntity::class,
        FileMetadataEntity::class,
        SyncQueueEntity::class,
        TemporaryBackupEntity::class,
        BackupSnapshotEntity::class,
        SyncHistoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun githubConnectionDao(): GitHubConnectionDao
    abstract fun hostingConnectionDao(): HostingConnectionDao
    abstract fun shrotiHostConnectionDao(): ShrotiHostConnectionDao
    abstract fun fileMetadataDao(): FileMetadataDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun temporaryBackupDao(): TemporaryBackupDao
    abstract fun backupSnapshotDao(): BackupSnapshotDao
    abstract fun syncHistoryDao(): SyncHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `github_connections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `project_id` INTEGER NOT NULL,
                        `owner` TEXT NOT NULL,
                        `repo` TEXT NOT NULL,
                        `branch` TEXT NOT NULL,
                        `destination_path` TEXT NOT NULL,
                        `encrypted_token_reference` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_github_connections_project_id` ON `github_connections` (`project_id`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create shrotihost_connections table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `shrotihost_connections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `project_id` INTEGER NOT NULL,
                        `connection_name` TEXT NOT NULL,
                        `server` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `username` TEXT NOT NULL,
                        `encrypted_password_reference` TEXT NOT NULL,
                        `remote_root_directory` TEXT NOT NULL,
                        `use_ftps` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shrotihost_connections_project_id` ON `shrotihost_connections` (`project_id`)")

                // 2. Create backup_snapshots table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `backup_snapshots` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `project_id` INTEGER NOT NULL,
                        `version_code` INTEGER NOT NULL,
                        `version_tag` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `snapshot_directory` TEXT NOT NULL,
                        `file_count` INTEGER NOT NULL,
                        `total_size_bytes` INTEGER NOT NULL,
                        `manifest_json` TEXT NOT NULL,
                        `is_stable` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_snapshots_project_id` ON `backup_snapshots` (`project_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_snapshots_version_code` ON `backup_snapshots` (`version_code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_snapshots_is_stable` ON `backup_snapshots` (`is_stable`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_snapshots_created_at` ON `backup_snapshots` (`created_at`)")

                // 3. Alter sync_queue table to add new columns safely
                try {
                    db.execSQL("ALTER TABLE `sync_queue` ADD COLUMN `target_provider` TEXT NOT NULL DEFAULT 'INFINITY_FREE'")
                } catch (ignored: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `sync_queue` ADD COLUMN `backup_id` INTEGER")
                } catch (ignored: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `sync_queue` ADD COLUMN `verified` INTEGER NOT NULL DEFAULT 0")
                } catch (ignored: Exception) {}
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE `sync_history` ADD COLUMN `target_provider` TEXT NOT NULL DEFAULT 'INFINITY_FREE'")
                } catch (ignored: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `sync_history` ADD COLUMN `duration_ms` INTEGER NOT NULL DEFAULT 0")
                } catch (ignored: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `sync_history` ADD COLUMN `retry_count` INTEGER NOT NULL DEFAULT 0")
                } catch (ignored: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `sync_history` ADD COLUMN `verified` INTEGER NOT NULL DEFAULT 0")
                } catch (ignored: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `sync_history` ADD COLUMN `error_category` TEXT")
                } catch (ignored: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `sync_history` ADD COLUMN `rollback_info` TEXT")
                } catch (ignored: Exception) {}
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_history_target_provider` ON `sync_history` (`target_provider`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "auto_deploy_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
