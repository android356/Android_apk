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
        Index("result")
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
    @ColumnInfo(name = "started_at")
    val startedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at")
    val completedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "result")
    val result: String, // SUCCESS, FAILED, SKIPPED, CONFLICT
    @ColumnInfo(name = "github_result")
    val githubResult: String? = null,
    @ColumnInfo(name = "infinityfree_result")
    val infinityFreeResult: String? = null,
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)
