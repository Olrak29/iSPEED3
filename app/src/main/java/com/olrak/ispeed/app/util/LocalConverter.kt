package com.olrak.ispeed.app.util

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.olrak.ispeed.app.shared.data.DisconnectedModel
import com.olrak.ispeed.app.shared.data.InternetDataModel
import com.olrak.ispeed.app.shared.data.TrackInternetModel
import java.lang.reflect.Type

object LocalConverter {
    @TypeConverter
    fun fromData(value: String?): List<DisconnectedModel> {
        val listType: Type = object : TypeToken<List<DisconnectedModel?>?>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromDataList(list: List<DisconnectedModel?>?): String = Gson().toJson(list)

    @TypeConverter
    fun fromDataTrack(value: String?): List<TrackInternetModel> {
        val listType: Type = object : TypeToken<List<TrackInternetModel?>?>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromDataTrackList(list: List<TrackInternetModel?>?): String = Gson().toJson(list)

    @TypeConverter
    fun fromDataInternet(value: String?): List<InternetDataModel> {
        val listType: Type = object : TypeToken<List<InternetDataModel?>?>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromDataInternetList(list: List<InternetDataModel?>?): String = Gson().toJson(list)
}