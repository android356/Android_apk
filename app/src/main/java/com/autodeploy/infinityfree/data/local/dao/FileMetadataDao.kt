package com.autodeploy.infinityfree.data.local.dao

import androidx.room.*
import com.autodeploy.infinityfree.data.local.entity.FileMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileMetadataDao {
    @Query("SELECT * FROM file_metadata WHERE project_id = :projectId AND is_present = 1")
    suspend fun getAllExistingFiles(projectId: Long): List<FileMetadataEntity>

    @Query("SELECT * FROM file_metadata WHERE project_id = :projectId AND relative_path = :relativePath LIMIT 1")
    suspend fun getByPath(projectId: Long, relativePath: String): FileMetadataEntity?

    @Query("SELECT * FROM file_metadata WHERE project_id = :projectId")
    suspend fun getAllForProject(projectId: Long): List<FileMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(fileMetadata: FileMetadataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(files: List<FileMetadataEntity>)

    @Query("UPDATE file_metadata SET is_present = 0 WHERE project_id = :projectId")
    suspend fun markAllNonExistent(projectId: Long)

    @Query("DELETE FROM file_metadata WHERE project_id = :projectId AND is_present = 0")
    suspend fun deleteNonExistent(projectId: Long)

    @Query("SELECT COUNT(*) FROM file_metadata WHERE project_id = :projectId AND item_type = 'FILE' AND is_present = 1")
    fun observeFileCount(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM file_metadata WHERE project_id = :projectId AND item_type = 'DIRECTORY' AND is_present = 1")
    fun observeFolderCount(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM file_metadata WHERE project_id = :projectId AND item_type = 'FILE' AND is_present = 1")
    suspend fun getFileCount(projectId: Long): Int

    @Query("SELECT COUNT(*) FROM file_metadata WHERE project_id = :projectId AND item_type = 'DIRECTORY' AND is_present = 1")
    suspend fun getFolderCount(projectId: Long): Int

    @Query("UPDATE file_metadata SET sync_status = :status, last_synced_at = :syncedAt WHERE project_id = :projectId AND relative_path = :path")
    suspend fun updateSyncStatus(projectId: Long, path: String, status: String, syncedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM file_metadata WHERE project_id = :projectId")
    suspend fun clearForProject(projectId: Long)
}
