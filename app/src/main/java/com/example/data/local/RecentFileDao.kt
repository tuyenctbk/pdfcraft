package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY lastAccessedTimestamp DESC LIMIT 10")
    fun getRecentFiles(): Flow<List<RecentFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(recentFile: RecentFileEntity)

    @Query("UPDATE recent_files SET filePath = :newPath, fileName = :newFileName WHERE filePath = :oldPath")
    suspend fun updateRecentFileByPath(oldPath: String, newPath: String, newFileName: String)

    @Query("DELETE FROM recent_files WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)

    @Query("DELETE FROM recent_files WHERE filePath IN (:filePaths)")
    suspend fun deleteByPaths(filePaths: List<String>)

    @Query("SELECT * FROM recent_files WHERE fileName LIKE '%' || :query || '%' OR filePath LIKE '%' || :query || '%' ORDER BY lastAccessedTimestamp DESC")
    fun searchRecentFiles(query: String): Flow<List<RecentFileEntity>>

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()
}
