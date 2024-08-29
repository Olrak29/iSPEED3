package com.olrak.ispeed.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.olrak.ispeed.R
import com.olrak.ispeed.app.shared.data.TrackInternetModel
import com.olrak.ispeed.app.shared.local.DatabaseUseCase
import com.shashank.sony.fancytoastlib.FancyToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class NetworkTrackerService : BroadcastReceiver() {

    private var context: Context? = null

    @Inject
    lateinit var databaseUseCase: DatabaseUseCase

    private val scope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @RequiresApi(api = Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        try {
            //Init Components
            setupComponents(context)
            checkInternetAvailability(context)
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkInternetAvailability(context: Context) {
        if (isOnline(context).not()) {
            val date = Date()
            val dateFormatWithZone = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            val currentDate = dateFormatWithZone.format(date)

            saveTrackDisconnect(TrackInternetModel(
                trackDate = currentDate,
                trackStatus = TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
            ))

            FancyToast.makeText(
                context,
                "You have been disconnected to the internet.",
                FancyToast.LENGTH_SHORT,
                FancyToast.INFO,
                false).show()
        }
    }

    private fun setupComponents(context: Context) {
        this.context = context
    }

    private fun saveTrackDisconnect(trackInternetModel: TrackInternetModel) {
        try {
            scope.launch {
                //adding to database
                databaseUseCase.saveInternetStatus(trackInternetModel = trackInternetModel)
                EventBus.getDefault().post("trigger")
            }
        } catch (e: Exception) {
            Log.d("GetError: ", e.message.toString())
        }
    }

    private fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val netInfo = cm.activeNetworkInfo
            //should check null because in airplane mode it will be null
            netInfo != null && netInfo.isConnected
        } catch (e: NullPointerException) {
            e.printStackTrace()
            false
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createNotification(context: Context, text: String) {
        val notificationManager = NotificationManagerCompat.from(context)
        val channel =
            NotificationChannel("CHANNEL_ID", "name", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "description"
        notificationManager.createNotificationChannel(channel)
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(context, "CHANNEL_ID")
            .setSmallIcon(R.drawable.ispeedlogo)
            .setPriority(1)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle(text)

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationManager.notify(1, builder.build())
    }
}