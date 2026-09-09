package com.sleeptalker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ClipEntity::class], version = 1, exportSchema = true)
abstract class SleepTalkerDatabase : RoomDatabase() {

    abstract fun clipDao(): ClipDao

    companion object {
        @Volatile private var instance: SleepTalkerDatabase? = null

        fun get(context: Context): SleepTalkerDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SleepTalkerDatabase::class.java,
                    "sleep_talker.db",
                ).build().also { instance = it }
            }
    }
}
