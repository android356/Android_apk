package com.autodeploy.infinityfree.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "temporary_backups",
    indices = [
        Index("project_id"),
        Index("relative_path"),
        Index("expires_at")
    ]
)
data class TemporaryBackupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "backup_path")
    val backupPath: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long = System.currentTimeMillis() + (60 * 60 * 1000L), // 1 hour default
    @ColumnInfo(name = "version_identifier")
    val versionIdentifier: String,
    @ColumnInfo(name = "status")
    val status: String = "AVAILABLE" // AVAILABLE, RESTORED, EXPIRED, DELETED
)
