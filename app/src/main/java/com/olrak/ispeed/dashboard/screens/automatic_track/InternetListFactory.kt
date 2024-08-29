package com.olrak.ispeed.dashboard.screens.automatic_track

import android.annotation.SuppressLint
import com.olrak.ispeed.app.shared.binder.TrackListItem
import com.olrak.ispeed.app.shared.data.TrackInternetModel
import com.olrak.ispeed.app.shared.dto.ListItemViewDto
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class InternetListFactory @Inject constructor() {

    fun createOverview(data: MutableList<TrackInternetModel>?) = data.prepareList()

    private fun MutableList<TrackInternetModel>?.prepareList() = this@prepareList?.map {
        TrackListItem(
            id = it.id,
            dto = ListItemViewDto(
                itemId = it.id,
                firstLine = convertDateToTime(it.trackDate),
                secondLine = convertDateToString(it.trackDate),
                thirdLine = if (it.ispName.isNullOrEmpty()) "Unknown" else it.ispName,
                fourthLine = extractLocation(location = it.location),
                fifthLine = it.trackStatus,
                isStable = it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
            )
        )
    } ?: emptyList()

    private fun extractLocation(location: String?) =
        if (location?.contains(",") == true) {
            location.split(",")[0]
        } else {
            location ?: "Unknown"
        }

    private fun convertDateToTime(trackDate: String?): String {
        return try {
            // Get date from string
            @SuppressLint("SimpleDateFormat") val dateFormatter =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm")
            var date: Date? = null
            try {
                date = trackDate?.let { dateFormatter.parse(it) }
            } catch (e: ParseException) {
                e.printStackTrace()
            }

            // Get time from date
            @SuppressLint("SimpleDateFormat") val timeFormatter = SimpleDateFormat("h:mm a")
            timeFormatter.format(date)
        } catch (e: Exception) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        }
    }

    private fun convertDateToString(trackDate: String?): String {
        return try {
            @SuppressLint("SimpleDateFormat") val dateFormatter =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm")
            var date: Date? = null
            try {
                date = trackDate?.let { dateFormatter.parse(it) }
            } catch (e: ParseException) {
                e.printStackTrace()
            }

            @SuppressLint("SimpleDateFormat") val timeFormatter = SimpleDateFormat("MM/dd/yy")
            timeFormatter.format(date)
        } catch (e: Exception) {
            SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(Date())
        }
    }
}