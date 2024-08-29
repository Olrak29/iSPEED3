package com.olrak.ispeed.app.shared.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "internetDataModel")
class FirebaseInternetDataModel : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id = 0

    @ColumnInfo(name = "isp")
    var isp: String? = null

    @ColumnInfo(name = "location")
    var location: String? = null

    @ColumnInfo(name = "latitude")
    var latitude: Double? = null

    @ColumnInfo(name = "longitude")
    var longitude: Double? = null

    @ColumnInfo(name = "user_id")
    var user_id: String? = null

    @ColumnInfo(name = "uploadSpeed")
    var uploadSpeed: String? = null

    @ColumnInfo(name = "downLoadSpeed")
    var downLoadSpeed: String? = null

    @ColumnInfo(name = "ping")
    var ping: String? = null

    @ColumnInfo(name = "time")
    var time: String? = null

    @ColumnInfo(name = "userName")
    var userName: String? = null
}