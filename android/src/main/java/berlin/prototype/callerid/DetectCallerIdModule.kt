package berlin.prototype.callerid

import android.os.Build
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

  // Declare CallManager with the current context
  private val callManager = CallManager(context)
  private val permissionsHelper = PermissionsHelper(reactContext)

    // Declare the module name
    override fun getName() = "DetectCallerId"

    override fun initialize() {
      super.initialize()
      CallerManager.initialize(context)
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
    fun setCallerList(options: String, promise: Promise) {
      try {
        val jsonObject = JSONObject(options)
        CallerManager.updateCallers(jsonObject.getJSONArray("items"), jsonObject.getString("type"))

        //promise.resolve(convertJsonToMap(jsonObject))
        promise.resolve("caller list updated")
      } catch (e: JSONException) {
        e.printStackTrace()
        // Reject the promise with an error message
        promise.reject("PARSE_ERROR", "Failed to parse JSON", e)
      }
    }

    @ReactMethod
    fun clearCallerList(promise: Promise) {
      CallerManager.clearAllCallerLists()
      promise.resolve("caller list cleared")

    }

    @ReactMethod
    fun checkPermissions(promise: Promise) {
      this.permissionsHelper.checkPermissions(promise)
    }

    @ReactMethod
    fun requestPhonePermission(promise: Promise) {
      this.permissionsHelper.requestPhonePermission(promise)
    }

    @ReactMethod
    fun requestOverlayPermission(promise: Promise) {
      this.permissionsHelper.requestOverlayPermission(promise)
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @ReactMethod
    fun requestServicePermission(promise: Promise) {
      this.permissionsHelper.requestServicePermission(promise)
    }
}
