package com.autodeploy.infinityfree.data.local.dao

import androidx.room.*
import com.autodeploy.infinityfree.data.local.entity.TemporaryBackupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemporaryBackupDao {
    @Query("SELECT * FROM temporary_backups WHERE project_id = :projectId AND status = 'AVAILABLE' ORDER BY created_at DESC")
    fun observeAvailableBackups(projectId: Long): Flow<List<TemporaryBackupEntity>>

    @Query("SELECT * FROM temporary_backups WHERE project_id = :projectId AND status = 'AVAILABLE' ORDER BY created_at DESC")
    suspend fun getAvailableBackups(projectId: Long): List<TemporaryBackupEntity>

    @Query("SELECT COUNT(*) FROM temporary_backups WHERE project_id = :projectId AND status = 'AVAILABLE'")
    fun observeActiveBackupCount(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM temporary_backups WHERE status = 'AVAILABLE'")
    fun observeActiveBackupCount(): Flow<Int>

    @Query("SELECT * FROM temporary_backups WHERE id = :id LIMIT 1")
    suspend fun getBackupById(id: Long): TemporaryBackupEntity?

    @Query("SELECT * FROM temporary_backups WHERE expires_at <= :currentTime AND status = 'AVAILABLE'")
    suspend fun getExpiredBackups(currentTime: Long): List<TemporaryBackupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackup(backup: TemporaryBackupEntity): Long

    @Update
    suspend fun updateBackup(backup: TemporaryBackupEntity)

    @Query("UPDATE temporary_backups SET status = 'EXPIRED' WHERE id = :id")
    suspend fun markExpired(id: Long)

    @Query("DELETE FROM temporary_backups WHERE id = :id")
    suspend fun deleteBackup(id: Long)

    @Query("DELETE FROM temporary_backups WHERE project_id = :projectId")
    suspend fun clearForProject(projectId: Long)
}
