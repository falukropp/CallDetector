package se.falukropp.calldetector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

// Based on https://stackoverflow.com/questions/15563921/how-to-detect-incoming-calls-in-an-android-device
class CallDetectorReceiver : BroadcastReceiver() {

    private val TAG = "MyActivity"

    private val LAST_STATE_KEY = "LAST_STATE"

    override fun onReceive(context: Context, intent: Intent) {

        val stateStr = intent.getExtras().getString(TelephonyManager.EXTRA_STATE)
        // val number = intent.getExtras().getString(TelephonyManager.EXTRA_INCOMING_NUMBER)

        var state = when (stateStr) {
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            else -> return
        }
        val sharedPreferencesKey = context.resources.getString(R.string.preference_file_key)
        val sharedPreferences = context.getSharedPreferences(sharedPreferencesKey, Context.MODE_PRIVATE)

        val lastState = sharedPreferences.getInt(LAST_STATE_KEY, TelephonyManager.CALL_STATE_IDLE)


        if (state == lastState) {
            return
        }

        // TODO: Check SSID.

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // IT'S RINGING!!!!
                Log.d(TAG, "Ringing")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // I'VE ANSWERED !!!! STOP BLINKING!!!
                Log.d(TAG, "Answered")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // It's stopped ringing. Did I answer?
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    // Oh no! Missed call!
                    Log.d(TAG, "Missed")
                }
            }
        }

        with (sharedPreferences.edit()) {
            putInt(LAST_STATE_KEY, state)
            commit()
        }

    }

}