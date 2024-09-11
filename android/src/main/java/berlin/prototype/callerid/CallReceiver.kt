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
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import berlin.prototype.callerid.db.User


interface GetCallerHandler {
    fun onGetCaller(user: User?)
}

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Settings.canDrawOverlays(context)) {
            return
        }
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            if (!isShowingOverlay) {
                var phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (phoneNumber == null) {
                    phoneNumber = callServiceNumber
                }

                if (phoneNumber == null) {
                    return
                }
                isShowingOverlay = true

                getCallerName(context, phoneNumber, object : GetCallerHandler {
                    override fun onGetCaller(user: User?) {
                        if (user != null) {
                            val callerName: String = user.fullName
                            val callerAppointment: String = user.appointment
                            val callerCity: String = user.city

                            showCallerInfo(context, callerName, callerAppointment, callerCity)
                        }
                    }
                })
            }
        } else if (state == TelephonyManager.EXTRA_STATE_OFFHOOK || state == TelephonyManager.EXTRA_STATE_IDLE) {
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
        callerAppointment: String,
        callerCity: String
    ) {
        var appName = ""
        appName = getApplicationName(context)
        val finalAppName = appName
        val layout = this.getLayoutTemplate(context)

        Handler().postDelayed({
            val windowManager: WindowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (overlay == null) {
                val inflater: LayoutInflater = LayoutInflater.from(context)
                overlay = inflater.inflate(layout, null) as LinearLayout



                fillLayout(context, finalAppName, callerName, callerAppointment, callerCity)
            }
            val typeParam: Int =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

            val params: WindowManager.LayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                typeParam,
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(overlay, params)
        }, 1000)
    }

    private fun fillLayout(
        context: Context,
        finalAppName: String,
        callerName: String,
        callerAppointment: String?,
        callerCity: String?
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
            textViewAppName.text = finalAppName
        } catch (error: Exception) {
        }

        try {
            // Set caller name
            val textViewCallerName: TextView = overlay!!.findViewById<TextView>(R.id.callerName)
            textViewCallerName.text = callerName
        } catch (error: Exception) {
        }

        try {
            // Set caller appointment
            val textViewCallerAppointment: TextView =
                overlay!!.findViewById<TextView>(R.id.callerAppointment)
            if (callerAppointment != null && callerAppointment.length > 0) {
                textViewCallerAppointment.text = callerAppointment
            } else {
                textViewCallerAppointment.visibility = View.GONE
            }
        } catch (error: Exception) {
        }

        try {
            // Set caller name
            val textViewCallerCity: TextView = overlay!!.findViewById<TextView>(R.id.callerCity)
            if (callerCity != null && callerCity.length > 0) {
                textViewCallerCity.text = callerCity
            } else {
                textViewCallerCity.visibility = View.GONE
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

    private fun getCallerName(
        context: Context,
        phoneNumberInString: String,
        getCallerNameHandler: GetCallerHandler
    ) {
        try {
            var correctedPhoneNumber = phoneNumberInString
            if (correctedPhoneNumber != null && correctedPhoneNumber[0] == '+') {
                correctedPhoneNumber = correctedPhoneNumber.substring(1)
            }

            val phoneForSearch = correctedPhoneNumber

          Log.d("CallReceiver", "get caller info for $phoneForSearch");

          val dummyUser = User()

          dummyUser.id = "dummyId"
          dummyUser.number = phoneForSearch
          dummyUser.fullName = "Max Mustermann"
          dummyUser.appointment = "Developer"
          dummyUser.city = "Berlin"

          getCallerNameHandler.onGetCaller(dummyUser);
        } catch (err: Exception) {
            Log.i("CALLER_ID", err.localizedMessage)
            getCallerNameHandler.onGetCaller(null)
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
