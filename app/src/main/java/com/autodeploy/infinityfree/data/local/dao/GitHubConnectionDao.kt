package com.autodeploy.infinityfree.data.local.dao

import androidx.room.*
import com.autodeploy.infinityfree.data.local.entity.GitHubConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GitHubConnectionDao {
    @Query("SELECT * FROM github_connections WHERE project_id = :projectId LIMIT 1")
    suspend fun getConnectionForProject(projectId: Long): GitHubConnectionEntity?

    @Query("SELECT * FROM github_connections WHERE project_id = :projectId LIMIT 1")
    fun observeConnectionForProject(projectId: Long): Flow<GitHubConnectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: GitHubConnectionEntity): Long

    @Update
    suspend fun updateConnection(connection: GitHubConnectionEntity)

    @Query("DELETE FROM github_connections WHERE project_id = :projectId")
    suspend fun deleteConnectionForProject(projectId: Long)
}
