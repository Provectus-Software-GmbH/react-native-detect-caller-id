package berlin.prototype.callerid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import berlin.prototype.callerid.db.CallerManager

class CallerForegroundService : Service() {

    private lateinit var phoneStateReceiver: BroadcastReceiver

    override fun onCreate() {
        super.onCreate()
        Log.d("CallerForegroundService", "Service created")

        phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d("CallerForegroundService", "Phone state: $state")

                if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                    val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    Log.d("CallerForegroundService", "Incoming call from: $number")

                    if (!number.isNullOrBlank()) {
                        val normalized = CallerManager.getNormalizedPhoneNumber(number)
                        val caller = CallerManager.getCallerByNumber(normalized)

                        if (caller != null) {
                            Log.d("CallerForegroundService", "Matched caller: ${caller.label}")
                            CustomOverlayManager.callServiceNumber = normalized
                            CustomOverlayManager.showOverlay(this@CallerForegroundService, caller.label)
                        }
                    }
                }

                if (state == TelephonyManager.EXTRA_STATE_IDLE || state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                    CustomOverlayManager.dismissOverlay(this@CallerForegroundService)
                }
            }
        }

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneStateReceiver, filter)

        startForegroundWithNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(phoneStateReceiver)
        Log.d("CallerForegroundService", "Service destroyed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("CallerForegroundService", "Service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val channelId = "caller_service_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Caller ID Service", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Secure Contacts")
            .setContentText("Caller ID")
            .setSmallIcon(android.R.drawable.sym_call_missed) // Replace with your app icon
            .build()

        startForeground(999, notification)
    }
}
