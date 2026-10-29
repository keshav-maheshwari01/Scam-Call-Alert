package com.scamcallai.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        // get call state — ringing, answered, ended
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

        // get the incoming phone number
        val incomingNumber = intent.getStringExtra(
            TelephonyManager.EXTRA_INCOMING_NUMBER
        ) ?: "unknown"

        when (state) {

            // phone is ringing — someone is calling
            TelephonyManager.EXTRA_STATE_RINGING -> {

                // is this number saved in contacts?
                if (!isKnownContact(context, incomingNumber)) {
                    // unknown number — start recording
                    val serviceIntent = Intent(context, AudioRecorderService::class.java)
                    serviceIntent.putExtra("phoneNumber", incomingNumber)
                    context.startForegroundService(serviceIntent)
                }
            }

            // call ended
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // stop recording
                val serviceIntent = Intent(context, AudioRecorderService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }

    // checks if number is saved in your contacts
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