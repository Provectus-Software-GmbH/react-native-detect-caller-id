package berlin.prototype.callerid

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import android.widget.Toast
// CallManager class for managing calls
class CallManager(context: Context) {
    // Declare a context
    private val context = context.applicationContext
    // Declare a service ID <- display name
    private val serviceID = "Secure Contacts"
    // Declare an instance of TelecomManager
    private var telecomManager: TelecomManager? = null
    // Declare an instance of PhoneAccountHandle
    private var phoneAccountHandle: PhoneAccountHandle? = null

    // Method to simulate an incoming call
    fun simulateIncomingCall(phoneNumber: String?): Boolean {
        // Guard
        if (phoneNumber.isNullOrEmpty()) return false
        // Create a bundle to store the call information > from phone number
        val callInfo = Bundle().apply {
            putString("from", phoneNumber)
        }
        try {
            val componentName = ComponentName(context, CustomConnectionService::class.java)
            phoneAccountHandle = PhoneAccountHandle(componentName, serviceID)
            if (checkAccountConnection()) {
                // Add new incoming call to the TelecomManager
                telecomManager?.addNewIncomingCall(phoneAccountHandle, callInfo)
                // Display a toast message
                Toast.makeText(context, "please wait, call incoming...", Toast.LENGTH_LONG).show()
                return true
            }
        } catch (ex: Exception) {
            Log.e("CallManager", ex.message ?: "Unknown error")
        }
        return false
    }

    // Method to register the required phone account
    fun registerPhoneAccount() {
        telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager?
        val componentName = ComponentName(context, CustomConnectionService::class.java)
        phoneAccountHandle = PhoneAccountHandle(componentName, serviceID)

        val phoneAccount = PhoneAccount.builder(phoneAccountHandle, serviceID)
            .setCapabilities(PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
            .build()

        try {
            // Could be inaccessible due to Intune App Protection Policy (!)
            // Allow "com.android.server.telecom" in APP > Apps to Exempt
            telecomManager?.registerPhoneAccount(phoneAccount)
            val intent = Intent().apply {
                component = ComponentName(
                    "com.android.server.telecom",
                    "com.android.server.telecom.settings.EnableAccountPreferenceActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            Log.e("CallManager", ex.message ?: "Unknown error")
        }
    }

    // Check if the phone account is connected
    fun checkAccountConnection(): Boolean {
        telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        Log.d("CallManager", "checkAccountConnection")
        // Guard
        if (telecomManager == null) return false
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return false

        Log.d("CallManager", "checkSelfPermission granted")

        // Get the list of call-capable phone accounts
        val enabledAccounts = telecomManager?.callCapablePhoneAccounts ?: return false
        val customConnectionServiceName = CustomConnectionService::class.java.canonicalName


        Log.d("CallManager", "enabled accounts_ " + enabledAccounts.size.toString())

        for (account in enabledAccounts) {
            if (account.componentName.className == customConnectionServiceName) return true
        }

        return false
    }
}
