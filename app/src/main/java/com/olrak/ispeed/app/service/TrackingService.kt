package com.olrak.ispeed.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.credentials.playservices.CredentialProviderMetadataHolder.*
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.olrak.ispeed.R
import com.olrak.ispeed.app.shared.data.TrackInternetModel
import com.olrak.ispeed.app.shared.local.DatabaseUseCase
import com.olrak.ispeed.app.util.Default
import com.olrak.ispeed.app.util.SpeedTestHandler
import com.olrak.ispeed.app.util.ViewUtil
import com.shashank.sony.fancytoastlib.FancyToast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import java.text.DecimalFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject
    lateinit var viewUtil: ViewUtil

    @Inject
    lateinit var speedTestHandler: SpeedTestHandler

    @Inject
    lateinit var databaseUseCase: DatabaseUseCase

    private var currentLocation: String? = null
    private var latitude: Double? = null
    private var longitude: Double? = null

    val dec = DecimalFormat("#.##")

    private var freq1Min = 60000L
    private var timer = Timer()
    private var timerKiller = Timer()

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent) = throw UnsupportedOperationException("Not yet implemented")

    override fun onCreate() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) createNotificationChannel(notificationManager) else ""

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification: Notification = notificationBuilder.setOngoing(true)
            .setSmallIcon(R.drawable.ispeedlogo)
            .setPriority(1)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle("Tracking Internet...")
            .build()
        startForeground(1, notification)
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.hasExtra("loc")) {
            currentLocation = intent.getStringExtra("loc")
        }

        if (intent.hasExtra("lat")) {
            latitude = intent.getDoubleExtra("lat", 0.0)
        }

        if (intent.hasExtra("longi")) {
            longitude = intent.getDoubleExtra("longi", 0.0)
        }

        startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        //Create a timer
        try {
            FancyToast.makeText(this, "Tracking on background...", FancyToast.LENGTH_SHORT, FancyToast.SUCCESS,false).show()

            speedTestHandler = SpeedTestHandler()
            speedTestHandler.start()

            timer.schedule(object : TimerTask() {
                override fun run() = trackInternet()
            }, viewUtil.getDateTime(), freq1Min)
        } catch (e: Exception) {
            Log.d(TAG, "Exception: " + e.message)
            onDestroy()
        } catch (e: ParseException) {
            Log.d(TAG, "Exception: " + e.message)
            onDestroy()
        }
    }

    private fun cancelTimer() {
        Log.d(TAG, "Killing both timer")

        //Timer
        timer.cancel()
        isTrackingStarted = false
        isTimerStopped = true

        //Killer timer
        timerKiller.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimer()
    }

    fun trackInternet() {
        Log.d(TAG, "Start tracking....")
        isTrackingStarted = true
        isTimerStopped = false

        val dateFormatWithZone = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val currentDate = dateFormatWithZone.format(Date())

        scope.launch {
            databaseUseCase.saveInternetStatus(TrackInternetModel(
                trackStatus = if (viewUtil.haveNetworkConnected(this@TrackingService)) TrackInternetModel.TrackStatusEnum.CONNECTED.status
                else TrackInternetModel.TrackStatusEnum.DISCONNECTED.status,
                trackDate = currentDate,
                ispName = if (speedTestHandler.ispName != "" && speedTestHandler.ispName != " ") speedTestHandler.ispName else Default.ispName ,
                location = currentLocation
            ))
            EventBus.getDefault().post("trigger")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(notificationManager: NotificationManager): String {
        val channelId = "my_service_channelid"
        val channelName = "My Foreground Service"
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        // omitted the LED color
        channel.importance = NotificationManager.IMPORTANCE_NONE
        channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        notificationManager.createNotificationChannel(channel)
        return channelId
    }

    companion object {
        private val TAG = "MyService"
        var isTrackingStarted = false
        var isTimerStopped = true
    }
}