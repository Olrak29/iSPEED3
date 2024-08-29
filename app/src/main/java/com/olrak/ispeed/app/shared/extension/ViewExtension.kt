package com.olrak.ispeed.app.shared.extension

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.shashank.sony.fancytoastlib.FancyToast

fun displayLogs(message: String){
    Log.d("iSpeed", message)
}

fun View.setVisible(canShow: Boolean) {
    this.visibility = if (canShow) View.VISIBLE else View.GONE
}

fun View.setInVisibility(canShow: Boolean) {
    this.visibility = if (canShow) View.VISIBLE else View.INVISIBLE
}

fun View.setEnabled(canEnabled: Boolean) {
    if (canEnabled) this.alpha = 1.0F else this.alpha = 0.4F
    this.isEnabled  = canEnabled
}

fun Activity.showFancyToast(
    message: String,
    style: Int = FancyToast.SUCCESS,
    duration: Int = FancyToast.LENGTH_SHORT
) = FancyToast.makeText(this, message, duration, style,false).show()

fun Fragment.showFancyToast(
    message: String,
    style: Int = FancyToast.SUCCESS,
    duration: Int = FancyToast.LENGTH_SHORT
) = this.context?.let { FancyToast.makeText(it, message, duration, style,false).show() }

@Suppress("DEPRECATION")
fun <T> Context.isServiceRunning(service: Class<T>): Boolean {
    return (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(Integer.MAX_VALUE)
        .any { it.service.className == service.name }
}