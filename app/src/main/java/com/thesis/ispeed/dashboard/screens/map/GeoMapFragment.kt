package com.thesis.ispeed.dashboard.screens.map

import LocationUpdatesHelper
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.Location
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.shashank.sony.fancytoastlib.FancyToast
import com.thesis.ispeed.R
import com.thesis.ispeed.app.foundation.BaseFragment
import com.thesis.ispeed.app.shared.extension.showFancyToast
import com.thesis.ispeed.app.shared.widget.DialogFactory
import com.thesis.ispeed.app.shared.widget.DialogFactory.Companion.showCustomInfoDialog
import com.thesis.ispeed.app.util.Default.Companion.REQUEST_LOCATION
import com.thesis.ispeed.app.util.HttpDownloadTest
import com.thesis.ispeed.app.util.HttpUploadTest
import com.thesis.ispeed.app.util.PingTest
import com.thesis.ispeed.app.util.SpeedTestHandler
import com.thesis.ispeed.databinding.FragmentGeoMapBinding
import com.thesis.ispeed.databinding.WidgetScreenToolbarBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class GeoMapFragment : BaseFragment<FragmentGeoMapBinding>(bindingInflater = FragmentGeoMapBinding::inflate) {

    private var locationMarker: PointAnnotation? = null

    private lateinit var annotationManager: PointAnnotationManager

    private var googleApiClient: GoogleApiClient? = null

    private var fusedLocationClient: FusedLocationProviderClient? = null

    private lateinit var locationUpdatesHelper: LocationUpdatesHelper

    private var currentLocForSavingData: String? = null

    private var mapView: MapView? = null

    private var permissionsManager: PermissionsManager? = null

    private val decimal = DecimalFormat("#.##")

    private var position = 0

    private var lastPosition = 0

    private var tempBlackList: HashSet<String>? = null

    private val viewModel: GeoMapViewModel by viewModels()

    override fun onCreated(savedInstanceState: Bundle?) {
        super.onCreated(savedInstanceState)
        MapboxOptions.accessToken = context?.getString(R.string.mapbox_maps_api_key).orEmpty()
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

    private fun showPermissionAlertDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setMessage("Location permission is denied, we need to access the location to get your address, do you want to turn it on?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                with(intent) {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
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

    private fun setupObserver() {
        with(viewModel) {
            userDetails.observe(viewLifecycleOwner) { details ->
                binding.tvUserInternet.text = "Name: ${details.firstName} ${details.lastName}"
            }
        }
    }

    private fun FragmentGeoMapBinding.setupComponents() {
        tempBlackList = HashSet()
        toolBar.setupToolbar()
        this@GeoMapFragment.mapView = mapView
        val mapboxMap = mapView.getMapboxMap()

        // Load map style with a callback
        mapboxMap.loadStyleUri(Style.MAPBOX_STREETS) { style ->
            checkAndRequestPermissions()
        }

        myLocationButton.setOnClickListener {
            // Check to ensure coordinates aren't null, probably a better way of doing this...
            enableLocationComponent()
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .zoom(14.0) // 👈 Set zoom level
                    .build()
            )
        }

        btnMeasureNow.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                checkAndRequestPermissions()
                return@setOnClickListener
            }

            measureSpeedTest()
        }
    }

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

            if (coarseGranted || fineGranted) {
                enableLocationComponent()
            } else {
                showPermissionAlertDialog()
            }
        }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocationComponent()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
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

                        checkGalleryPermission()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }).start()
        }
    }

    private val galleryPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                captureMapbox(binding.mapView) { mapBitmap ->
                    if (mapBitmap != null) {
                        mergeBitmaps(binding.mapView, binding.parentLayout, mapBitmap) { finalBitmap ->
                            saveBitmapToGallery(finalBitmap)
                        }
                    }
                }
            } else {
                showFancyToast(
                    "Gallery Permission Denied, We need to access your gallery to be able to save the result photo.",
                    FancyToast.INFO,
                    FancyToast.LENGTH_LONG
                )
            }
        }

    private fun checkGalleryPermission() {
        val galleryPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val isGranted = ContextCompat.checkSelfPermission(
            requireContext(), galleryPermission
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            captureMapbox(binding.mapView) { mapBitmap ->
                if (mapBitmap != null) {
                    mergeBitmaps(binding.mapView, binding.parentLayout, mapBitmap) { finalBitmap ->
                        saveBitmapToGallery(finalBitmap)
                    }
                }
            }
        } else {
            galleryPermissionRequest.launch(galleryPermission)
        }
    }

    private fun captureLayout(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    private fun captureMapbox(mapView: MapView, callback: (Bitmap?) -> Unit) {
        mapView.snapshot { mapBitmap ->
            callback(mapBitmap)
        }
    }

    private fun mergeBitmaps(mapView: MapView, rootView: View, mapBitmap: Bitmap, callback: (Bitmap) -> Unit) {
        val screenBitmap = captureLayout(rootView)

        val finalBitmap = Bitmap.createBitmap(screenBitmap.width, screenBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(finalBitmap)

        // Draw UI first (without Mapbox)
        canvas.drawBitmap(screenBitmap, 0f, 0f, null)

        // Get MapView position
        val mapLeft = mapView.left.toFloat()
        val mapTop = mapView.top.toFloat()

        // Draw Mapbox at the correct position
        canvas.drawBitmap(mapBitmap, mapLeft, mapTop, null)

        callback(finalBitmap)
    }

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
        speedTestHandler = SpeedTestHandler()
        speedTestHandler.start()

        viewUtil.gpsChecker(
            getAppActivity(),
            googleApiClient,
            REQUEST_LOCATION
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationUpdatesHelper.stopLocationUpdates()
        } catch (e: Exception) { }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionsManager!!.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showPermissionAlertDialog()
            }
        }
    }

    private fun enableLocationComponent() {
        locationUpdatesHelper = LocationUpdatesHelper(requireContext()) { location ->
            val point = Point.fromLngLat(location.longitude, location.latitude)

            // Move Camera to New Location
            mapView?.mapboxMap?.setCamera(
                CameraOptions.Builder()
                    .center(point)
                    .zoom(14.0)
                    .build()
            )

            // Add or Update the Marker
            updateLocationMarker(point)
        }

        locationUpdatesHelper.startLocationUpdates()

        // Initialize Annotation Manager for Markers
        mapView?.annotations?.let {
            annotationManager = it.createPointAnnotationManager()
        }
    }

    private fun updateLocationMarker(point: Point) {
        if (::annotationManager.isInitialized) {
            if (locationMarker == null) {
                val bitmap = getBitmapFromVectorDrawable(R.drawable.ic_location)

                if (bitmap != null) {
                    val annotationOptions = PointAnnotationOptions()
                        .withPoint(point)
                        .withIconImage(bitmap) // Use converted bitmap

                    locationMarker = annotationManager.create(annotationOptions)
                }
            } else {
                locationMarker?.point = point
                annotationManager.update(locationMarker!!)
            }
        }
    }

    // Convert Vector Drawable to Bitmap
    private fun getBitmapFromVectorDrawable(drawableId: Int): Bitmap? {
        val drawable: Drawable = AppCompatResources.getDrawable(requireContext(), drawableId) ?: return null

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        return bitmap
    }
}