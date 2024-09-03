package ru.nasvyazi.callerid;

import android.os.Build;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;


public class CallDetectScreeningService extends CallScreeningService {
  @Override
  public void onScreenCall(Call.Details details) {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      if(details.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
        CallResponse.Builder response = new CallResponse.Builder();
        response.setDisallowCall(false);
        response.setRejectCall(false);
        response.setSilenceCall(false);
        response.setSkipCallLog(false);
        response.setSkipNotification(false);

        CallReceiver.callServiceNumber = details.getHandle().getSchemeSpecificPart();
        respondToCall(details, response.build());

      }
    }
  }
}
