package com.scamcallai.app

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Talks to the Flask backend. Shared by MainActivity (test button) and AudioRecorderService. */
object BackendClient {

    /**
     * Uploads [file] to the backend and returns the raw JSON response.
     * Blocking - call from a background thread. Throws [IOException] on network / HTTP errors.
     */
    fun analyze(file: File, mimeType: String, phoneNumber: String? = null): String {
        val conn = URL(BuildConfig.BACKEND_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 120_000 // Whisper on a CPU can take a while
            val boundary = "B${System.currentTimeMillis()}"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            conn.outputStream.buffered().use { out ->
                if (!phoneNumber.isNullOrBlank() && phoneNumber != "unknown") {
                    out.write("--$boundary\r\n".toByteArray())
                    out.write("Content-Disposition: form-data; name=\"number\"\r\n\r\n".toByteArray())
                    out.write("$phoneNumber\r\n".toByteArray())
                }
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"test\"; filename=\"${file.name}\"\r\n".toByteArray())
                out.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }

            if (conn.responseCode != 200) throw IOException("Server returned HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** True if the backend classified the call as SCAM. */
    fun isScam(responseJson: String): Boolean =
        try {
            JSONObject(responseJson).optString("classification") == "SCAM"
        } catch (e: Exception) {
            false
        }
}
