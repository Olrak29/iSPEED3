package com.olrak.ispeed.app.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import com.olrak.ispeed.app.shared.data.TrackInternetModel
import com.olrak.ispeed.app.util.LocalConverter

@Dao
interface TrackInternetDao {
    @Query("SELECT * FROM trackInternet ORDER BY trackDate desc LIMIT :count")
    @TypeConverters(LocalConverter::class)
    suspend fun getTrackInternetData(count: Int) : MutableList<TrackInternetModel>

    @Query("SELECT * FROM trackInternet ORDER BY trackDate")
    @TypeConverters(LocalConverter::class)
    suspend fun getAllTrackList() : MutableList<TrackInternetModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(trackInternetModel: TrackInternetModel)

    @Query("SELECT COUNT(id) FROM trackInternet")
    suspend fun getRowCount(): Int
}