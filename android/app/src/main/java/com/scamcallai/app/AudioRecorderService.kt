package com.scamcallai.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioRecorderService : Service() {

    @Volatile private var isRecording = false
    private var phoneNumber = "unknown"

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        phoneNumber = i?.getStringExtra("phoneNumber") ?: "unknown"
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            // Android 12+ can refuse to start a foreground service from the background
            e.printStackTrace()
            stopSelf()
            return START_NOT_STICKY
        }
        isRecording = true
        Thread { record() }.start()
        return START_STICKY
    }

    private fun record() {
        // check permission first
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val rate = 16000
        val buf = AudioRecord.getMinBufferSize(
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // create audio recorder
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buf
        )

        // temp file in the app's private cache - deleted again after upload
        val outFile = File(cacheDir, "call_${System.currentTimeMillis()}.wav")
        val data = ByteArray(buf)
        var totalBytes = 0

        try {
            RandomAccessFile(outFile, "rw").use { raf ->
                raf.setLength(0)
                raf.write(ByteArray(WAV_HEADER_SIZE)) // placeholder, filled in below

                recorder.startRecording()

                // record for 10 seconds
                val end = System.currentTimeMillis() + 10000
                while (isRecording && System.currentTimeMillis() < end) {
                    val n = recorder.read(data, 0, buf)
                    if (n > 0) {
                        raf.write(data, 0, n)
                        totalBytes += n
                    }
                }

                // A raw PCM dump is not a playable/decodable file; the WAV header makes it one
                raf.seek(0)
                raf.write(wavHeader(totalBytes, rate))
            }
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }

        // send file to flask
        sendFile(outFile)
        outFile.delete()
        stopSelf()
    }

    private fun sendFile(f: File) {
        try {
            val res = BackendClient.analyze(f, "audio/wav", phoneNumber)
            if (BackendClient.isScam(res)) showAlert(res)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAlert(response: String) {
        val intent = Intent(this, ScamAlertActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.putExtra("response", response)
        intent.putExtra("phoneNumber", phoneNumber)
        startActivity(intent)
    }

    private fun createNotification(): Notification {
        val id = "scam"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(id, "Scam Detection", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, id)
            .setContentTitle("ScamCallAI Active")
            .setContentText("Monitoring call...")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }

    override fun onDestroy() { isRecording = false; super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null

    private companion object {
        const val WAV_HEADER_SIZE = 44

        /** 44-byte header for 16-bit mono PCM. */
        fun wavHeader(dataLen: Int, sampleRate: Int): ByteArray =
            ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray())
                putInt(36 + dataLen)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16)                 // fmt chunk size
                putShort(1)                // PCM
                putShort(1)                // mono
                putInt(sampleRate)
                putInt(sampleRate * 2)     // byte rate
                putShort(2)                // block align
                putShort(16)               // bits per sample
                put("data".toByteArray())
                putInt(dataLen)
            }.array()
    }
}
