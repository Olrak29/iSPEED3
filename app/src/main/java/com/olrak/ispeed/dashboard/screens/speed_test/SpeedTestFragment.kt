package com.olrak.ispeed.dashboard.screens.speed_test

import android.R
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.View.MeasureSpec
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.olrak.ispeed.app.foundation.BaseFragment
import com.olrak.ispeed.app.shared.extension.showFancyToast
import com.olrak.ispeed.app.shared.widget.DialogFactory
import com.olrak.ispeed.app.shared.widget.DialogFactory.Companion.showCustomResultDialog
import com.olrak.ispeed.app.util.HttpDownloadTest
import com.olrak.ispeed.app.util.HttpUploadTest
import com.olrak.ispeed.app.util.PingTest
import com.olrak.ispeed.app.util.SpeedTestHandler
import com.olrak.ispeed.databinding.FragmentSpeedTestBinding
import com.olrak.ispeed.databinding.WidgetScreenToolbarBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.achartengine.ChartFactory
import org.achartengine.GraphicalView
import org.achartengine.model.XYMultipleSeriesDataset
import org.achartengine.model.XYSeries
import org.achartengine.renderer.XYMultipleSeriesRenderer
import org.achartengine.renderer.XYSeriesRenderer
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.DecimalFormat


@AndroidEntryPoint
class SpeedTestFragment : BaseFragment<FragmentSpeedTestBinding>(bindingInflater = FragmentSpeedTestBinding::inflate) {

    private var tempBlackList: HashSet<String>? = null

    private var position = 0

    private var lastPosition = 0

    private val decimal = DecimalFormat("#.##")

    override fun onViewCreated() {
        super.onViewCreated()
        with(binding) {
            setupComponents()
        }
    }

    private fun FragmentSpeedTestBinding.setupComponents() {
        tempBlackList = HashSet()

        widgetToolbar.setupToolbar()
        startButton.setOnClickListener {
            getSpeedTest()
        }
    }

