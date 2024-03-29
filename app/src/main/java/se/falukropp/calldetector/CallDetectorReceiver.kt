package se.falukropp.calldetector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.telephony.TelephonyManager
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


// Based on https://stackoverflow.com/questions/15563921/how-to-detect-incoming-calls-in-an-android-device
class CallDetectorReceiver : BroadcastReceiver() {

    private val tag = "MyActivity"

    private val lastStateKey = "LAST_STATE"

    private fun getStateFromIntent(intent: Intent): Int? {
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

        val lastState = sharedPreferences.getInt(lastStateKey, TelephonyManager.CALL_STATE_IDLE)

        if (state == lastState
            || !approvedNetwork(sharedPreferences, context)
            || !approvedTime(sharedPreferences)
        ) {
            return
        }

        with(sharedPreferences.edit()) {
            putInt(lastStateKey, state)
            commit()
        }

        if (!sharedPreferences.getBoolean("enable_call_detect", true)) {
            return
        }

        // TODO : Should probably select server from which network your on.
        val serverAddress = sharedPreferences.getString("server_address", "http://192.168.0.103") ?: return
        // TODO: Should probably be enforced in preferences or something. Might support other protocols than http/https later on though.
        val server = if (serverAddress.startsWith("http")) {
            serverAddress
        } else {
            "http://${serverAddress}"
        }
        val id = sharedPreferences.getString("id", "main_phone") ?: return

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                // IT'S RINGING!!!!
                Log.d(tag, "Ringing")
                changeStatus(server, id, "ringing")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // I'VE ANSWERED !!!! STOP BLINKING!!!
                Log.d(tag, "Answered")
                changeStatus(server, id, "answered")
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // It's stopped ringing. Did I answer?
                if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                    // Oh no! Missed call!
                    Log.d(tag, "Missed")
                    changeStatus(server, id, "missed")
                }
            }
        }

    }

    // TODO: Allow multiple networks. The network would then also select the server.
    private fun approvedNetwork(sharedPreferences: SharedPreferences, context: Context): Boolean {
        val ssid = sharedPreferences.getString("ssid", "AndroidWifi")

        val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        // For some bizarre reason the network SSID might have "" around it.
        return wifiInfo.supplicantState == SupplicantState.COMPLETED && wifiInfo.ssid.replace("\"", "") == ssid
    }

    private fun getTimeToday(hoursAndMinutes: String?): Date? {
        hoursAndMinutes ?: return null

        val parser = SimpleDateFormat("HH:mm")
        val calHoursAndMinutes = Calendar.getInstance()
        try {
            val time = parser.parse(hoursAndMinutes)
            calHoursAndMinutes.time = time
        } catch (e: ParseException) {
            return null
        }

        val calToday = Calendar.getInstance()
        calHoursAndMinutes.set(
            calToday.get(Calendar.YEAR),
            calToday.get(Calendar.MONTH),
            calToday.get(Calendar.DAY_OF_MONTH)
        )
        return calHoursAndMinutes.time
    }

    private fun approvedTime(sharedPreferences: SharedPreferences): Boolean {
        val earliestAlarmToday = getTimeToday(sharedPreferences.getString("earliest", "07:00")) ?: return false
        val latestAlarmToday = getTimeToday(sharedPreferences.getString("latest", "23:00")) ?: return false
        val now = Date()
        return if (earliestAlarmToday.after(latestAlarmToday)) {
            now.before(latestAlarmToday) || !now.before(earliestAlarmToday)
        } else {
            !(earliestAlarmToday.after(now) || latestAlarmToday.before(now))
        }

    }

    private fun changeStatus(server: String, id: String, status: String) {
        AsyncTask.execute {
            // TODO : Should use better protocol for this... MQTT?
            val serverEndpoint = URL("$server/phone/$id")
            val serverConnection = serverEndpoint.openConnection() as HttpURLConnection
            serverConnection.requestMethod = "PATCH"
            serverConnection.doOutput = true
            serverConnection.setRequestProperty("Content-Type", "application/merge-patch+json")
            val outStream = serverConnection.outputStream
            val outStreamWriter = OutputStreamWriter(outStream, "UTF-8")
            outStreamWriter.write("""{"status" : "$status"}""")
            outStreamWriter.flush()
            outStreamWriter.close()
            outStream.close()

            serverConnection.connect()

            if (serverConnection.responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                Log.d(tag, "Got error contacting server : ${serverConnection.responseCode}")
            }
        }
    }

}