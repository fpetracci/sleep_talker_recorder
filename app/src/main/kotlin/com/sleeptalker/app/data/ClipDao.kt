package com.sleeptalker.app.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ClipDao {

    @Query("SELECT * FROM clips ORDER BY recordedAtMillis DESC")
    fun observeAll(): LiveData<List<ClipEntity>>

    @Insert
    fun insert(clip: ClipEntity): Long
}
