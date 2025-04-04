package berlin.prototype.callerid

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import berlin.prototype.callerid.db.CallerManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device rebooted, initializing caller ID system")

            val contentProviderAvailable = hasGenuineAndroidDefaultDialer(context)
            val workProfileAvailable = isInstalledOnWorkProfile(context)

            if (workProfileAvailable) return

            if (contentProviderAvailable) {
                CallerManager.initialize(context)
            } else {
                val serviceIntent = Intent(context, CallerForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }

    private fun hasGenuineAndroidDefaultDialer(context: Context): Boolean {
        val curDefaultDialer = getDefaultDialer(context)
        return curDefaultDialer == "com.google.android.dialer" ||
                curDefaultDialer == "com.android.dialer"
    }

    private fun isInstalledOnWorkProfile(context: Context): Boolean {
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val activeAdmins = devicePolicyManager?.activeAdmins ?: return false

        for (admin in activeAdmins) {
            if (devicePolicyManager.isProfileOwnerApp(admin.packageName)) {
                return true
            }
        }
        return false
    }

    private fun getDefaultDialer(context: Context): String {
        return try {
            val manager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            manager?.defaultDialerPackage ?: ""
        } catch (ex: Exception) {
            ex.printStackTrace()
            ""
        }
    }
}