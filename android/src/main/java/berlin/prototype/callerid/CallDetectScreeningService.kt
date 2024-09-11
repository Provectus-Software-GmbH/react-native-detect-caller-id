package berlin.prototype.callerid

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService

class CallDetectScreeningService : CallScreeningService() {
  override fun onScreenCall(details: Call.Details) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      if (details.callDirection == Call.Details.DIRECTION_INCOMING) {
        val response = CallResponse.Builder()
          .setDisallowCall(false)
          .setRejectCall(false)
          .setSilenceCall(false)
          .setSkipCallLog(false)
          .setSkipNotification(false)
          .build()

        CallReceiver.callServiceNumber = details.handle.schemeSpecificPart
        respondToCall(details, response)
      }
    }
  }
}
