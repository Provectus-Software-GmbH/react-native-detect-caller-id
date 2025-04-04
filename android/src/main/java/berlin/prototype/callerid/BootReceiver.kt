package berlin.prototype.callerid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import berlin.prototype.callerid.db.CallerManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted, initializing caller ID system")

            // Example: preload contacts, warm caches, request overlay permissions if needed
            CallerManager.initialize(context)

            // You could also register a listener or pre-load logic if needed
        }
    }
}