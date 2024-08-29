package com.olrak.ispeed.dashboard.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.olrak.ispeed.R
import com.olrak.ispeed.app.foundation.BaseFragment
import com.olrak.ispeed.app.shared.extension.showFancyToast
import com.olrak.ispeed.app.shared.widget.DialogFactory
import com.olrak.ispeed.app.shared.widget.DialogFactory.Companion.showCustomInfoDialog
import com.olrak.ispeed.app.util.Default.Companion.REQUEST_LOCATION
import com.olrak.ispeed.app.util.HttpDownloadTest
import com.olrak.ispeed.app.util.HttpUploadTest
import com.olrak.ispeed.app.util.PingTest
import com.olrak.ispeed.app.util.SpeedTestHandler
import com.olrak.ispeed.databinding.FragmentGeoMapBinding
import com.olrak.ispeed.databinding.WidgetScreenToolbarBinding
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class GeoMapFragment : BaseFragment<FragmentGeoMapBinding>(bindingInflater = FragmentGeoMapBinding::inflate),
    OnMapReadyCallback, PermissionsListener {

    private var googleApiClient: GoogleApiClient? = null

    private var fusedLocationClient: FusedLocationProviderClient? = null

    private var currentLocForSavingData: String? = null

    private var mapboxMap: MapboxMap? = null

    private var locationComponent: LocationComponent? = null

    private var currentLocation: Location? = null

    private var initialPosition: CameraPosition? = null

    private var permissionsManager: PermissionsManager? = null

    private val decimal = DecimalFormat("#.##")

    private var position = 0

    private var lastPosition = 0

    private var tempBlackList: HashSet<String>? = null

    private val viewModel: GeoMapViewModel by viewModels()

    override fun onCreated(savedInstanceState: Bundle?) {
        super.onCreated(savedInstanceState)
        Mapbox.getInstance(requireContext(), getString(R.string.mapbox_maps_api_key))
    }

    override fun onViewCreated() {
        super.onViewCreated()
        with(binding) {
            setupComponents()
            createGoogleApiClient()
            observeLocationClient()
            setupObserver()
        }
    }

    private fun setupObserver() {
        with(viewModel) {
            userDetails.observe(viewLifecycleOwner) { details ->
                binding.tvUserInternet.text = "Name: ${details.firstName} ${details.lastName}"
            }
        }
    }

    private fun FragmentGeoMapBinding.setupComponents() {
        mapView.getMapAsync(this@GeoMapFragment)
        tempBlackList = HashSet()

        toolBar.setupToolbar()

        myLocationButton.setOnClickListener {
            // Check to ensure coordinates aren't null, probably a better way of doing this...
            if (mapboxMap?.locationComponent != null) {
                enableLocationComponent(mapboxMap?.style)
                locationComponent?.zoomWhileTracking(14.0)
            }
        }

        btnMeasureNow.setOnClickListener {
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

                return@setOnClickListener
            }

            measureSpeedTest()
        }
    }

    private fun WidgetScreenToolbarBinding.setupToolbar() {
        toolBar.setBackgroundColor(resources.getColor(R.color.midnight_blue))
        title.text = "Geo Loc Map Tracking"
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

    private fun measureSpeedTest() {
        with(binding) {
            btnMeasureNow.isEnabled = false
            speedTestHandler = SpeedTestHandler()
            speedTestHandler.start()

            Thread(Runnable {
                var timeCount = 600 // 1min
                while (speedTestHandler.isFinished.not()) {
                    timeCount--
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) { }

                    if (timeCount <= 0) {
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                            btnMeasureNow.isEnabled = true
                            btnMeasureNow.textSize = 16f
                        }
                        return@Runnable
                    }
                }

                //Find closest server
                val mapKey: HashMap<Int, String> = speedTestHandler.mapKey
                val mapValue: HashMap<Int, List<String>> = speedTestHandler.mapValue
                val selfLat: Double = speedTestHandler.selfLat
                val selfLon: Double = speedTestHandler.selfLon

                tvIsp.text = "ISP: " + speedTestHandler.ispName

                var tmp = 19349458.0

                var findServerIndex = 0
                for (index in mapKey.keys) {
                    if (tempBlackList?.contains(mapValue[index]!![5]) == true) {
                        continue
                    }
                    val source = Location("Source")
                    source.latitude = selfLat
                    source.longitude = selfLon
                    val ls = mapValue[index]!!
                    val dest = Location("Dest")
                    dest.latitude = ls[0].toDouble()
                    dest.longitude = ls[1].toDouble()
                    val distance = source.distanceTo(dest).toDouble()
                    if (tmp > distance) {
                        tmp = distance
                        findServerIndex = index
                    }
                }
                val testAddr = mapKey[findServerIndex]!!.replace("http://", "https://")
                val info = mapValue[findServerIndex]

                if (info == null) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        btnMeasureNow.textSize = 12f
                        btnMeasureNow.text = "There was a problem in getting Host Location. Try again later."
                    }
                    return@Runnable
                }

                //Reset value, graphics
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    tvPing.text = "0 ms"
                    tvDownload.text = "0 Mbps"
                    tvUpload.text = "0 Mbps"
                    tvStablitiy.text = "Internet Stability: "
                    tvTimeRecorded.text = "Time Recorded: "
                    tvIsp.text = "ISP: " + speedTestHandler.ispName
                }

                val pingRateList: MutableList<Double> = ArrayList()
                val downloadRateList: MutableList<Double> = ArrayList()
                val uploadRateList: MutableList<Double> = ArrayList()
                var pingTestStarted = false
                var pingTestFinished = false
                var downloadTestStarted = false
                var downloadTestFinished = false
                var uploadTestStarted = false
                var uploadTestFinished = false

                //Init Test
                val pingTest = PingTest(info[6].replace(":8080", ""), 3)
                val downloadTest = HttpDownloadTest(
                    testAddr.replace(
                        testAddr.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()[testAddr.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray().size - 1], ""
                    )
                )
                val uploadTest = HttpUploadTest(testAddr)


                //Tests
                while (true) {
                    if (!pingTestStarted) {
                        pingTest.start()
                        pingTestStarted = true
                    }
                    if (pingTestFinished && !downloadTestStarted) {
                        downloadTest.start()
                        downloadTestStarted = true
                    }
                    if (downloadTestFinished && !uploadTestStarted) {
                        uploadTest.start()
                        uploadTestStarted = true
                    }

                    //Ping Test
                    if (pingTestFinished) {
                        //Failure
                        if (pingTest.avgRtt == 0.0) {
                            println("Ping error...")
                        } else {
                            try {
                                //Success
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    tvPing.text = "Ping: " + decimal.format(pingTest.avgRtt) + " ms"
                                    if (pingTest.avgRtt >= 20) {
                                        tvStablitiy.text = "Stability: Stable"
                                    } else {
                                        tvStablitiy.text = "Stability: Unstable"
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        pingRateList.add(pingTest.instantRtt)

                        try {
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                tvPing.text = "Ping: " + decimal.format(pingTest.instantRtt) + " ms"
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    //Download Test
                    if (pingTestFinished) {
                        if (downloadTestFinished) {
                            //Failure
                            if (downloadTest.getFinalDownloadRate() == 0.0) {
                                println("Download error...")
                            } else {
                                try {
                                    //Success
                                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                        tvDownload.text = "Download Speed: " + decimal.format(downloadTest.getFinalDownloadRate()) + " Mbps"
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            //Calc position
                            val downloadRate: Double = downloadTest.instantDownloadRate
                            downloadRateList.add(downloadRate)
                            position = getPositionByRate(downloadRate)
                            try {
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    tvDownload.text = "Download Speed: " + decimal.format(downloadTest.instantDownloadRate) + " Mbps"
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            lastPosition = position
                        }
                    }

                    //Upload Test
                    if (downloadTestFinished) {
                        if (uploadTestFinished) {
                            //Failure
                            if (uploadTest.getFinalUploadRate() == 0.0) {
                                println("Upload error...")
                            } else {
                                try {
                                    //Success
                                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                        val date = Date()
                                        val formatter = SimpleDateFormat("hh:mm aa")
                                        tvUpload.text = "Upload Speed: " + decimal.format(uploadTest.getFinalUploadRate()) + " Mbps"
                                        tvTimeRecorded.text = "Time Recorded: " + formatter.format(date)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } else {
                            //Calc position
                            val uploadRate: Double = uploadTest.instantUploadRate
                            uploadRateList.add(uploadRate)
                            position = getPositionByRate(uploadRate)

                            try {
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    val date = Date()
                                    val formatter = SimpleDateFormat("hh:mm aa")
                                    tvUpload.text = "Upload Speed: " + decimal.format(uploadTest.instantUploadRate) + " Mbps"
                                    tvTimeRecorded.text = "Time Recorded: " + formatter.format(date)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            lastPosition = position
                        }
                    }

                    if (pingTestFinished && downloadTestFinished && uploadTest.isFinished) {
                        break
                    }

                    if (pingTest.isFinished) {
                        pingTestFinished = true
                    }

                    if (downloadTest.isFinished) {
                        downloadTestFinished = true
                    }

                    if (uploadTest.isFinished) {
                        uploadTestFinished = true
                    }

                    if (pingTestStarted && !pingTestFinished) {
                        try {
                            Thread.sleep(300)
                        } catch (e: InterruptedException) { }
                    } else {
                        try {
                            Thread.sleep(100)
                        } catch (e: InterruptedException) { }
                    }
                }

                try {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        btnMeasureNow.isEnabled = true
                        btnMeasureNow.textSize = 16f
                        btnMeasureNow.text = "Measure Now"

                        captureMapScreen(parentLayout.rootView)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }).start()
        }
    }

    private fun captureMapScreen(view: View): File? {
        try {
            val dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
            val path = (dirPath + "/" + "SCREEN" + System.currentTimeMillis() + ".png")
            view.isDrawingCacheEnabled = true
            view.isDrawingCacheEnabled = false
            val imageFile = File(path)
            val fileOutputStream = FileOutputStream(imageFile)
            mapboxMap?.snapshot { bitmap: Bitmap ->
                val quality = 100
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fileOutputStream)
                try {
                    fileOutputStream.flush()
                    fileOutputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                showFancyToast("Check Exported Image on your gallery.")
            }
            return imageFile
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getPositionByRate(rate: Double): Int {
        if (rate <= 1) {
            return (rate * 30).toInt()
        } else if (rate <= 10) {
            return (rate * 6).toInt() + 30
        } else if (rate <= 30) {
            return ((rate - 10) * 3).toInt() + 90
        } else if (rate <= 50) {
            return ((rate - 30) * 1.5).toInt() + 150
        } else if (rate <= 100) {
            return ((rate - 50) * 1.2).toInt() + 180
        }
        return 0
    }

    private fun createGoogleApiClient() {
        googleApiClient?.let { googleClient ->
            googleApiClient = GoogleApiClient.Builder(requireContext())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                    override fun onConnected(bundle: Bundle?) {}
                    override fun onConnectionSuspended(i: Int) {
                        googleApiClient!!.connect()
                    }
                })
                .addOnConnectionFailedListener { _: ConnectionResult? -> }.build()
            googleClient.connect()
        }
    }

    private fun observeLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
            // Got last known location. In some rare situations this can be null.
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
                        val geo = Geocoder(requireContext(), Locale.getDefault())
                        val addresses = geo.getFromLocation(location.latitude, location.longitude, 1)
                        currentLocForSavingData = addresses!![0].locality
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        speedTestHandler = SpeedTestHandler()
        speedTestHandler.start()

        viewUtil.gpsChecker(
            getAppActivity(),
            googleApiClient,
            REQUEST_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager!!.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        this.mapboxMap?.setStyle(Style.MAPBOX_STREETS) { style: Style ->
            enableLocationComponent(loadedMapStyle = style)
        }
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        showFancyToast(getString(R.string.user_location_permission_explanation))
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            mapboxMap?.getStyle { style: Style? ->
                enableLocationComponent(style)
            }
        } else {
            showFancyToast(getString(R.string.user_location_permission_not_granted))
            findNavController().popBackStack()
        }
    }

    private fun enableLocationComponent(loadedMapStyle: Style?) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(requireContext())) {
            loadedMapStyle?.let { style ->
                // Get an instance of the component
                locationComponent = mapboxMap?.locationComponent
                locationComponent?.activateLocationComponent(requireContext(), style)
                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }

                locationComponent?.isLocationComponentEnabled = true
                // Set the component's camera mode
                locationComponent?.cameraMode = CameraMode.TRACKING
                locationComponent?.renderMode = RenderMode.COMPASS
                locationComponent?.zoomWhileTracking(14.0)
                val lastKnownLocation: Location? = locationComponent?.lastKnownLocation
                if (lastKnownLocation != null) {
                    currentLocation = lastKnownLocation
                    initialPosition = CameraPosition.Builder()
                    .target(LatLng(currentLocation!!.latitude, currentLocation!!.longitude))
                    .zoom(15.0)
                    .build()
                }

                // Activate with options
                locationComponent?.activateLocationComponent(
                    LocationComponentActivationOptions.builder(
                        requireContext(),
                        loadedMapStyle
                    ).build()
                )

                // Enable to make component visible
                locationComponent?.isLocationComponentEnabled = true

                // Set the component's camera mode
                locationComponent?.cameraMode = CameraMode.TRACKING

                // Set the component's render mode
                locationComponent?.setRenderMode(RenderMode.COMPASS)
            }
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager?.requestLocationPermissions(getAppActivity())
        }
    }
}