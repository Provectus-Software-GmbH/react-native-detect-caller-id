package berlin.prototype.callerid

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import berlin.prototype.callerid.permissions.PermissionsHelper
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod


class DetectCallerIdModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    // Declare the module name
    override fun getName() = "DetectCallerId"

    // Declare android application context
    private val context = reactContext.applicationContext
    private var permissionsHelper = PermissionsHelper(reactContext)

    // Declare CallManager with the current context
    private val callManager = CallManager(context)

    @ReactMethod
    fun simulateIncomingCall(phoneNumber: String) {
        Log.d("DetectCallerIdModule", "Simulate incoming call from: $phoneNumber")
        // if the call simulation fails, try to register the phone account
        if (!callManager.simulateIncomingCall(phoneNumber))
            callManager.registerPhoneAccount()
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
