
package com.scamcallai.app

import android.annotation.SuppressLint
import android.content.*
import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ScamAlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // get data from AudioRecorderService
        val response = intent.getStringExtra("response") ?: ""
        val phoneNumber = intent.getStringExtra("phoneNumber") ?: "Unknown"

        // vibrate phone
        vibrate()

        // show alert screen
        showAlert(phoneNumber, response)
    }

    @SuppressLint("MissingPermission")
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        // vibrate pattern — 3 times
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 500, 200, 500, 200, 500),
                    -1
                )
            )
        }
    }

    private fun showAlert(phoneNumber: String, response: String) {

        // create full screen red layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(android.graphics.Color.parseColor("#B71C1C"))
        }

        // warning emoji
        val emoji = TextView(this).apply {
            text = "🚨"
            textSize = 80f
            gravity = android.view.Gravity.CENTER
        }

        // main warning text
        val title = TextView(this).apply {
            text = "SCAM CALL DETECTED!"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            setPadding(0, 20, 0, 10)
        }

        // phone number
        val numberText = TextView(this).apply {
            text = "From: $phoneNumber"
            textSize = 18f
            setTextColor(android.graphics.Color.parseColor("#FFCDD2"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }

        // warning message
        val warning = TextView(this).apply {
            text = "⚠️ Do NOT share OTP\n⚠️ Do NOT share Aadhaar/PAN\n⚠️ Do NOT transfer money"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        // end call button
        val endCallBtn = Button(this).apply {
            text = "END CALL NOW"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(android.graphics.Color.parseColor("#7F0000"))
            setPadding(40, 20, 40, 20)
        }
        endCallBtn.setOnClickListener {
            finish()
        }

        // ignore button
        val ignoreBtn = Button(this).apply {
            text = "Ignore Warning"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#FFCDD2"))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        ignoreBtn.setOnClickListener {
            finish()
        }

        // add all views to layout
        layout.addView(emoji)
        layout.addView(title)
        layout.addView(numberText)
        layout.addView(warning)
        layout.addView(endCallBtn)
        layout.addView(ignoreBtn)

        setContentView(layout)
    }
}