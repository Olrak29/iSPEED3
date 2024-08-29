package com.olrak.ispeed.dashboard.screens.automatic_track

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.LruCache
import android.view.View
import androidx.core.app.ActivityCompat
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
import com.google.android.gms.location.LocationServices
import com.olrak.ispeed.R
import com.olrak.ispeed.app.component.CustomRecyclerView
import com.olrak.ispeed.app.foundation.BaseFragment
import com.olrak.ispeed.app.service.TrackingService
import com.olrak.ispeed.app.shared.binder.TrackListItem
import com.olrak.ispeed.app.shared.binder.setupTrackListItemBinder
import com.olrak.ispeed.app.shared.data.TrackInternetModel
import com.olrak.ispeed.app.shared.data.UserDetails
import com.olrak.ispeed.app.shared.extension.isServiceRunning
import com.olrak.ispeed.app.shared.extension.setVisible
import com.olrak.ispeed.app.shared.extension.showFancyToast
import com.olrak.ispeed.app.shared.widget.DialogFactory
import com.olrak.ispeed.app.shared.widget.DialogFactory.Companion.showCustomInfoDialog
import com.olrak.ispeed.app.util.Default
import com.olrak.ispeed.databinding.FragmentAutomaticTrackBinding
import com.olrak.ispeed.databinding.WidgetScreenToolbarBinding
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

    private var latitude: Double? = null

    private var longitude: Double? = null

    private var currentLocation: String? = null

    private var googleApiClient: GoogleApiClient? = null

    private val REQUEST_LOCATION = 1

    private val REQUEST_MEDIA = 2

    private var fusedLocationClient: FusedLocationProviderClient? = null

    private var trackList: MutableList<TrackInternetModel>? = ArrayList()

    private val viewModel: AutomaticTrackViewModel by viewModels()

    private var ITEM_COUNT = 10

    private var isWeekly = true

    override fun onViewCreated() {
        super.onViewCreated()
        with(binding) {
            setupGoogleClient()
            setupObserveLocation()
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

        internetStatusList.setupList()
        setupLabels()
    }

    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                getAppActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION
            )
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
            } else {
                today.setBackgroundResource(R.drawable.rounded_outline_background)
                weekly.setBackgroundResource(0)
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

                viewLifecycleOwner.lifecycleScope.launch {
                    launch(Dispatchers.Main) {
                        binding.loadingWidget.root.setVisible(canShow = true)
                    }

                    viewModel.getTrackInternetList(count = ITEM_COUNT)
                }
            }
        })
    }

    private fun buildGraphChart(trackList: MutableList<TrackInternetModel>?) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            with(binding.barChart) {
                trackList?.let {
                    val barEntries = ArrayList<BarEntry>()

                    if (isWeekly) {
                        val mondayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[0]
                        }
                        val mondayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[0]
                        }

                        val tuesdayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[1]
                        }
                        val tuesdayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[1]
                        }

                        val wednesdayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[2]
                        }
                        val wednesdayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[2]
                        }

                        val thursdayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[3]
                        }
                        val thursdayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[3]
                        }

                        val fridayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[4]
                        }
                        val fridayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[4]
                        }

                        val saturdayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[5]
                        }
                        val saturdayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[5]
                        }

                        val sundayStable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.CONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[6]
                        }
                        val sundayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[6]
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
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[dayPosition]
                        }

                        val todayUnstable = trackList.filter {
                            it.trackStatus == TrackInternetModel.TrackStatusEnum.DISCONNECTED.status
                                    && viewUtil.parseDateToString(it.trackDate) == viewUtil.getWeeklyDate()[dayPosition]
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

    private fun setupObserveLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationClient?.lastLocation?.addOnSuccessListener {  location ->
            // Get last known location. In some rare situations this can be null.
            if (location != null) {
                try {
                    if (ActivityCompat.checkSelfPermission(
                           requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            getAppActivity(),
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            REQUEST_LOCATION
                        )
                    } else {
                        latitude = location.latitude
                        longitude = location.longitude
                        getCityName(latitude, longitude)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                // Internet automatically starts, when this screen is open.
                if (requireContext().isServiceRunning(TrackingService::class.java).not()) {
                    startBackgroundTracking()
                }
            }
        }
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

    private fun captureScreen(): File? {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    getAppActivity(),
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    REQUEST_MEDIA
                )
                return null
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
                return null
            }
        }

        try {
            val dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
            val path = dirPath + "/" + "SCREEN" + System.currentTimeMillis() + ".png"
            val bitmap: Bitmap? = getScreenshotFromRecyclerView(binding.internetStatusList)
            val imageFile = File(path)
            val fileOutputStream = FileOutputStream(imageFile)
            val quality = 100
            bitmap!!.compress(Bitmap.CompressFormat.JPEG, quality, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            showFancyToast(message = "Check Exported Image on your gallery.")
            return imageFile
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()

        }
        return null
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