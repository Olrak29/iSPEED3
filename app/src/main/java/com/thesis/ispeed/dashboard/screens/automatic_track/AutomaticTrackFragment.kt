package com.thesis.ispeed.dashboard.screens.automatic_track

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.Intent.CATEGORY_DEFAULT
import android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.Log
import android.util.LruCache
import android.view.View
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.checkSelfPermission
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.thesis.ispeed.R
import com.thesis.ispeed.app.component.CustomRecyclerView
import com.thesis.ispeed.app.foundation.BaseFragment
import com.thesis.ispeed.app.service.TrackingService
import com.thesis.ispeed.app.shared.binder.TrackListItem
import com.thesis.ispeed.app.shared.binder.setupTrackListItemBinder
import com.thesis.ispeed.app.shared.data.TrackInternetModel
import com.thesis.ispeed.app.shared.data.UserDetails
import com.thesis.ispeed.app.shared.extension.isServiceRunning
import com.thesis.ispeed.app.shared.extension.setVisible
import com.thesis.ispeed.app.shared.extension.showFancyToast
import com.thesis.ispeed.app.shared.widget.DialogFactory
import com.thesis.ispeed.app.shared.widget.DialogFactory.Companion.showCustomInfoDialog
import com.thesis.ispeed.app.util.Default
import com.thesis.ispeed.databinding.FragmentAutomaticTrackBinding
import com.thesis.ispeed.databinding.WidgetScreenToolbarBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.Locale


@AndroidEntryPoint
class AutomaticTrackFragment : BaseFragment<FragmentAutomaticTrackBinding>(bindingInflater = FragmentAutomaticTrackBinding::inflate) {

    private var latitude: Double = 0.0

    private var longitude: Double = 0.0

    private var currentLocation: String? = null

    private var googleApiClient: GoogleApiClient? = null

    private val REQUEST_LOCATION = 1

    private val REQUEST_MEDIA = 2

    private var trackList: MutableList<TrackInternetModel>? = ArrayList()

    private val viewModel: AutomaticTrackViewModel by viewModels()

    private var ITEM_COUNT = 10

    private var isWeekly = true

    private val calendar: Calendar = Calendar.getInstance()

    private var currentWeek = calendar.get(Calendar.WEEK_OF_MONTH)

    private val REQUEST_FOREGROUND_SERVICE_PERMISSION = 1001

    override fun onViewCreated() {
        super.onViewCreated()
        with(binding) {
            setupGoogleClient()
            setupComponents()
            setupObserver()
            checkLocationPermission()
        }
    }

    private fun FragmentAutomaticTrackBinding.setupComponents() {
        // Setup Domain Components
        viewModel.getTotalTrackCount()

        toolBar.setupToolbar()
        scrollable.addScrollObservable()

        btnExport.setOnClickListener {
            captureScreen()
        }

        today.setOnClickListener {
            if (isWeekly) {
                isWeekly = false
                setFilterAppearance()
            }
        }

        weekly.setOnClickListener {
            if (!isWeekly) {
                isWeekly = true
                setFilterAppearance()
            }
        }

        previousWeek.setOnClickListener {
            buildGraphChart(trackList, weekNumber = getWeekNumber(WeekState.PREVIOUS_WEEK))
        }

        nextWeek.setOnClickListener {
            buildGraphChart(trackList, weekNumber = getWeekNumber(WeekState.NEXT_WEEK))
        }

        internetStatusList.setupList()
        setupLabels()
    }

