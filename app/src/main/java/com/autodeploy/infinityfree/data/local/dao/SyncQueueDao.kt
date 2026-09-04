package com.autodeploy.infinityfree.data.local.dao

import androidx.room.*
import com.autodeploy.infinityfree.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE project_id = :projectId AND status IN ('PENDING', 'RETRYING') ORDER BY created_at ASC")
    suspend fun getPendingItems(projectId: Long): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE project_id = :projectId AND relative_path = :relativePath AND status IN ('PENDING', 'PREPARING', 'UPLOADING', 'RETRYING') LIMIT 1")
    suspend fun getActiveItemByPath(projectId: Long, relativePath: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE project_id = :projectId AND status = 'FAILED' ORDER BY last_attempt_at DESC")
    suspend fun getFailedItems(projectId: Long): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE project_id = :projectId AND status = 'FAILED' ORDER BY last_attempt_at DESC")
    fun observeFailedItems(projectId: Long): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE project_id = :projectId AND status = 'CONFLICT' ORDER BY created_at DESC")
    fun observeConflictedItems(projectId: Long): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE project_id = :projectId ORDER BY created_at DESC LIMIT 50")
    fun observeAllQueueItems(projectId: Long): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE project_id = :projectId AND status IN ('PENDING', 'PREPARING', 'UPLOADING', 'RETRYING')")
    fun observePendingCount(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE project_id = :projectId AND status = 'FAILED'")
    fun observeFailedCount(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE project_id = :projectId AND status = 'CONFLICT'")
    fun observeConflictCount(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'CONFLICT'")
    fun observeConflictCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SyncQueueEntity): Long

    @Update
    suspend fun updateItem(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = :status, error_message = :errorMessage, last_attempt_at = :attemptAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorMessage: String? = null, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'CONFLICT', conflict_details = :details, last_attempt_at = :attemptAt WHERE id = :id")
    suspend fun markConflict(id: Long, details: String, attemptAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'PENDING', retry_count = 0, error_message = null WHERE project_id = :projectId AND status = 'FAILED'")
    suspend fun retryAllFailed(projectId: Long): Int

    @Query("UPDATE sync_queue SET status = 'PENDING', retry_count = 0, error_message = null WHERE id = :id")
    suspend fun retryItem(id: Long)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM sync_queue WHERE project_id = :projectId AND status = 'SUCCESS'")
    suspend fun clearCompleted(projectId: Long)

    @Query("DELETE FROM sync_queue WHERE project_id = :projectId")
    suspend fun clearAllForProject(projectId: Long)
}
