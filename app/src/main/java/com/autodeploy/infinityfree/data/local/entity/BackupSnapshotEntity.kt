package com.autodeploy.infinityfree.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "backup_snapshots",
    indices = [
        Index("project_id"),
        Index("version_code"),
        Index("is_stable"),
        Index("created_at")
    ]
)
data class BackupSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "version_code")
    val versionCode: Int,
    @ColumnInfo(name = "version_tag")
    val versionTag: String, // e.g. "v001", "v002"
    @ColumnInfo(name = "label")
    val label: String, // e.g. "Pre-deployment Snapshot", "Manual Snapshot", "Emergency Pre-Rollback"
    @ColumnInfo(name = "snapshot_directory")
    val snapshotDirectory: String,
    @ColumnInfo(name = "file_count")
    val fileCount: Int,
    @ColumnInfo(name = "total_size_bytes")
    val totalSizeBytes: Long,
    @ColumnInfo(name = "manifest_json")
    val manifestJson: String,
    @ColumnInfo(name = "is_stable")
    val isStable: Boolean = false,
    @ColumnInfo(name = "status")
    val status: String = "AVAILABLE", // AVAILABLE, RESTORED, EMERGENCY, FAILED
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
