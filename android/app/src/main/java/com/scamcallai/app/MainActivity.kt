package com.scamcallai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 100

    // Required permissions for core functionality
    private val corePermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val testBtn = findViewById<Button>(R.id.testButton)
        testBtn.setOnClickListener { sendTestAudio() }

        // Settings button: opens app-level permission settings so users can
        // manually grant permissions they previously denied permanently.
        val settingsBtn = findViewById<Button>(R.id.settingsButton)
        settingsBtn.setOnClickListener { openAppSettings() }

        askPermissions()
    }

    override fun onResume() {
        super.onResume()
        // Re-check on resume — user may have come back from the Settings screen
        if (allCorePermissionsGranted()) showReady() else showError()
    }

    // ─────────────────────────────────────────────────
    //  Permission helpers
    // ─────────────────────────────────────────────────

    private fun allCorePermissionsGranted() = corePermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun askPermissions() {
        val toRequest = corePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toMutableList()

        // Android 13+: notification permission (nice-to-have, not blocking)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (toRequest.isEmpty()) {
            showReady()
        } else {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (allCorePermissionsGranted()) showReady() else showError()
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        )
    }

    // ─────────────────────────────────────────────────
    //  UI state helpers
    // ─────────────────────────────────────────────────

    private fun showReady() {
        val ring = findViewById<View>(R.id.shieldRing)
        val status = findViewById<TextView>(R.id.heroStatusText)
        val subtext = findViewById<TextView>(R.id.heroSubText)

        status.text = "Active — You're Protected"
        subtext.text = "ScamCallAI is monitoring calls in the background."
        setRingColor(ring, 0xFF00C853.toInt()) // green
    }

    private fun showError() {
        val ring = findViewById<View>(R.id.shieldRing)
        val status = findViewById<TextView>(R.id.heroStatusText)
        val subtext = findViewById<TextView>(R.id.heroSubText)

        status.text = "Permissions Required"
        subtext.text = "Tap \"Fix Permissions\" below to grant all required permissions."
        setRingColor(ring, 0xFFF44336.toInt()) // red
    }

    /** Recolours only the ring's outline, so the dark fill from shield_ring.xml is kept. */
    private fun setRingColor(view: View, color: Int) {
        (view.background.mutate() as GradientDrawable)
            .setStroke((3 * resources.displayMetrics.density).toInt(), color)
    }

    // ─────────────────────────────────────────────────
    //  Test scam detection
    // ─────────────────────────────────────────────────

    private fun sendTestAudio() {
        val status = findViewById<TextView>(R.id.heroStatusText)
        val subtext = findViewById<TextView>(R.id.heroSubText)

        // Check if the test asset exists before trying
        val assetList = assets.list("") ?: emptyArray()
        if ("test_scam.mpeg" !in assetList) {
            status.text = "Test file missing"
            subtext.text = "Place test_scam.mpeg in the app/src/main/assets/ folder."
            return
        }

        status.text = "Sending audio for analysis…"
        subtext.text = "This may take a few seconds."

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
                        // Restore ready state after returning from alert
                        if (allCorePermissionsGranted()) showReady()
                    } else {
                        status.text = "Test FAILED — scam sample was not detected"
                        subtext.text = "Response: $response"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Test Error"
                    subtext.text = e.message ?: "Unknown error"
                }
            }
        }.start()
    }
}