    private fun requestForegroundServicePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                    arrayOf(Manifest.permission.FOREGROUND_SERVICE_LOCATION),
                    REQUEST_FOREGROUND_SERVICE_PERMISSION
                )
            } else {
                // Permission already granted, start the service
                startBackgroundTracking()
            }
        } else {
            // For Android 13 and below, start service directly
            startBackgroundTracking()
        }
    }

    private fun getWeekNumber(weekState: WeekState): Int {
        if (weekState == WeekState.PREVIOUS_WEEK) {
            currentWeek--
        } else {
            if (currentWeek != 5) {
                currentWeek++
            }
        }

        return currentWeek
    }

    enum class WeekState {
        PREVIOUS_WEEK,
        NEXT_WEEK,
        DEFAULT
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        @NonNull permissions: Array<String>,
        @NonNull grantResults: IntArray
    ) {
        if (requestCode == REQUEST_FOREGROUND_SERVICE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start foreground service
                startBackgroundTracking()
            }
        } else if (requestCode == REQUEST_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                showPermissionAlertDialog()
            }
        }
    }

    private fun showPermissionAlertDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage("Location permission is denied, we need to access the location to get your address, do you want to turn it on?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
                val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                with(intent) {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                    addCategory(CATEGORY_DEFAULT)
                    addFlags(FLAG_ACTIVITY_NEW_TASK)
                    addFlags(FLAG_ACTIVITY_NO_HISTORY)
                    addFlags(FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                startActivity(intent)
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.cancel()
                showPermissionAlertDialog()
            }
        val alert: AlertDialog = builder.create()
        alert.setCancelable(false)
        alert.setCanceledOnTouchOutside(false)
        alert.show()
    }

    private fun checkLocationPermission() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
        mLocationRequest = LocationRequest.create()

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION
            )
        } else if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showAlertMessage()
        } else {
            startLocationUpdates()
        }
    }

    private fun CustomRecyclerView.setupList() {
        addItemBindings(viewHolders = setupTrackListItemBinder(
            context = requireContext(),
            dtoRetriever = TrackListItem::dto,
        ))
    }

    private fun setFilterAppearance() {
        buildGraphChart(trackList)
        with(binding) {
            if (isWeekly) {
                today.setBackgroundResource(0)
                weekly.setBackgroundResource(R.drawable.rounded_outline_background)

                previousWeek.setVisible(canShow = true)
                nextWeek.setVisible(canShow = true)
                weeklyDate.setVisible(canShow = true)
            } else {
                today.setBackgroundResource(R.drawable.rounded_outline_background)
                weekly.setBackgroundResource(0)

                previousWeek.setVisible(canShow = false)
                nextWeek.setVisible(canShow = false)
                weeklyDate.setVisible(canShow = false)
            }
        }
    }

    private fun WidgetScreenToolbarBinding.setupToolbar() {
        toolBar.setBackgroundColor(resources.getColor(R.color.lightRed))
        title.text = "Automatic Tracking"
        arrowBack.setOnClickListener {
            findNavController().popBackStack()
        }

        instruction.setOnClickListener {
            showCustomInfoDialog(
                context = requireContext(),
                dialogAttributes = DialogFactory.InformationDialogAttributes(
                    title = "INSTRUCTIONS",
                    header = "How to use this service?",
                    firstLineTitle = requireContext().getString(R.string.instructions_manual_tracking),
                    secondLineTitle = requireContext().getString(R.string.instructions_automatic_tracking),
                    thirdLineTitle = requireContext().getString(R.string.instructions_geo_map_tracking)
                )
            )
        }

        about.setOnClickListener {
            showCustomInfoDialog(
                context = requireContext(),
                dialogAttributes = DialogFactory.InformationDialogAttributes(
                    title = "ABOUT",
                    header = "Purpose of each Testing Services",
                    firstLineTitle = "This service will manually track your internet connectivity capturing its ping, download, and upload speed.",
                    secondLineTitle = "This service will provide real-time and compiled reports of the user’s internet stability status. The graph will show the unstable/stable reports gathered during testing that are sectioned through timely synchronization (Days, Weeks, Months). The table below shows the continuous report of the user’s internet stability status.",
                    thirdLineTitle = "This service will provide reports regarding the behavior of user’s internet connectivity in their current location."
                )
            )
        }
    }

    private fun NestedScrollView.addScrollObservable() {
        setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { view, _, scrollY, _, _ ->
            if (view.getChildAt(0).bottom <= height + scrollY && !viewModel.isLoading) {
                // Increment the ITEM COUNT to 10 for pagination.
                ITEM_COUNT += 10

                if ((trackList?.size ?: 0) < (viewModel.totalCount.value ?: 0)) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        launch(Dispatchers.Main) {
                            binding.loadingWidget.root.setVisible(canShow = true)
                        }

                        viewModel.getTrackInternetList(count = ITEM_COUNT)
                    }
                }
            }
        })
    }

    private fun buildGraphChart(
        trackList: MutableList<TrackInternetModel>?,
        weekNumber: Int = currentWeek
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            with(binding.barChart) {
                trackList?.let {
                    val barEntries = ArrayList<BarEntry>()

                    val weekFirstDay = viewUtil.parseWeekDate(viewUtil.getWeeklyDate(weekNumber)[0])
                    val weekLastDay = viewUtil.parseWeekDate(viewUtil.getWeeklyDate(
                        weekNumber
                    )[6])

                    binding.weeklyDate.text = "Weekly Range: ($weekFirstDay - $weekLastDay)"

                    if (isWeekly) {
                        val mondayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[0]
                        }
                        val mondayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[0]
                        }

                        val tuesdayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[1]
                        }
                        val tuesdayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[1]
                        }

                        val wednesdayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[2]
                        }
                        val wednesdayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[2]
                        }

                        val thursdayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[3]
                        }
                        val thursdayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[3]
                        }

                        val fridayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[4]
                        }
                        val fridayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[4]
                        }

                        val saturdayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[5]
                        }
                        val saturdayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[5]
                        }

                        val sundayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[6]
                        }
                        val sundayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[6]
                        }

                        barEntries.add(BarEntry(0F, floatArrayOf(mondayStable.size.toFloat(), mondayUnstable.size.toFloat())))
                        barEntries.add(BarEntry(1F, floatArrayOf(tuesdayStable.size.toFloat(), tuesdayUnstable.size.toFloat())))
                        barEntries.add(BarEntry(2F, floatArrayOf(wednesdayStable.size.toFloat(), wednesdayUnstable.size.toFloat())))
                        barEntries.add(BarEntry(3F, floatArrayOf(thursdayStable.size.toFloat(), thursdayUnstable.size.toFloat())))
                        barEntries.add(BarEntry(4F, floatArrayOf(fridayStable.size.toFloat(), fridayUnstable.size.toFloat())))
                        barEntries.add(BarEntry(5F, floatArrayOf(saturdayStable.size.toFloat(), saturdayUnstable.size.toFloat())))
                        barEntries.add(BarEntry(6F, floatArrayOf(sundayStable.size.toFloat(), sundayUnstable.size.toFloat())))
                    } else {
                        val dayPosition = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
                            1 -> 6 // "Sunday"
                            2 -> 0 // "Monday"
                            3 -> 1 // "Tuesday"
                            4 -> 2 // "Wednesday"
                            5 -> 3 // "Thursday"
                            6 -> 4 // "Friday"
                            7 -> 5 // "Saturday"
                            else -> 0
                        }

                        val todayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[dayPosition]
                        }

                        val todayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate(weekNumber)[dayPosition]
                        }

                        barEntries.add(BarEntry(dayPosition.toFloat(), floatArrayOf(todayStable.size.toFloat(), todayUnstable.size.toFloat())))
                    }

                    val barDataSet = BarDataSet(barEntries, "(Status)")
                    barDataSet.stackLabels = arrayOf("Stable", "Unstable")
                    val barData = BarData(barDataSet)
                    barDataSet.setColors(Color.GREEN, Color.RED)

                    data = barData
                    animateY(1000)
                    invalidate()
                }
            }
        }
    }

    private fun FragmentAutomaticTrackBinding.setupLabels() {
        with(barChart) {
            val weekEndLabel = arrayOf("Mon", "Tues", "Wed", "Thurs", "Fri", "Sat", "Sun")

            val xAxis: XAxis = xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM_INSIDE

            val formatter = IAxisValueFormatter { value, _ -> weekEndLabel[value.toInt()] }
            xAxis.granularity = 1f // minimum axis-step (interval) is 1
            xAxis.valueFormatter = formatter
            xAxis.yOffset = -10F
            extraBottomOffset = 10F

            setTouchEnabled(false)
            isDragEnabled = false
            description.isEnabled = false

            val yAxisRight: YAxis = axisRight
            yAxisRight.isEnabled = false
            setScaleEnabled(true)
        }
    }

    private fun setupObserver() {
        with(viewModel) {
            userDetails.observe(viewLifecycleOwner) {
                binding.setupDetails(userDetails = it)
            }

            totalCount.observe(viewLifecycleOwner) {
                viewModel.getTrackInternetList(count = ITEM_COUNT)
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                launch {
                    uiItems.collectLatest {
                        when(it) {
                            is ShowTrackLoading -> binding.loadingWidget.root.setVisible(canShow = true)
                            is ShowTrackDismissLoading -> binding.loadingWidget.root.setVisible(canShow = false)
                            is GetTrackList -> {
                                it.response?.let { it1 ->
                                    trackList?.clear()
                                    trackList?.addAll(it1)
                                    buildGraphChart(trackList)
                                    viewModel.getUiItems(response = it.response)
                                }
                            }
                            is GetUiItems -> {
                                binding.loadingWidget.root.setVisible(canShow = false)
                                binding.internetStatusList.setItems(items = it.uiItems)

                                launch(Dispatchers.Main) {
                                    with(binding) {
                                        val connectedCount = trackList?.filter { it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status }
                                        val disConnectedCount = trackList?.filter { it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status }
                                        stableCount.text = connectedCount?.size.toString()
                                        unstableCount.text = disConnectedCount?.size.toString()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun FragmentAutomaticTrackBinding.setupDetails(userDetails: UserDetails) {
        with(userDetails) {
            nameLbl.text = "$firstName $lastName"
            ispName.text = "ISP: ${Default.ispName}"
            locationName.text = "Location: $currentLocation"
        }
    }

    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private val interval: Long = 10000 // 10seconds
    private val fastestInterval: Long = 5000 // 5 seconds
    private lateinit var mLastLocation: Location
    private lateinit var mLocationRequest: LocationRequest

    override fun onPause() {
        super.onPause()
        fusedLocationProviderClient?.removeLocationUpdates(mLocationCallback)
    }

    private fun startLocationUpdates() {
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = interval
        mLocationRequest.fastestInterval = fastestInterval

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest)
        val locationSettingsRequest = builder.build()
        val settingsClient = LocationServices.getSettingsClient(requireContext())
        settingsClient.checkLocationSettings(locationSettingsRequest)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())

        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            fusedLocationProviderClient?.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback,
                Looper.myLooper()!!
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation
            locationChanged(locationResult.lastLocation)
            latitude = locationResult.lastLocation.latitude
            longitude = locationResult.lastLocation.longitude

            // Internet automatically starts, when this screen is open.
            if (requireContext().isServiceRunning(TrackingService::class.java).not()) {
                // Request foreground service permission before starting service
                requestForegroundServicePermission()
            }
        }
    }

    fun locationChanged(location: Location) {
        mLastLocation = location
        longitude = mLastLocation.longitude
        latitude = mLastLocation.latitude

        // Get last known location. In some rare situations this can be null.
        try {
            latitude = location.latitude
            longitude = location.longitude
            getCityName(latitude, longitude)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun showAlertMessage() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage("Location is disabled, we need to turn on the location to get your address, do you want to turn it on?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
                startActivityForResult(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),10)
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.cancel()
                showAlertMessage()
            }
        val alert: AlertDialog = builder.create()
        alert.setCancelable(false)
        alert.setCanceledOnTouchOutside(false)
        alert.show()
    }

    private fun getCityName(lat: Double?,long: Double?) {
        var cityName: String?
        val geoCoder = Geocoder(requireContext(), Locale.getDefault())
        val address = geoCoder.getFromLocation(lat ?: 0.0,long ?: 0.0,1)
        cityName = address?.get(0)?.getAddressLine(0)
        if (cityName == null){
            cityName = address?.get(0)?.locality
            if (cityName == null){
                cityName = address?.get(0)?.subAdminArea
            }
        }

        if (cityName?.contains(",") == true) {
            val result = try {
                "${cityName.split(",")[1]}, ${ cityName.split(",")[2]}"
            } catch (e: Exception) {
                try {
                    cityName.split(",")[3]
                } catch (e: Exception) {
                    try {
                        cityName.split(",")[0]
                    } catch (e: Exception) {
                        "Unknown"
                    }
                }
            }
            currentLocation = result
            binding.locationName.text = "Location: $currentLocation"
        }
    }

    private fun setupGoogleClient() {
        if (googleApiClient == null) {
            googleApiClient = GoogleApiClient.Builder(requireContext())
            .addApi(LocationServices.API)
            .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                override fun onConnected(bundle: Bundle?) {}
                override fun onConnectionSuspended(i: Int) {
                    googleApiClient?.connect()
                }
            })
            .addOnConnectionFailedListener { }.build()
            googleApiClient?.connect()
        }
    }

    private fun startBackgroundTracking() {
        val intent = Intent(requireContext(), TrackingService::class.java)

        intent.putExtra("lat", latitude)
        intent.putExtra("longi", longitude)
        intent.putExtra("loc", currentLocation)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) getAppActivity().startForegroundService(intent)
        else getAppActivity().startService(intent)
    }

    private fun stopBackgroundTracking() {
        val stopServiceIntent = Intent(requireContext(), TrackingService::class.java)
        stopServiceIntent.putExtra("loc", currentLocation)
        getAppActivity().stopService(stopServiceIntent)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(eventStr: String?) {
        if (eventStr.equals("trigger")){
            viewModel.getTrackInternetList(count = ITEM_COUNT)
            viewModel.userDetails.value?.let { binding.setupDetails(userDetails = it) }
        } else {
            Default.ispName = eventStr ?: "Unknown"
            binding.ispName.text = "ISP: ${Default.ispName}"
        }
    }

    private fun captureScreen() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    getAppActivity(),
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    REQUEST_MEDIA
                )
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    getAppActivity(),
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_MEDIA
                )
                return
            }
        }

        try {
            val bitmap: Bitmap? = getScreenshotFromRecyclerView(binding.internetStatusList)
            bitmap?.let { saveBitmapToGallery(bitmap) }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // ✅ Save the bitmap to gallery (works on all Android versions)
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "Screen_Screenshot_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
            }

            val contentResolver = requireContext().contentResolver
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                try {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                        showFancyToast("Screenshot saved to gallery.")
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    showFancyToast("Error saving screenshot.")
                }
            } ?: showFancyToast("Failed to save screenshot.")
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "Screen_Screenshot_${System.currentTimeMillis()}.jpg")
            try {
                FileOutputStream(file).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                showFancyToast("Screenshot saved to: ${file.absolutePath}")
            } catch (e: IOException) {
                e.printStackTrace()
                showFancyToast("Error saving screenshot.")
            }
        }
    }

    private fun getScreenshotFromRecyclerView(view: RecyclerView): Bitmap? {
        val adapter = view.adapter
        var bigBitmap: Bitmap? = null
        if (adapter != null) {
            val size = adapter.itemCount
            var height = 0
            val paint = Paint()
            var iHeight = 0
            val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

            // Use 1/8th of the available memory for this memory cache.
            val cacheSize = maxMemory / 8
            val bitmaCache = LruCache<String, Bitmap>(cacheSize)
            for (i in 0 until size) {
                val holder = adapter.createViewHolder(view, adapter.getItemViewType(i))
                adapter.onBindViewHolder(holder, i)
                holder.itemView.measure(
                    View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                holder.itemView.layout(
                    0,
                    0,
                    holder.itemView.measuredWidth,
                    holder.itemView.measuredHeight
                )
                holder.itemView.isDrawingCacheEnabled = true
                holder.itemView.buildDrawingCache()
                val drawingCache = holder.itemView.drawingCache
                if (drawingCache != null) {
                    bitmaCache.put(i.toString(), drawingCache)
                }
                height += holder.itemView.measuredHeight
            }
            bigBitmap = Bitmap.createBitmap(view.measuredWidth, height, Bitmap.Config.ARGB_8888)
            val bigCanvas = Canvas(bigBitmap)
            bigCanvas.drawColor(Color.WHITE)
            for (i in 0 until size) {
                val bitmap = bitmaCache[i.toString()]
                bigCanvas.drawBitmap(bitmap, 0f, iHeight.toFloat(), paint)
                iHeight += bitmap.height
                bitmap.recycle()
            }
        }
        return bigBitmap
    }
}