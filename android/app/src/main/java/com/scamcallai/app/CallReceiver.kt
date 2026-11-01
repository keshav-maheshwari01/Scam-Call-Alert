package com.scamcallai.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS = "scamcallai_prefs"
        private const val KEY_PENDING_NUMBER = "pending_number"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        when (state) {

            // Phone is ringing — save the number so we have it when the call is answered
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val incomingNumber = intent.getStringExtra(
                    TelephonyManager.EXTRA_INCOMING_NUMBER
                ) ?: "unknown"
                prefs.edit().putString(KEY_PENDING_NUMBER, incomingNumber).apply()
            }

            // Call answered — NOW there is actual audio; start recording only for unknown numbers
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val number = prefs.getString(KEY_PENDING_NUMBER, null) ?: return
                if (!isKnownContact(context, number)) {
                    val serviceIntent = Intent(context, AudioRecorderService::class.java)
                    serviceIntent.putExtra("phoneNumber", number)
                    context.startForegroundService(serviceIntent)
                }
            }

            // Call ended — stop recording and clear saved number
            TelephonyManager.EXTRA_STATE_IDLE -> {
                prefs.edit().remove(KEY_PENDING_NUMBER).apply()
                context.stopService(Intent(context, AudioRecorderService::class.java))
            }
        }
    }

    /** Returns true if [number] is saved in the user's contacts. */
    private fun isKnownContact(context: Context, number: String): Boolean {
        val uri = android.net.Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(number)
        )
        val cursor = context.contentResolver.query(
            uri, arrayOf("display_name"), null, null, null
        )
        val isKnown = (cursor?.count ?: 0) > 0
        cursor?.close()
        return isKnown
    }
}