package com.olrak.ispeed.app.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.TypeConverters
import com.olrak.ispeed.app.shared.data.FirebaseInternetDataModel
import com.olrak.ispeed.app.util.LocalConverter

@Dao
interface FirebaseInternetDao {
    @Query("SELECT * FROM internetDataModel")
    @TypeConverters(LocalConverter::class)
    fun fBInternetCount() : List<FirebaseInternetDataModel>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(fbInternetModel: FirebaseInternetDataModel)
}