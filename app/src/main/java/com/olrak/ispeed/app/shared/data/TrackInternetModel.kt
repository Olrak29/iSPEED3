package com.olrak.ispeed.app.shared.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "trackInternet", indices = [Index(value = ["trackDate"], unique = true)])
class TrackInternetModel(

    @field:ColumnInfo(name = "trackStatus")
    var trackStatus: String? = null,

    @field:ColumnInfo(name = "trackDate")
    var trackDate: String? = null,

    @field:ColumnInfo(name = "ispName")
    var ispName: String? = null,

    @field:ColumnInfo(name = "location")
    var location: String? = null
) :
    Serializable {
    @PrimaryKey(autoGenerate = true)
    var id = 0

    enum class TrackStatusEnum(val status: String) {
        CONNECTED("STABLE"), DISCONNECTED("UNSTABLE")
    }
}