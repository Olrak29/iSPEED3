package com.olrak.ispeed.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentSender.SendIntentException
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.listener.single.PermissionListener
import com.shashank.sony.fancytoastlib.FancyToast
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ViewUtil @Inject constructor() {

    fun getDateTime() = Date()

    @SuppressLint("SimpleDateFormat")
    fun getCurrentDate() : String {
        val sdf = SimpleDateFormat("MM/dd/yyyy")
        return sdf.format(Date())
    }

    @SuppressLint("SimpleDateFormat")
    fun getWeeklyDate(): Array<String?> {
        val format: DateFormat = SimpleDateFormat("MM/dd/yyyy")
        val calendar: Calendar = Calendar.getInstance()
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val days = arrayOfNulls<String>(7)
        for (i in 0..6) {
            days[i] = format.format(calendar.time)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return days
    }

    fun parseDateToString(trackDate: String?): String {
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
            @SuppressLint("SimpleDateFormat") val timeFormatter = SimpleDateFormat("MM/dd/yyyy")
            timeFormatter.format(date)
        } catch (e: Exception) {
            SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).format(Date())
        }
    }

    fun gpsChecker(activity: Activity, googleApiClient: GoogleApiClient?, requestLocationCode: Int) {
        val manager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) && hasGPSDevice(activity)) {
            enableLoc(activity, googleApiClient, requestLocationCode)
        }
    }

    private fun enableLoc(
        activity: Context,
        googleApiClient: GoogleApiClient?,
        requestLocationCode: Int
    ) {
        var googleApiClient: GoogleApiClient? = googleApiClient

        if (googleApiClient == null) {
            val builder = GoogleApiClient.Builder(activity)
            builder.addApi(LocationServices.API)
            val finalGoogleApiClient = googleApiClient
            builder.addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                override fun onConnected(bundle: Bundle?) {}
                override fun onConnectionSuspended(i: Int) {
                    finalGoogleApiClient?.connect()
                }
            })
            builder.addOnConnectionFailedListener { connectionResult ->
                Log.d(
                    "Location error",
                    "Location error " + connectionResult.errorCode
                )
            }
            googleApiClient = builder.build()
            googleApiClient.connect()
        }

        val locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = (30 * 1000).toLong()
        locationRequest.fastestInterval = (5 * 1000).toLong()

        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)

        val result = LocationServices.SettingsApi.checkLocationSettings(googleApiClient, builder.build())
        result.setResultCallback { result ->
            val status = result.status
            when (status.statusCode) {
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    status.startResolutionForResult(
                        (activity as Activity),
                        requestLocationCode
                    )
                } catch (e: SendIntentException) {
                    // Ignore the error.
                }
            }
        }
    }

    private fun hasGPSDevice(activity: Activity): Boolean {
        val mgr = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = mgr.allProviders
        return providers.contains(LocationManager.GPS_PROVIDER)
    }

    fun haveNetworkConnected(activity: Context): Boolean {
        var haveWifi = false
        var haveMobileData = false
        val connectivityManager =
            activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @SuppressLint("MissingPermission") val networkInfos = connectivityManager.allNetworkInfo
        for (info in networkInfos) {
            if (info.typeName.equals("WIFI", ignoreCase = true)) if (info.isConnected) haveWifi = true
            if (info.typeName.equals("MOBILE", ignoreCase = true)) if (info.isConnected) haveMobileData = true
        }
        return haveMobileData || haveWifi
    }

    fun locationPermission(activity: Context?) {
        Dexter.withContext(activity)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse?) {}
                override fun onPermissionDenied(response: PermissionDeniedResponse?) {}
                override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken) = token.continuePermissionRequest()
            }).check()
    }

    fun storagePermission(activity: Context, permissions: MutableList<String>) {
        Dexter.withContext(activity)
        .withPermissions(permissions)
        .withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(p0: MultiplePermissionsReport?) {
                FancyToast.makeText(activity, "Storage Permission needs to access, before exporting a tracking list.", FancyToast.LENGTH_LONG, FancyToast.SUCCESS,false).show()
            }

            override fun onPermissionRationaleShouldBeShown(
                p0: MutableList<PermissionRequest>?,
                p1: PermissionToken?
            ) {
            }
        })
    }
}