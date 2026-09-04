package com.autodeploy.infinityfree.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "hosting_connections",
    indices = [Index("project_id")]
)
data class HostingConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "connection_name")
    val connectionName: String = "InfinityFree Hosting",
    @ColumnInfo(name = "server")
    val server: String,
    @ColumnInfo(name = "port")
    val port: Int = 21,
    @ColumnInfo(name = "username")
    val username: String,
    @ColumnInfo(name = "encrypted_password_reference")
    val encryptedPasswordReference: String,
    @ColumnInfo(name = "remote_root_directory")
    val remoteRootDirectory: String = "/htdocs/",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
