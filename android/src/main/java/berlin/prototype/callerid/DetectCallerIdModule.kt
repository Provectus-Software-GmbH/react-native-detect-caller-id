package berlin.prototype.callerid

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import berlin.prototype.callerid.db.CallerManager
import berlin.prototype.callerid.permissions.PermissionsHelper
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import org.json.JSONException
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.Q)
class DetectCallerIdModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val context = reactContext
  private var contentProviderAvailable = false // true for default mode, false for compatibility mode
  private var workProfileAvailable = false // true for work profile mode (SyncToLocalContacts)

  // Declare CallManager with the current context
  private val callManager = CallManager(context)
  private val permissionsHelper = PermissionsHelper(reactContext)

    // Declare the module name
    override fun getName() = "DetectCallerId"

    override fun initialize() {
      super.initialize()
      contentProviderAvailable = hasGenuineAndroidDefaultDialer()
      workProfileAvailable = isInstalledOnWorkProfile()

      // CallerManager also stores flags for contentProvider and workProfile mode
      CallerManager.initialize(context, contentProviderAvailable, workProfileAvailable)
    }

    @ReactMethod
    fun simulateIncomingCall(phoneNumber: String, promise: Promise) {
        Log.d("DetectCallerIdModule", "Simulate incoming call from: $phoneNumber")
        // if the call simulation fails, try to register the phone account
        if (!callManager.simulateIncomingCall(phoneNumber))
            callManager.registerPhoneAccount()

      promise.resolve("simulateIncomingCall with number $phoneNumber")
    }

    @ReactMethod
    fun getCallerIdMode(promise: Promise) {
      if (workProfileAvailable) {
        promise.resolve("workProfileMode")
      }

      if (contentProviderAvailable) {
        promise.resolve("defaultMode")
      }

      promise.resolve("compatibilityMode")
    }

    @ReactMethod
    fun syncContacts(options: String, isVacationModeActive: Boolean, promise: Promise) {
      if (!workProfileAvailable) {
        promise.reject("DetectCallerId", "Syncing contacts while not in work profile mode is not supported by this plugin")
        return
      }

      try {
        val manager = SyncContactsManager(context)
        manager.syncContacts(options, isVacationModeActive, promise)
      } catch (e: JSONException) {
        e.printStackTrace()
      }
    }

  @ReactMethod
  fun setCallerList(options: String, promise: Promise) {
    Log.d("DetectCallerId", "setCallerList")

    if (workProfileAvailable) {
      promise.reject("DetectCallerId", "Setting up caller ids in work profile mode is not supported by this plugin")
    }

    try {
      val jsonObject = JSONObject(options)
      CallerManager.updateCallers(jsonObject.getJSONArray("items"), jsonObject.getString("type"))

      promise.resolve("caller list updated")
    } catch (e: JSONException) {
      e.printStackTrace()
      // Reject the promise with an error message
      promise.reject("PARSE_ERROR", "Failed to parse JSON", e)
    }
  }

    @ReactMethod
    fun clearCallerList(promise: Promise) {
      if (workProfileAvailable) {
        promise.reject("DetectCallerId", "Removing caller ids in work profile mode is not supported by this plugin")
      }

      CallerManager.clearAllCallerList()
      promise.resolve("caller list cleared")
    }

    @ReactMethod
    fun checkPermissions(promise: Promise) {
      if (workProfileAvailable) {
        promise.reject("DetectCallerId", "permissions are checked by expo-contacts plugin in work profile mode")
      }

      this.permissionsHelper.checkPermissions(promise)
    }

    @ReactMethod
    fun requestPhonePermission(promise: Promise) {
      if (workProfileAvailable) {
        promise.reject("DetectCallerId", "permissions are requested by expo-contacts plugin in work profile mode")
      }

      this.permissionsHelper.requestPhonePermission(promise)
    }

    @ReactMethod
    fun requestOverlayPermission(promise: Promise) {
      if (workProfileAvailable) {
        promise.reject("DetectCallerId", "permissions are requested by expo-contacts plugin in work profile mode")
      }

      this.permissionsHelper.requestOverlayPermission(promise)
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @ReactMethod
    fun requestServicePermission(promise: Promise) {
      if (workProfileAvailable) {
        promise.reject("DetectCallerId", "permissions are requested by expo-contacts plugin in work profile mode")
      }

      this.permissionsHelper.requestServicePermission(promise)
    }

    private fun getDefaultDialer(): String {
      return try {
        val manager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager?
        manager?.defaultDialerPackage ?: ""
      } catch (ex: Exception) {
        ex.printStackTrace()
        ""
      }
    }

    private fun hasGenuineAndroidDefaultDialer(): Boolean {
      val curDefaultDialer = getDefaultDialer()

      return curDefaultDialer == "com.google.android.dialer" ||
        curDefaultDialer == "com.android.dialer"
    }

    private fun isInstalledOnWorkProfile(): Boolean {
      // Get the DevicePolicyManager service
      val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
      // Get the list of active admins
      val activeAdmins = devicePolicyManager?.activeAdmins ?: return false
      for (admin in activeAdmins) {
        // Check if the package is a profile owner app
        if (devicePolicyManager.isProfileOwnerApp(admin.packageName)) {
          return true
        }
      }
      return false
    }
}
