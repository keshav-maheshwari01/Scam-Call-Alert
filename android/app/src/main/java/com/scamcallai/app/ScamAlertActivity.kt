
package com.scamcallai.app

import android.annotation.SuppressLint
import android.content.*
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ScamAlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_ScamCallAI_Alert)
        setContentView(R.layout.activity_scam_alert)

        val response    = intent.getStringExtra("response")    ?: ""
        val phoneNumber = intent.getStringExtra("phoneNumber") ?: "Unknown"

        // Populate dynamic fields
        findViewById<TextView>(R.id.alertPhoneNumber).text = phoneNumber

        // Wire buttons
        findViewById<Button>(R.id.endCallButton).setOnClickListener  { finish() }
        findViewById<Button>(R.id.ignoreButton).setOnClickListener   { finish() }

        vibrate()
    }

    @SuppressLint("MissingPermission")
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500, 200, 500),
                    -1
                )
            )
        }
    }
}