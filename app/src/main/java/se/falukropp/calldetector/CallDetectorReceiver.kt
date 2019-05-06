package se.falukropp.calldetector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.preference.PreferenceManager
import java.net.HttpURLConnection
import java.net.URL


// Based on https://stackoverflow.com/questions/15563921/how-to-detect-incoming-calls-in-an-android-device
class CallDetectorReceiver : BroadcastReceiver() {

    private val TAG = "MyActivity"

    private val LAST_STATE_KEY = "LAST_STATE"

    private fun getStateFromIntent(intent: Intent) : Int? {
        // val number = intent.getExtras().getString(TelephonyManager.EXTRA_INCOMING_NUMBER)

        return when (intent.extras?.getString(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            else -> null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {

        // val number = intent.getExtras().getString(TelephonyManager.EXTRA_INCOMING_NUMBER)

        val state = getStateFromIntent(intent) ?: return

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context /* Activity context */)

        val lastState = sharedPreferences.getInt(LAST_STATE_KEY, TelephonyManager.CALL_STATE_IDLE)

        if (state == lastState) {
            return
        }

        if (!checkSsid(sharedPreferences, context)) {
            return
        }


        val server = sharedPreferences.getString("server", "http://192.168.0.103") ?: return
        val id = "1" // Phonenumber, probably?

        // TODO: These should really change the state of this resource on the server, i.e. the should basically say
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // IT'S RINGING!!!!
                Log.d(TAG, "Ringing")
                changeStatus(server, id, "ringing")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // I'VE ANSWERED !!!! STOP BLINKING!!!
                Log.d(TAG, "Answered")
                changeStatus(server, id, "answered")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // It's stopped ringing. Did I answer?
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    // Oh no! Missed call!
                    Log.d(TAG, "Missed")
                    changeStatus(server, id, "answered")
                }
            }
        }

        with(sharedPreferences.edit()) {
            putInt(LAST_STATE_KEY, state)
            commit()
        }

    }

    private fun checkSsid(sharedPreferences: SharedPreferences, context: Context): Boolean {
        val ssid = sharedPreferences.getString("ssid", "test_ssid")

        val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo.supplicantState != SupplicantState.COMPLETED && wifiInfo.ssid == ssid
    }

    private fun changeStatus(server : String, id : String, status : String) {
        val githubEndpoint = URL("$server/phone/$id/status")
        val serverConnection = githubEndpoint.openConnection() as HttpURLConnection
        serverConnection.requestMethod = "POST" // TODO: More properly PATCH?

        if (serverConnection.responseCode != 200) {
            Log.d(TAG, "Got error contacting server : ${serverConnection.responseCode}")
        }
    }

}