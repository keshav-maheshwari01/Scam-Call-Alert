package com.scamcallai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        askPermissions()

        val testBtn = findViewById<Button>(R.id.testButton)
        testBtn.setOnClickListener { sendTestAudio() }
    }

    private fun sendTestAudio() {
        val status = findViewById<TextView>(R.id.statusText)
        status.text = "Sending audio to Flask..."

        Thread {
            try {
                val testFile = File(cacheDir, "test_scam.mpeg")
                assets.open("test_scam.mpeg").use { input ->
                    testFile.outputStream().use { output -> input.copyTo(output) }
                }

                val response = BackendClient.analyze(testFile, "audio/mpeg")

                runOnUiThread {
                    if (BackendClient.isScam(response)) {
                        val alertIntent = Intent(this, ScamAlertActivity::class.java)
                        alertIntent.putExtra("response", response)
                        alertIntent.putExtra("phoneNumber", "TEST CALL")
                        startActivity(alertIntent)
                    } else {
                        status.text = "Result: $response"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Error: ${e.message}"
                }
            }
        }.start()
    }

    private fun askPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        // Android 13+: ask for notifications too (nice to have, not required for "ready")
        val toRequest = notGranted.toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), PERMISSION_CODE)
        } else {
            showReady()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            val required = permissions.indices.filter {
                permissions[it] != Manifest.permission.POST_NOTIFICATIONS
            }
            if (required.all { grantResults[it] == PackageManager.PERMISSION_GRANTED }) {
                showReady()
            } else {
                showError()
            }
        }
    }

    private fun showReady() {
        val status = findViewById<TextView>(R.id.statusText)
        status.text = "ScamCallAI is Active! Protecting you from scam calls!"
        status.setBackgroundColor(android.graphics.Color.parseColor("#1B5E20"))
    }

    private fun showError() {
        val status = findViewById<TextView>(R.id.statusText)
        status.text = "Permissions denied! Please allow all permissions."
        status.setBackgroundColor(android.graphics.Color.parseColor("#B71C1C"))
    }
}