package berlin.prototype.callerid

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
import berlin.prototype.callerid.db.CallerManager

@RequiresApi(Build.VERSION_CODES.Q)
class CallDetectScreeningService : CallScreeningService() {
  override fun onScreenCall(callDetails: Call.Details) {
    // system contacts app will handle incoming calls
    if (CallerManager.workProfileAvailable) {
      return
    }

    Log.d("CallDetectScreeningService", "onScreenCall")

    if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
      val phoneNumber = callDetails.handle?.schemeSpecificPart ?: return

      // We have to make sure our number doesn't contain
      // any characters other than numbers
      val normalizedNumber = CallerManager.getNormalizedPhoneNumber(phoneNumber)

      if (CallerManager.isPhoneNumberBlocked(normalizedNumber)) {
        Log.d("CallDetectScreeningService", "$phoneNumber is blocked")

        val response = CallResponse.Builder()
          .setDisallowCall(true)
          .setRejectCall(true)
          .setSkipCallLog(false)
          .setSkipNotification(true)
          .build()
        respondToCall(callDetails, response)

        return
      }

      val response = CallResponse.Builder()
        .setDisallowCall(false)
        .setRejectCall(false)
        .setSilenceCall(false)
        .setSkipCallLog(false)
        .setSkipNotification(false)
        .build()
      
      CustomOverlayManager.callServiceNumber = normalizedNumber;

      respondToCall(callDetails, response)
    }

  }
}
