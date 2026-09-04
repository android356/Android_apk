package com.autodeploy.infinityfree.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "github_connections",
    indices = [Index("project_id")]
)
data class GitHubConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "owner")
    val owner: String,
    @ColumnInfo(name = "repo")
    val repo: String,
    @ColumnInfo(name = "branch")
    val branch: String = "main",
    @ColumnInfo(name = "destination_path")
    val destinationPath: String = "/",
    @ColumnInfo(name = "encrypted_token_reference")
    val encryptedTokenReference: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
