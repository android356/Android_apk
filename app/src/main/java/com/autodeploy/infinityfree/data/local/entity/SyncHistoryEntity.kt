package com.autodeploy.infinityfree.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_history",
    indices = [
        Index("project_id"),
        Index("started_at"),
        Index("result"),
        Index("target_provider")
    ]
)
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "operation")
    val operation: String,
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "target_provider", defaultValue = "INFINITY_FREE")
    val targetProvider: String = "INFINITY_FREE",
    @ColumnInfo(name = "started_at")
    val startedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at")
    val completedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "duration_ms", defaultValue = "0")
    val durationMs: Long = 0L,
    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,
    @ColumnInfo(name = "result")
    val result: String, // SUCCESS, FAILED, SKIPPED, CONFLICT, ROLLED_BACK
    @ColumnInfo(name = "verified", defaultValue = "0")
    val verified: Boolean = false,
    @ColumnInfo(name = "github_result")
    val githubResult: String? = null,
    @ColumnInfo(name = "infinityfree_result")
    val infinityFreeResult: String? = null,
    @ColumnInfo(name = "error_category")
    val errorCategory: String? = null,
    @ColumnInfo(name = "rollback_info")
    val rollbackInfo: String? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)
