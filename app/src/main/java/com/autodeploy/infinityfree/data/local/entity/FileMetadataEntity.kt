package com.autodeploy.infinityfree.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "file_metadata",
    indices = [
        Index("project_id"),
        Index(value = ["project_id", "relative_path"], unique = true),
        Index("sync_status"),
        Index("relative_path")
    ]
)
data class FileMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "item_type")
    val itemType: String = "FILE", // FILE or DIRECTORY
    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,
    @ColumnInfo(name = "last_modified")
    val lastModified: Long = 0L,
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "NOT_SYNCED", // NOT_SYNCED, SYNCED, PENDING, FAILED, MODIFIED
    @ColumnInfo(name = "optional_hash")
    val optionalHash: String? = null,
    @ColumnInfo(name = "is_present")
    val isPresent: Boolean = true
)
