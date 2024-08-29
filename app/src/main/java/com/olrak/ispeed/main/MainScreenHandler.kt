package com.olrak.ispeed.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.olrak.ispeed.R
import com.olrak.ispeed.app.foundation.BaseActivity
import com.olrak.ispeed.app.service.NetworkTrackerService
import com.olrak.ispeed.app.shared.widget.DialogFactory
import com.olrak.ispeed.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainScreenHandler : BaseActivity<ActivityMainBinding>(bindingInflater = ActivityMainBinding::inflate) {

    private var mNetworkReceiver: BroadcastReceiver? = null

    override fun onActivityCreated() {
        super.onActivityCreated()
        mNetworkReceiver = NetworkTrackerService()
        registerNetworkBroadcast()
        checkNotificationPermission()
    }

    private fun registerNetworkBroadcast() {
        registerReceiver(
            mNetworkReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )
    }

    private fun unregisterNetworkChanges() {
        try {
            unregisterReceiver(mNetworkReceiver)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }

    private fun checkNotificationPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    /* context = */ this,
                    /* permission = */ Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            true
        } else false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted.not()) showPermissionDialog()
    }

    private fun showPermissionDialog() {
        DialogFactory.showCustomDialog(
            context = this,
            dialogAttributes = DialogFactory.DialogAttributes(
                title = getString(R.string.dialog_permission_required_title),
                subTitle = getString(R.string.dialog_subtitle),
                primaryButtonTitle = getString(R.string.action_cancel),
                secondaryButtonTitle = getString(R.string.action_settings)
            ), secondaryButtonClicked = {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkChanges()
    }
}