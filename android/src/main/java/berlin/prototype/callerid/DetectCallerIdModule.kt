package berlin.prototype.callerid

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import berlin.prototype.callerid.db.CallerManager
import berlin.prototype.callerid.permissions.PermissionsHelper
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import org.json.JSONException
import org.json.JSONObject

@RequiresApi(Build.VERSION_CODES.Q)
class DetectCallerIdModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
  private val context = reactContext
  private var contentProviderAvailable = false // true for default mode, false for compatibility mode
  private var workProfileAvailable = false // true for work profile mode (SyncToLocalContacts)
  private var isCallerManagerInitialized = false

  // Declare CallManager with the current context
  private val callManager = CallManager(context)
  private val permissionsHelper = PermissionsHelper(context)

    // Declare the module name
    override fun getName() = "DetectCallerId"

    override fun initialize() {
      super.initialize()

    }

  @ReactMethod
  fun init(forceAndroidMode: String = "none", promise: Promise) {
    Log.d("DetectCallerIdModule", "init: $forceAndroidMode")

    val forceWorkProfile = forceAndroidMode == "workProfileMode";
    val forceDefaultMode = forceAndroidMode == "defaultMode";
    val forceCompatibilityMode = forceAndroidMode == "compatibilityMode";

    contentProviderAvailable = forceDefaultMode || hasGenuineAndroidDefaultDialer()
    workProfileAvailable = forceWorkProfile || isInstalledOnWorkProfile()

    if (forceCompatibilityMode) {
      contentProviderAvailable = false
      workProfileAvailable = false
    }

    // Only start foreground service if we're in Samsung compatibility mode
    if (!contentProviderAvailable && !workProfileAvailable) {
      val serviceIntent = Intent(context, CallerForegroundService::class.java)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(serviceIntent)
      } else {
        context.startService(serviceIntent)
      }
    }

    promise.resolve("init completed");
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
      Log.d("DetectCallerIdModule", "getCallerIdMode")

      if (workProfileAvailable) {
        promise.resolve("workProfileMode")
      }

      if (contentProviderAvailable) {
        promise.resolve("defaultMode")
      }

      promise.resolve("compatibilityMode")
    }

    @ReactMethod
    fun syncContacts(options: ReadableMap, isVacationModeActive: Boolean = false, promise: Promise) {
      Log.d("DetectCallerIdModule", "syncContacts")

      if (!workProfileAvailable) {
        promise.reject("DetectCallerId", "Syncing contacts while not in work profile mode is not supported by this plugin")
        return
      }

      try {
        val manager = SyncContactsManager(context)
        manager.syncContacts(options, isVacationModeActive)
        promise.resolve("synced to local contacts")
      } catch (e: JSONException) {
        e.printStackTrace()
      }
    }

  @ReactMethod
  fun blockLocalContact(signature: String, promise: Promise) {
    Log.d("DetectCallerIdModule", "blockLocalContact")
    if (!workProfileAvailable) {
      promise.reject("DetectCallerId", "Blocking local contacts while not in work profile mode is not supported by this plugin")
      return
    }
    try {
      val manager = SyncContactsManager(context)
      manager.blockLocalContact(signature)
      promise.resolve("blocked local contact")
    } catch (e: JSONException) {
      e.printStackTrace()
    }
  }

  @ReactMethod
  fun unblockLocalContact(signature: String, promise: Promise) {
    Log.d("DetectCallerIdModule", "unblockLocalContact")
    if (!workProfileAvailable) {
      promise.reject("DetectCallerId", "Unblocking local contacts while not in work profile mode is not supported by this plugin")
      return
    }
    try {
      val manager = SyncContactsManager(context)
      manager.unblockLocalContact(signature)
      promise.resolve("unblocked local contact")
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

    if (isCallerManagerInitialized == false) {
      CallerManager.initialize(context, contentProviderAvailable, workProfileAvailable)
      isCallerManagerInitialized = true
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
    Log.d("DetectCallerIdModule", "clearCallerList")
    if (workProfileAvailable) {
      promise.reject("DetectCallerId", "Removing caller ids in work profile mode is not supported by this plugin")
    }

    CallerManager.clearAllCallerList()
    promise.resolve("caller list cleared")
  }

  @ReactMethod
  fun dummyCall(promise: Promise) {
    Log.d("DetectCallerIdModule", "dummyCall")
    promise.resolve("dummyCall")
  }

  @ReactMethod
  fun clearContacts(promise: Promise) {
    Log.d("DetectCallerIdModule", "clearContacts")
    val syncContactsManager = SyncContactsManager(context)
    syncContactsManager.clearContacts()
    promise.resolve("contacts cleared")
  }

  @ReactMethod
  fun ensureContactPermissions(promise: Promise) {
    Log.d("DetectCallerIdModule", "ensureContactPermissions")

    if (permissionsHelper.hasContactPermissions()) {
      promise.resolve("granted")
      return
    }

    val activity = currentActivity
    if (activity != null) {
      Log.d("DetectCallerIdModule", "ensureContactPermissions: requestPermissions")
      ActivityCompat.requestPermissions(
        activity,
        arrayOf(
          android.Manifest.permission.READ_CONTACTS,
          android.Manifest.permission.WRITE_CONTACTS
        ),
        1002 // any request code
      )
      promise.resolve("requested")
    } else {
      promise.reject("NO_ACTIVITY", "Cannot request permissions: no current activity")
    }
  }

    @ReactMethod
    fun checkPermissions(promise: Promise) {
      Log.d("DetectCallerIdModule", "checkPermissions")
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
