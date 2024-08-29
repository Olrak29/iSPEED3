package com.olrak.ispeed.dashboard.screens.automatic_track

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.olrak.ispeed.R
import com.olrak.ispeed.app.shared.data.TrackInternetModel
import com.olrak.ispeed.app.util.DiffUtilCallback
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackStatusAdapter(context: Context) :
    RecyclerView.Adapter<TrackStatusAdapter.ViewStatusHolder>() {

    private var dataList: MutableList<TrackInternetModel> = mutableListOf()
    private val context: Context

    init {
        this.dataList = dataList
        this.context = context
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewStatusHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.track_internet_list_item, parent, false)
        return ViewStatusHolder(view)
    }

    override fun onBindViewHolder(holder: ViewStatusHolder, position: Int) {
        val data: TrackInternetModel = dataList[position]

        holder.trackTime.text = convertDateToTime(data.trackDate)
        holder.ispName.text = if (data.ispName.isNullOrEmpty()) "Unknown" else data.ispName
        holder.date.text = convertDateToString(data.trackDate)

        if (data.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status) {
            holder.trackStatus.setTextColor(ContextCompat.getColor(context, R.color.lightGreen))
        } else {
            holder.trackStatus.setTextColor(ContextCompat.getColor(context, R.color.lightRed))
        }

        holder.trackStatus.text = data.trackStatus
    }

    override fun getItemCount() = dataList.size

    fun setData(data: List<TrackInternetModel>) {
        this.dataList = data.toMutableList()
    }

    // add new data
    fun setNewData(newData: List<TrackInternetModel>) {
        val diffCallback = DiffUtilCallback(dataList, newData)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        dataList.clear()
        dataList.addAll(newData)
        diffResult.dispatchUpdatesTo(this)
    }

    inner class ViewStatusHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var trackTime: TextView
        var trackStatus: TextView
        var ispName: TextView
        var date: TextView

        init {
            trackTime = itemView.findViewById(R.id.track_time)
            trackStatus = itemView.findViewById(R.id.track_status)
            ispName = itemView.findViewById(R.id.isp_name)
            date = itemView.findViewById(R.id.date)
        }
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