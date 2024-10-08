package com.olrak.ispeed.app.util

import com.olrak.ispeed.app.util.HttpUploadTest.Companion.uploadedKByte
import java.io.DataOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory


class HttpUploadTest(fileURL: String) : Thread() {

    private var fileURL = ""
    private var uploadElapsedTime = 0.0
    private var elapsedTime = 0.0
    private var finalUploadRate = 0.0
    private var startTime: Long = 0
    var isFinished = false

    init {
        this.fileURL = fileURL
    }

    private fun round(value: Double, places: Int): Double {
        require(places >= 0)
        var bd: BigDecimal = try {
            BigDecimal(value)
        } catch (ex: Exception) {
            return 0.0
        }
        bd = bd.setScale(places, RoundingMode.HALF_UP)
        return bd.toDouble()
    }

    val instantUploadRate: Double
        get() {
            try {
                val bd = BigDecimal(uploadedKByte)
            } catch (ex: Exception) {
                return 0.0
            }
            return if (uploadedKByte >= 0) {
                val now = System.currentTimeMillis()
                elapsedTime = (now - startTime) / 1000.0
                round((uploadedKByte / 1000.0 * 8 / elapsedTime), 2)
            } else {
                0.0
            }
        }

    fun getFinalUploadRate() = round(finalUploadRate, 2)

    override fun run() {
        try {
            val url = URL(fileURL)
            uploadedKByte = 0
            startTime = System.currentTimeMillis()
            val executor = Executors.newFixedThreadPool(4)
            for (i in 0..3) {
                executor.execute(HandlerUpload(url))
            }
            executor.shutdown()
            while (!executor.isTerminated) {
                try {
                    sleep(100)
                } catch (ex: InterruptedException) { }
            }
            val now = System.currentTimeMillis()
            uploadElapsedTime = (now - startTime) / 1000.0
            finalUploadRate = (uploadedKByte / 1000.0 * 8 / uploadElapsedTime)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        isFinished = true
    }

    companion object {
        var uploadedKByte = 0
    }
}

internal class HandlerUpload(private var url: URL) : Thread() {
    override fun run() {
        val buffer = ByteArray(150 * 1024)
        val startTime = System.currentTimeMillis()
        val timeout = 8
        while (true) {
            try {
                var conn: HttpsURLConnection? = null
                conn = url.openConnection() as HttpsURLConnection
                conn.doOutput = true
                conn.requestMethod = "POST"
                conn.setRequestProperty("Connection", "Keep-Alive")
                conn.sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                conn.hostnameVerifier = HostnameVerifier { hostname, session -> true }
                conn.connect()
                val dos = DataOutputStream(conn.outputStream)
                dos.write(buffer, 0, buffer.size)
                dos.flush()
                conn.responseCode
                uploadedKByte += (buffer.size / 1024.0).toInt()
                val endTime = System.currentTimeMillis()
                val uploadElapsedTime = (endTime - startTime) / 1000.0
                if (uploadElapsedTime >= timeout) {
                    break
                }
                dos.close()
                conn.disconnect()
            } catch (ex: Exception) {
                ex.printStackTrace()
                break
            }
        }
    }
}