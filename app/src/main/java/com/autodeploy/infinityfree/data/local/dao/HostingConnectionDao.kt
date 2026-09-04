package com.autodeploy.infinityfree.data.local.dao

import androidx.room.*
import com.autodeploy.infinityfree.data.local.entity.HostingConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HostingConnectionDao {
    @Query("SELECT * FROM hosting_connections WHERE project_id = :projectId LIMIT 1")
    suspend fun getConnectionForProject(projectId: Long): HostingConnectionEntity?

    @Query("SELECT * FROM hosting_connections WHERE project_id = :projectId LIMIT 1")
    fun observeConnectionForProject(projectId: Long): Flow<HostingConnectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: HostingConnectionEntity): Long

    @Update
    suspend fun updateConnection(connection: HostingConnectionEntity)

    @Query("DELETE FROM hosting_connections WHERE project_id = :projectId")
    suspend fun deleteConnectionForProject(projectId: Long)
}
