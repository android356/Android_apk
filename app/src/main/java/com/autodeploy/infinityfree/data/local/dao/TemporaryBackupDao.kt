package com.autodeploy.infinityfree.data.local.dao

import androidx.room.*
import com.autodeploy.infinityfree.data.local.entity.TemporaryBackupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemporaryBackupDao {
    @Query("SELECT * FROM temporary_backups WHERE project_id = :projectId AND status = 'AVAILABLE' AND expires_at > :currentTime ORDER BY created_at DESC")
    fun observeAvailableBackups(projectId: Long, currentTime: Long = System.currentTimeMillis()): Flow<List<TemporaryBackupEntity>>

    @Query("SELECT * FROM temporary_backups WHERE project_id = :projectId AND status = 'AVAILABLE' AND expires_at > :currentTime ORDER BY created_at DESC")
    suspend fun getAvailableBackups(projectId: Long, currentTime: Long = System.currentTimeMillis()): List<TemporaryBackupEntity>

    @Query("SELECT COUNT(*) FROM temporary_backups WHERE project_id = :projectId AND status = 'AVAILABLE' AND expires_at > :currentTime")
    fun observeActiveBackupCount(projectId: Long, currentTime: Long = System.currentTimeMillis()): Flow<Int>

    @Query("SELECT * FROM temporary_backups WHERE id = :id LIMIT 1")
    suspend fun getBackupById(id: Long): TemporaryBackupEntity?

    @Query("SELECT * FROM temporary_backups WHERE expires_at <= :currentTime AND status = 'AVAILABLE'")
    suspend fun getExpiredBackups(currentTime: Long = System.currentTimeMillis()): List<TemporaryBackupEntity>

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
