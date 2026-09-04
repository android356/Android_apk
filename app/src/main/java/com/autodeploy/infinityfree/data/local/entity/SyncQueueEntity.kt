package com.autodeploy.infinityfree.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [
        Index("project_id"),
        Index("status"),
        Index("relative_path")
    ]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "operation")
    val operation: String = "UPLOAD", // UPLOAD, CREATE_DIRECTORY, DELETE_FILE, DELETE_DIRECTORY, RETRY, ROLLBACK
    @ColumnInfo(name = "status")
    val status: String = "PENDING", // PENDING, PREPARING, UPLOADING, SUCCESS, FAILED, RETRYING, CANCELLED, CONFLICT
    @ColumnInfo(name = "target")
    val target: String = "ALL", // ALL, GITHUB, INFINITYFREE
    @ColumnInfo(name = "github_status")
    val githubStatus: String = "PENDING", // PENDING, SUCCESS, FAILED, SKIPPED, CONFLICT
    @ColumnInfo(name = "infinityfree_status")
    val infinityFreeStatus: String = "PENDING", // PENDING, SUCCESS, FAILED, SKIPPED
    @ColumnInfo(name = "github_sha")
    val githubSha: String? = null,
    @ColumnInfo(name = "conflict_details")
    val conflictDetails: String? = null,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)
