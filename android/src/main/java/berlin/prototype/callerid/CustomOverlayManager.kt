package berlin.prototype.callerid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.Call
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import berlin.prototype.callerid.db.CallerManager

@RequiresApi(Build.VERSION_CODES.N)
class CustomOverlayManager : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Settings.canDrawOverlays(context)) {
            return
        }
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            if (!isShowingOverlay) {

              val phoneNumber = callServiceNumber ?: return
              val caller = CallerManager.getCallerByNumber(phoneNumber) ?: return
              
              isShowingOverlay = true

              val parts = caller.label.split(",", limit = 2)
              val callerName = parts[0].trim()
              val callerInfo = if (parts.size > 1) parts[1].trim() else ""
              showCallerInfo(context, callerName, callerInfo)
            }
        } else if (state == TelephonyManager.EXTRA_STATE_OFFHOOK || state == TelephonyManager.EXTRA_STATE_IDLE) {
            Log.d("CustomOverlayManager", "hide overlay")
            if (isShowingOverlay) {
                isShowingOverlay = false
                callServiceNumber = null
                dismissCallerInfo(context)

            }
        }
    }

    private fun getLayoutTemplate(context: Context): Int {
        val manager: PackageManager = context.packageManager
        var resources: Resources? = null
        var layout: Int
        try {
            resources = manager.getResourcesForApplication(context.packageName)
            layout = resources.getIdentifier("caller_info_dialog", "layout", context.packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            layout = R.layout.caller_info_dialog
        }

        return layout
    }

    private fun showCallerInfo(
        context: Context,
        callerName: String,
        callerInfo: String,
    ) {
        var appName = getApplicationName(context)
        val layout = this.getLayoutTemplate(context)

        Handler(Looper.getMainLooper()).postDelayed({
          val windowManager: WindowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
          if (overlay == null) {
            val inflater: LayoutInflater = LayoutInflater.from(context)
            overlay = inflater.inflate(layout, null) as LinearLayout

            fillLayout(context, appName, callerName, callerInfo)
          }

          val typeParam: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

          val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            typeParam,
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
          )

          // TODO: replace deprecated FLAG_SHOW_WHEN_LOCKED with keyguardManager
          // this would require starting an activity which we currently haven't implemented
          // something like this:
          // val overlayIntent = Intent(context, OverlayActivity::class.java)
          // overlayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          // context?.startActivity(overlayIntent)
          //
          // OverlayActivity: Activity() {
          //  val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
          //  if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
          //    keyguardManager.requestDismissKeyguard(this, null)
          //  }
          windowManager.addView(overlay, params)
        }, 1000)
    }

    private fun fillLayout(
        context: Context,
        appName: String,
        callerName: String,
        callerInfo: String
    ) {
        try {
            val closeButton: Button = overlay!!.findViewById(R.id.close_btn)
            closeButton.setOnClickListener {
                isShowingOverlay = false
                dismissCallerInfo(context)
            }
        } catch (error: Exception) {
        }

        try {
            val CallerLabel: LinearLayout = overlay!!.findViewById<LinearLayout>(R.id.callerLabel)
            CallerLabel.setOnClickListener(View.OnClickListener {
                isShowingOverlay = false
                dismissCallerInfo(context)
            })
        } catch (error: Exception) {
        }

        try {
            // Set app name
            val textViewAppName: TextView = overlay!!.findViewById<TextView>(R.id.appName)
            textViewAppName.text = appName
        } catch (error: Exception) {
        }

        try {
            // Set caller name
            val textViewCallerName: TextView = overlay!!.findViewById<TextView>(R.id.callerName)
            textViewCallerName.text = callerName
        } catch (error: Exception) {
        }

        try {
            // Set caller info/label
            val textViewCallerInfo: TextView =
                overlay!!.findViewById<TextView>(R.id.callerInfo)
            if (callerInfo != null && callerInfo.length > 0) {
              textViewCallerInfo.text = callerInfo
            } else {
              textViewCallerInfo.text = ""
            }
        } catch (error: Exception) {
        }

        try {
            // Set app icon
            val appIconImage: ImageView = overlay!!.findViewById<ImageView>(R.id.appIcon)
            var icon: Drawable? = null
            try {
                icon = context.packageManager.getApplicationIcon(context.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                appIconImage.visibility = View.GONE
            }
            appIconImage.setImageDrawable(icon)
        } catch (error: Exception) {
        }
    }

    private fun dismissCallerInfo(context: Context) {
        if (overlay != null) {
            val windowManager: WindowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (windowManager != null) {
                windowManager.removeView(overlay)
                overlay = null
            }
        }
    }

    companion object {
        private var isShowingOverlay = false
        private var overlay: LinearLayout? = null
        var callServiceNumber: String? = null
        private fun getApplicationName(context: Context): String {
            val applicationInfo = context.applicationInfo
            val stringId = applicationInfo.labelRes
            return if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else context.getString(
                stringId
            )
        }
    }
}
