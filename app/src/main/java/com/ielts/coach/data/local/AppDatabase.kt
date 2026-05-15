package com.ielts.coach.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ielts.coach.data.local.converter.Converters
import com.ielts.coach.data.local.dao.SessionDao
import com.ielts.coach.data.local.entity.SessionEntity

@Database(entities = [SessionEntity::class], version = 1, exportSchema = false)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ielts_coach_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
