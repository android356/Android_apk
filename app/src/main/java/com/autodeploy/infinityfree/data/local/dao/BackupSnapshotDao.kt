package com.autodeploy.infinityfree.data.local.dao

import androidx.room.*
import com.autodeploy.infinityfree.data.local.entity.BackupSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupSnapshotDao {

    @Query("SELECT * FROM backup_snapshots WHERE project_id = :projectId ORDER BY version_code DESC")
    fun observeSnapshots(projectId: Long): Flow<List<BackupSnapshotEntity>>

    @Query("SELECT * FROM backup_snapshots WHERE project_id = :projectId ORDER BY version_code DESC")
    suspend fun getSnapshots(projectId: Long): List<BackupSnapshotEntity>

    @Query("SELECT * FROM backup_snapshots WHERE id = :id LIMIT 1")
    suspend fun getSnapshotById(id: Long): BackupSnapshotEntity?

    @Query("SELECT * FROM backup_snapshots WHERE project_id = :projectId AND is_stable = 1 ORDER BY version_code DESC LIMIT 1")
    suspend fun getLatestStableSnapshot(projectId: Long): BackupSnapshotEntity?

    @Query("SELECT * FROM backup_snapshots WHERE project_id = :projectId ORDER BY version_code DESC LIMIT 1")
    suspend fun getLatestSnapshot(projectId: Long): BackupSnapshotEntity?

    @Query("SELECT COALESCE(MAX(version_code), 0) + 1 FROM backup_snapshots WHERE project_id = :projectId")
    suspend fun getNextVersionCode(projectId: Long): Int

    @Query("SELECT COUNT(*) FROM backup_snapshots WHERE project_id = :projectId")
    fun observeSnapshotCount(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM backup_snapshots WHERE project_id = :projectId AND is_stable = 1")
    fun observeStableCount(projectId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: BackupSnapshotEntity): Long

    @Update
    suspend fun updateSnapshot(snapshot: BackupSnapshotEntity)

    @Query("UPDATE backup_snapshots SET is_stable = :isStable WHERE id = :id")
    suspend fun setStable(id: Long, isStable: Boolean)

    @Query("UPDATE backup_snapshots SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM backup_snapshots WHERE id = :id")
    suspend fun deleteSnapshot(id: Long)

    @Query("SELECT * FROM backup_snapshots WHERE project_id = :projectId AND is_stable = 0 ORDER BY version_code ASC")
    suspend fun getUnstableSnapshotsAscending(projectId: Long): List<BackupSnapshotEntity>

    @Query("DELETE FROM backup_snapshots WHERE project_id = :projectId")
    suspend fun clearForProject(projectId: Long)
}
