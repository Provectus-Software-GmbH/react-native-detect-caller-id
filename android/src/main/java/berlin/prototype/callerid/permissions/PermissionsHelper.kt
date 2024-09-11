package berlin.prototype.callerid.permissions

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.SparseArray
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener
import com.google.gson.GsonBuilder
import org.json.JSONException
import org.json.JSONObject
import java.util.ArrayList

import android.content.Context.ROLE_SERVICE
import berlin.prototype.callerid.ReactBridgeTools.convertJsonToMap

class PermissionsHelper(private val reactContext: ReactApplicationContext) : PermissionListener, ActivityEventListener {

  private val mRequests: SparseArray<Request> = SparseArray()
  private val oRequests: SparseArray<Request> = SparseArray()
  private var mRequestCode = 1
  private var oRequestCode = 10000

  private val CHECKING_PERMISSIONS = arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)

  init {
    reactContext.addActivityEventListener(this)
  }

  fun isSystemAlertPermissionGranted(): Boolean {
    return Settings.canDrawOverlays(reactContext)
  }

  fun isPhonePermissionGranted(): Boolean {
    var result = true
    for (permission in CHECKING_PERMISSIONS) {
      result = result && ActivityCompat.checkSelfPermission(reactContext, permission) == PackageManager.PERMISSION_GRANTED
    }
    return result
  }

  fun isServicePermissionGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val roleManager = reactContext.getSystemService(ROLE_SERVICE) as RoleManager
      roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    } else {
      true
    }
  }

  fun checkPermissions(promise: Promise) {
    val overlayPermissionGranted = isSystemAlertPermissionGranted()
    val phonePermissionGranted = isPhonePermissionGranted()
    val servicePermissionGranted = isServicePermissionGranted()

    val permissionsInfo = PermissionsInfo().apply {
      this.overlayPermissionGranted = overlayPermissionGranted
      this.phonePermissionGranted = phonePermissionGranted
      this.servicePermissionGranted = servicePermissionGranted
    }

    try {
      val gson = GsonBuilder().create()
      promise.resolve(convertJsonToMap(JSONObject(gson.toJson(permissionsInfo))))
    } catch (e: JSONException) {
      promise.reject("ERROR", "JSON PARSE ERROR")
    }
  }

  fun requestPhonePermission(promise: Promise) {
    if (!isPhonePermissionGranted()) {
      try {
        val activity = getPermissionAwareActivity()

        val askingPermissions = ArrayList<String>()
        for (permission in CHECKING_PERMISSIONS) {
          if (ActivityCompat.checkSelfPermission(reactContext, permission) != PackageManager.PERMISSION_GRANTED) {
            askingPermissions.add(permission)
          }
        }

        mRequests.put(mRequestCode, Request(
          null,
          Callback { args ->
            val results = args[0] as IntArray
            val resultsForReturn = Array(askingPermissions.size) { i ->
              if (results.isNotEmpty()) {
                if (results[i] == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
              } else {
                "error"
              }
            }
            promise.resolve(Arguments.makeNativeArray<Any>(resultsForReturn))
          }
        ))

        activity.requestPermissions(askingPermissions.toTypedArray(), mRequestCode, this)
        mRequestCode++
      } catch (e: IllegalStateException) {
        promise.reject("ERROR", "INVALID ACTIVITY ERROR")
      }
    } else {
      promise.resolve("granted")
    }
  }

  fun requestOverlayPermission(promise: Promise) {
    if (!isSystemAlertPermissionGranted()) {
      try {
        oRequests.put(oRequestCode, Request(
          null,
          Callback {
            if (isSystemAlertPermissionGranted()) {
              promise.resolve("granted")
            } else {
              promise.resolve("denied")
            }
          }
        ))

        val packageName = reactContext.packageName
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        reactContext.startActivityForResult(intent, oRequestCode, null)
        oRequestCode++
      } catch (e: IllegalStateException) {
        promise.reject("ERROR", "INVALID ACTIVITY ERROR")
      }
    } else {
      promise.resolve("granted")
    }
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  fun requestServicePermission(promise: Promise) {
    if (!isServicePermissionGranted()) {
      try {
        oRequests.put(oRequestCode, Request(
          null,
          Callback {
            if (isServicePermissionGranted()) {
              promise.resolve("granted")
            } else {
              promise.resolve("denied")
            }
          }
        ))

        val roleManager = reactContext.getSystemService(ROLE_SERVICE) as RoleManager
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
        reactContext.startActivityForResult(intent, oRequestCode, null)
        oRequestCode++
      } catch (e: IllegalStateException) {
        promise.reject("ERROR", "INVALID ACTIVITY ERROR")
      }
    } else {
      promise.resolve("granted")
    }
  }

  override fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
    val request = oRequests[requestCode]
    request?.callback?.invoke()
    oRequests.remove(requestCode)
  }

  override fun onNewIntent(intent: Intent?) {
    // No-op
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
    val request = mRequests[requestCode]
    return if (request != null) {
      request.callback.invoke(grantResults, getPermissionAwareActivity(), request.rationaleStatuses)
      mRequests.remove(requestCode)
      mRequests.size() == 0
    } else {
      false
    }
  }

  private fun getPermissionAwareActivity(): PermissionAwareActivity {
    val activity = reactContext.currentActivity
    if (activity == null) {
      throw IllegalStateException("Tried to use permissions API while not attached to an Activity.")
    } else if (activity !is PermissionAwareActivity) {
      throw IllegalStateException("Tried to use permissions API but the host Activity doesn't implement PermissionAwareActivity.")
    }
    return activity
  }
}
