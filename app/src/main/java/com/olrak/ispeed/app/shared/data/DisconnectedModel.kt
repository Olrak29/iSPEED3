package com.olrak.ispeed.app.shared.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "disconnected", indices = [Index(value = ["date"], unique = true)])
class DisconnectedModel : Serializable {
    @PrimaryKey(autoGenerate = true)
    var id = 0

    @ColumnInfo(name = "disconnectedCount")
    var disconnectedCount: String? = null

    @ColumnInfo(name = "date")
    var date: String? = null
}