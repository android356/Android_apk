package com.autodeploy.infinityfree.data.local.dao

import androidx.room.*
import com.autodeploy.infinityfree.data.local.entity.SyncHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncHistoryDao {
    @Query("SELECT * FROM sync_history WHERE project_id = :projectId ORDER BY started_at DESC LIMIT :limit")
    fun observeHistory(projectId: Long, limit: Int = 100): Flow<List<SyncHistoryEntity>>

    @Query("SELECT * FROM sync_history WHERE project_id = :projectId AND result = :filterResult ORDER BY started_at DESC LIMIT :limit")
    fun observeHistoryByResult(projectId: Long, filterResult: String, limit: Int = 100): Flow<List<SyncHistoryEntity>>

    @Query("SELECT * FROM sync_history WHERE project_id = :projectId AND operation = :operation ORDER BY started_at DESC LIMIT :limit")
    fun observeHistoryByOperation(projectId: Long, operation: String, limit: Int = 100): Flow<List<SyncHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SyncHistoryEntity): Long

    @Query("DELETE FROM sync_history WHERE project_id = :projectId")
    suspend fun clearHistory(projectId: Long)
}
