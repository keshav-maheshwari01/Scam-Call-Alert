package com.scamcallai.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class AudioRecorderService : Service() {

    // ─── state ────────────────────────────────────────────────────────────────
    @Volatile private var isRecording   = false
    private val scamDetected            = AtomicBoolean(false)   // set to true → stop all chunks
    private var phoneNumber             = "unknown"
    private var callId                  = ""                      // same for every chunk of one call

    // ─── tuning ───────────────────────────────────────────────────────────────
    private companion object {
        const val SAMPLE_RATE      = 16_000          // Hz  — Whisper wants 16 kHz
        const val CHUNK_SECONDS    = 8               // seconds per analysed chunk
        const val MAX_CHUNKS       = 40              // 40 × 8 s ≈ 5 minutes of the call
        const val TAG              = "ScamCallAI"
        const val WAV_HEADER_SIZE  = 44

        /** 44-byte PCM WAV header. */
        fun wavHeader(dataLen: Int, sampleRate: Int): ByteArray =
            ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray());  putInt(36 + dataLen)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray());  putInt(16)
                putShort(1);  putShort(1)                // PCM, mono
                putInt(sampleRate);  putInt(sampleRate * 2)
                putShort(2);  putShort(16)               // block-align, bits
                put("data".toByteArray());  putInt(dataLen)
            }.array()
    }

    // ─── lifecycle ────────────────────────────────────────────────────────────

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        // null intent = Android restarted us after a crash. No call is happening, so don't record.
        if (i == null) { stopSelf(); return START_NOT_STICKY }

        try {
            val notification = createNotification("Listening for scam activity…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            // Most likely Android refused a microphone foreground service started from the
            // background. Show that to the user/Logcat instead of failing silently.
            Log.e(TAG, "Could not start microphone foreground service", e)
            notifyProblem("Monitoring could not start (${e.javaClass.simpleName})")
            stopSelf()
            return START_NOT_STICKY
        }

        // A second "call answered" broadcast for the same call: already recording, don't start another.
        if (isRecording) return START_NOT_STICKY

        phoneNumber = i.getStringExtra("phoneNumber") ?: "unknown"
        callId      = UUID.randomUUID().toString()
        scamDetected.set(false)

        isRecording = true
        Thread { recordInChunks() }.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRecording = false
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null

    // ─── chunked recording ────────────────────────────────────────────────────

    /**
     * Records the call in fixed-size [CHUNK_SECONDS]-second WAV slices.
     * Each slice is uploaded to the backend on a separate thread immediately after
     * it is captured.  Recording stops as soon as:
     *   • [isRecording] is set to false (call ended / service stopped), or
     *   • [scamDetected] flips to true (alert already shown), or
     *   • we have processed [MAX_CHUNKS] chunks.
     */
    private fun recordInChunks() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf(); return
        }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // Use a larger internal buffer (2× minimum) to avoid overflow between reads
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord did not initialise - microphone unavailable or blocked")
            notifyProblem("Microphone unavailable during this call")
            recorder.release()
            stopSelf()
            return
        }

        try {
            recorder.startRecording()
            var chunkIndex = 0

            while (isRecording && !scamDetected.get() && chunkIndex < MAX_CHUNKS) {
                val chunkFile = recordOneChunk(recorder, minBuf)
                chunkIndex++

                // Upload this chunk on a dedicated thread so recording the NEXT
                // chunk starts immediately — no gap, no silence at chunk boundaries.
                val capturedFile = chunkFile
                Thread {
                    analyseChunk(capturedFile, chunkIndex)
                }.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording failed", e)
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
            stopSelf()
        }
    }

    /**
     * Records exactly [CHUNK_SECONDS] seconds of PCM audio into a temp WAV file.
     * Returns the completed file (header written, ready to upload).
     */
    private fun recordOneChunk(recorder: AudioRecord, minBuf: Int): File {
        val outFile = File(cacheDir, "chunk_${System.currentTimeMillis()}.wav")
        val pcmBuf  = ByteArray(minBuf)
        var totalBytes = 0
        var peak = 0                                   // loudest sample seen in this chunk
        val deadline = System.currentTimeMillis() + CHUNK_SECONDS * 1_000L

        RandomAccessFile(outFile, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(WAV_HEADER_SIZE)) // placeholder

            while (isRecording && !scamDetected.get()
                && System.currentTimeMillis() < deadline
            ) {
                val n = recorder.read(pcmBuf, 0, minBuf)
                if (n > 0) {
                    raf.write(pcmBuf, 0, n); totalBytes += n
                    var k = 0
                    while (k + 1 < n) {                // 16-bit little-endian samples
                        val sample = ((pcmBuf[k + 1].toInt() shl 8) or (pcmBuf[k].toInt() and 0xFF)).toShort().toInt()
                        peak = maxOf(peak, abs(sample))
                        k += 2
                    }
                } else if (n < 0) {
                    break                              // read error: stop instead of spinning
                }
            }

            // Back-fill the WAV header now that we know the total data size
            raf.seek(0)
            raf.write(wavHeader(totalBytes, SAMPLE_RATE))
        }
        // Speech normally peaks in the thousands. A peak near 0 means Android is giving us silence.
        Log.d(TAG, "chunk recorded: ${totalBytes / 1024} KB, peak amplitude $peak")
        return outFile
    }

    /**
     * Uploads [chunk] to the backend and shows a scam alert if the result is SCAM.
     * The file is deleted after upload regardless of outcome.
     */
    private fun analyseChunk(chunk: File, chunkIndex: Int) {
        try {
            val response = BackendClient.analyze(chunk, "audio/wav", phoneNumber, callId)
            if (BackendClient.isScam(response) && scamDetected.compareAndSet(false, true)) {
                // First chunk that triggers → show alert; subsequent chunks are ignored
                updateNotification("⚠ Scam detected! Stay on guard.")
                showAlert(response)
            } else if (BackendClient.classification(response) == "ALERT") {
                updateNotification("Suspicious call \u2013 stay careful")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            chunk.delete()
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun showAlert(response: String) {
        val alertIntent = Intent(this, ScamAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("response", response)
            putExtra("phoneNumber", phoneNumber)
        }
        val pending = PendingIntent.getActivity(
            this, 0, alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 10+ blocks activities launched straight from a background service.
        // A high-priority notification with a full-screen intent is the supported way:
        // it opens the red screen over the call (on Android 14+ the user may have to allow
        // "full-screen notifications" for this app in Settings), and otherwise shows a
        // heads-up banner with sound; tapping it opens the alert.
        val notification = NotificationCompat.Builder(this, alertChannel())
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("\u26A0 Scam call detected")
            .setContentText("Do NOT share OTP, PIN or money. Tap for details.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(2, notification)

        // Still try the direct launch too: it works when the app happens to be on screen.
        try { startActivity(alertIntent) } catch (e: Exception) { Log.w(TAG, "Direct launch blocked", e) }
    }

    /** High-importance channel (sound + heads-up) used for alerts and for "could not monitor" errors. */
    private fun alertChannel(): String {
        val id = "scam_alert"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(id, "Scam alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        return id
    }

    private fun notifyProblem(text: String) {
        val n = NotificationCompat.Builder(this, alertChannel())
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("ScamCallAI could not monitor this call")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(3, n)
    }

    private fun createNotification(text: String): Notification {
        val channelId = "scam_detection"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "Scam Detection",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ScamCallAI — Monitoring")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        if (!isRecording) return   // service already stopped: don't leave an undismissable notification
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, createNotification(text))
    }
}
