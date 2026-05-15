package com.ielts.coach.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ielts.coach.data.local.entity.SessionEntity

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY timestamp DESC")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE sessionId = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE sessionId = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun count(): Int
}
