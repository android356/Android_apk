package com.autodeploy.infinityfree.data.local.dao

import androidx.room.*
import com.autodeploy.infinityfree.data.local.entity.ShrotiHostConnectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShrotiHostConnectionDao {
    @Query("SELECT * FROM shrotihost_connections WHERE project_id = :projectId LIMIT 1")
    suspend fun getConnectionForProject(projectId: Long): ShrotiHostConnectionEntity?

    @Query("SELECT * FROM shrotihost_connections WHERE project_id = :projectId LIMIT 1")
    fun observeConnectionForProject(projectId: Long): Flow<ShrotiHostConnectionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnection(connection: ShrotiHostConnectionEntity): Long

    @Update
    suspend fun updateConnection(connection: ShrotiHostConnectionEntity)

    @Query("DELETE FROM shrotihost_connections WHERE project_id = :projectId")
    suspend fun deleteConnectionForProject(projectId: Long)
}