    private fun WidgetScreenToolbarBinding.setupToolbar() {
        title.text = "Manual Internet Testing"
        arrowBack.setOnClickListener {
            findNavController().popBackStack()
        }

        instruction.setOnClickListener {
            DialogFactory.showCustomInfoDialog(
                context = requireContext(),
                dialogAttributes = DialogFactory.InformationDialogAttributes(
                    title = "INSTRUCTIONS",
                    header = "How to use this service?",
                    firstLineTitle = requireContext().getString(com.olrak.ispeed.R.string.instructions_manual_tracking),
                    secondLineTitle = requireContext().getString(com.olrak.ispeed.R.string.instructions_automatic_tracking),
                    thirdLineTitle = requireContext().getString(com.olrak.ispeed.R.string.instructions_geo_map_tracking)
                )
            )
        }

        about.setOnClickListener {
            DialogFactory.showCustomInfoDialog(
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

    private fun getSpeedTest() {
        with(binding) {
            startButton.isEnabled = false

            speedTestHandler = SpeedTestHandler()
            speedTestHandler.start()

            try {
                Thread(object : Runnable {
                    var rotate: RotateAnimation? = null
                    override fun run() {

                        var timeCount = 600 //1min
                        while (speedTestHandler.isFinished.not()) {
                            timeCount--
                            try {
                                Thread.sleep(100)
                            } catch (e: InterruptedException) { }

                            if (timeCount <= 0) {
                                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                    showFancyToast( "No Connection...")

                                    startButton.isEnabled = true
                                    startButton.textSize = 16f
                                    startButton.text = "Restart Test"
                                }
                                return
                            }
                        }

                        //Find closest server
                        val mapKey: HashMap<Int, String> = speedTestHandler.mapKey
                        val mapValue: HashMap<Int, List<String>> = speedTestHandler.mapValue
                        val selfLat: Double = speedTestHandler.selfLat
                        val selfLon: Double = speedTestHandler.selfLon
                        var tmp = 19349458.0
                        var dist = 0.0
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
                                dist = distance
                                findServerIndex = index
                            }
                        }
                        val testAddr = mapKey[findServerIndex]!!.replace("http://", "https://")
                        val info = mapValue[findServerIndex]
                        if (info == null) {
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                startButton.text = "There was a problem in getting Host Location. Try again later."
                            }
                            return
                        }

                        //Init Ping graphic
                        val pingRenderer = XYSeriesRenderer()
                        val pingFill: XYSeriesRenderer.FillOutsideLine = XYSeriesRenderer.FillOutsideLine(XYSeriesRenderer.FillOutsideLine.Type.BOUNDS_ALL)
                        pingFill.color = Color.parseColor("#4d5a6a")
                        pingRenderer.addFillOutsideLine(pingFill)
                        pingRenderer.isDisplayChartValues = false
                        pingRenderer.isShowLegendItem = false
                        pingRenderer.color = Color.parseColor("#4d5a6a")
                        pingRenderer.lineWidth = 5F
                        val multiPingRenderer = XYMultipleSeriesRenderer()
                        multiPingRenderer.xLabels = 0
                        multiPingRenderer.yLabels = 0
                        multiPingRenderer.isZoomEnabled = false
                        multiPingRenderer.xAxisColor = Color.parseColor("#647488")
                        multiPingRenderer.yAxisColor = Color.parseColor("#2F3C4C")
                        multiPingRenderer.setPanEnabled(true, true)
                        multiPingRenderer.isZoomButtonsVisible = false
                        multiPingRenderer.marginsColor = Color.argb(0x00, 0xff, 0x00, 0x00)
                        multiPingRenderer.addSeriesRenderer(pingRenderer)

                        //Init Download graphic
                        val downloadRenderer = XYSeriesRenderer()
                        val downloadFill: XYSeriesRenderer.FillOutsideLine = XYSeriesRenderer.FillOutsideLine(XYSeriesRenderer.FillOutsideLine.Type.BOUNDS_ALL)
                        downloadFill.color = Color.parseColor("#4d5a6a")
                        downloadRenderer.addFillOutsideLine(downloadFill)
                        downloadRenderer.isDisplayChartValues = false
                        downloadRenderer.color = Color.parseColor("#4d5a6a")
                        downloadRenderer.isShowLegendItem = false
                        downloadRenderer.lineWidth = 5F
                        val multiDownloadRenderer = XYMultipleSeriesRenderer()
                        multiDownloadRenderer.xLabels = 0
                        multiDownloadRenderer.yLabels = 0
                        multiDownloadRenderer.isZoomEnabled = false
                        multiDownloadRenderer.xAxisColor = Color.parseColor("#647488")
                        multiDownloadRenderer.yAxisColor = Color.parseColor("#2F3C4C")
                        multiDownloadRenderer.setPanEnabled(false, false)
                        multiDownloadRenderer.isZoomButtonsVisible = false
                        multiDownloadRenderer.marginsColor = Color.argb(0x00, 0xff, 0x00, 0x00)
                        multiDownloadRenderer.addSeriesRenderer(downloadRenderer)

                        //Init Upload graphic
                        val uploadRenderer = XYSeriesRenderer()
                        val uploadFill: XYSeriesRenderer.FillOutsideLine = XYSeriesRenderer.FillOutsideLine(XYSeriesRenderer.FillOutsideLine.Type.BOUNDS_ALL)
                        uploadFill.color = Color.parseColor("#4d5a6a")
                        uploadRenderer.addFillOutsideLine(uploadFill)
                        uploadRenderer.isDisplayChartValues = false
                        uploadRenderer.color = Color.parseColor("#4d5a6a")
                        uploadRenderer.isShowLegendItem = false
                        uploadRenderer.lineWidth = 5F
                        val multiUploadRenderer = XYMultipleSeriesRenderer()
                        multiUploadRenderer.xLabels = 0
                        multiUploadRenderer.yLabels = 0
                        multiUploadRenderer.isZoomEnabled = false
                        multiUploadRenderer.xAxisColor = Color.parseColor("#647488")
                        multiUploadRenderer.yAxisColor = Color.parseColor("#2F3C4C")
                        multiUploadRenderer.setPanEnabled(false, false)
                        multiUploadRenderer.isZoomButtonsVisible = false
                        multiUploadRenderer.marginsColor = Color.argb(0x00, 0xff, 0x00, 0x00)
                        multiUploadRenderer.addSeriesRenderer(uploadRenderer)

                        //Reset value, graphics
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                            pingTextView.text = "0 ms"
                            chartPing.removeAllViews()
                            downloadTextView.text = "0 Mbps"
                            chartDownload.removeAllViews()
                            uploadTextView.text = "0 Mbps"
                            chartUpload.removeAllViews()
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
                        val uploadTest = HttpUploadTest(testAddr)
                        val downloadTest = HttpDownloadTest(
                            testAddr.replace(
                                testAddr.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                                    .toTypedArray()[testAddr.split("/".toRegex())
                                    .dropLastWhile { it.isEmpty() }.toTypedArray().size - 1], ""
                            )
                        )

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
                                            pingTextView.text =
                                                decimal.format(pingTest.avgRtt) + " ms"
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            } else {
                                pingRateList.add(pingTest.instantRtt)
                                try {
                                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                        pingTextView.text = decimal.format(pingTest.instantRtt) + " ms"

                                        val pingSeries = XYSeries("")
                                        pingSeries.title = ""
                                        val tmpLs: List<Double> = ArrayList(pingRateList)
                                        for ((count, `val`) in tmpLs.withIndex()) {
                                            pingSeries.add(count.toDouble(), `val`)
                                        }
                                        val dataset = XYMultipleSeriesDataset()
                                        dataset.addSeries(pingSeries)
                                        val chartView: GraphicalView = ChartFactory.getLineChartView(
                                            requireContext(),
                                            dataset,
                                            multiPingRenderer
                                        )
                                        chartPing.addView(chartView, 0)
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
                                                downloadTextView.text = decimal.format(downloadTest.getFinalDownloadRate()) + " Mbps"
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
                                            rotate = RotateAnimation(
                                                lastPosition.toFloat(),
                                                position.toFloat(),
                                                Animation.RELATIVE_TO_SELF,
                                                0.5f,
                                                Animation.RELATIVE_TO_SELF,
                                                0.5f
                                            )
                                            rotate!!.interpolator = LinearInterpolator()
                                            rotate!!.duration = 100
                                            barImageView.startAnimation(rotate)
                                            downloadTextView.text = decimal.format(downloadTest.instantDownloadRate) + " Mbps"

                                            val downloadSeries = XYSeries("")
                                            downloadSeries.title = ""
                                            val tmpLs: List<Double> = ArrayList(downloadRateList)
                                            for ((count, `val`) in tmpLs.withIndex()) {
                                                downloadSeries.add(count.toDouble(), `val`)
                                            }
                                            val dataset = XYMultipleSeriesDataset()
                                            dataset.addSeries(downloadSeries)
                                            val chartView: GraphicalView = ChartFactory.getLineChartView(
                                                requireContext(),
                                                dataset,
                                                multiDownloadRenderer
                                            )
                                            chartDownload.addView(chartView, 0)
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
                                                uploadTextView.text = decimal.format(uploadTest.getFinalUploadRate()) + " Mbps"
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
                                            rotate = RotateAnimation(
                                                lastPosition.toFloat(),
                                                position.toFloat(),
                                                Animation.RELATIVE_TO_SELF,
                                                0.5f,
                                                Animation.RELATIVE_TO_SELF,
                                                0.5f
                                            )
                                            rotate!!.interpolator = LinearInterpolator()
                                            rotate!!.duration = 100
                                            barImageView.startAnimation(rotate)
                                            uploadTextView.text = decimal.format(uploadTest.instantUploadRate) + " Mbps"

                                            val uploadSeries = XYSeries("")
                                            uploadSeries.title = ""
                                            val tmpLs: List<Double> = ArrayList(uploadRateList)
                                            for ((count, `val`) in tmpLs.withIndex()) {
                                                uploadSeries.add(count.toDouble(), `val`)
                                            }
                                            val dataset = XYMultipleSeriesDataset()
                                            dataset.addSeries(uploadSeries)
                                            val chartView: GraphicalView = ChartFactory.getLineChartView(
                                                requireContext(),
                                                dataset,
                                                multiUploadRenderer
                                            )
                                            chartUpload.addView(chartView, 0)
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

                        //Thread
                        try {
                            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                                startButton.isEnabled = true
                                startButton.text = "Restart Test"

                                showCustomResultDialog(
                                    context = requireContext(),
                                    dialogAttributes = DialogFactory.ResultDialogAttributes(
                                        title = "TRACKING RESULTS",
                                        firstLineTitle = "DOWNLOAD: ${decimal.format(downloadTest.getFinalDownloadRate()) + " Mbps"}",
                                        secondLineTitle = "UPLOAD: ${decimal.format(uploadTest.getFinalUploadRate()) + " Mbps"}",
                                        thirdLineTitle = "PING: ${decimal.format(pingTest.avgRtt) + " ms"}",
                                        fourthLineTitle = "DATE: ${viewUtil.getCurrentDate()}",
                                        fifthLineTitle = "STATUS: STABLE",
                                        primaryButtonTitle = "SAVE",
                                        secondaryButtonTitle = "CLOSE"
                                    ),
                                    primaryButtonClicked = ::saveResults
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }).start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getPositionByRate(rate: Double): Int {
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

    private fun saveResults(view: View): File? {
        try {
            val dirPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()
            val path = (dirPath + "/" + "SCREEN" + System.currentTimeMillis() + ".png")
            view.isDrawingCacheEnabled = true
            view.isDrawingCacheEnabled = false
            val imageFile = File(path)
            val fileOutputStream = FileOutputStream(imageFile)

            val quality = 100
            getBitmapView(v = view)?.compress(Bitmap.CompressFormat.JPEG, quality, fileOutputStream)
            try {
                fileOutputStream.flush()
                fileOutputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            showFancyToast("Check Exported Image on your gallery.")
            return imageFile
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getBitmapView(v: View): Bitmap? {
        var screenshot: Bitmap? = null
        try {
            if (v != null) {
                screenshot = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(screenshot)
                v.draw(canvas)
            }
        } catch (e: Exception) {
            Log.e("BLABLA", "Failed to capture screenshot because:" + e.message)
        }
        return screenshot
    }
}