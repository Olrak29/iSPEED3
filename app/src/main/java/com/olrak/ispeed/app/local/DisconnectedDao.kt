package com.olrak.ispeed.app.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import com.olrak.ispeed.app.shared.data.DisconnectedModel
import com.olrak.ispeed.app.util.LocalConverter

@Dao
interface DisconnectedDao {
    @Query("SELECT * FROM disconnected")
    @TypeConverters(LocalConverter::class)
    fun disconnectedCount() : List<DisconnectedModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(disconnectedModel: DisconnectedModel)

    @Query("UPDATE disconnected SET disconnectedCount= :count WHERE date =:date")
    fun updateDisconnectCount(count: String?, date: String?): Int

    @Query("SELECT disconnectedCount FROM disconnected WHERE date =:date")
    fun getSpecificCount(date: String?): String?

    @Query("SELECT * FROM disconnected WHERE date = :date")
    fun isDataExist(date: String?): Int
}