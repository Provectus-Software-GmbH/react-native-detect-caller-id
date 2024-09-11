package berlin.prototype.callerid

import android.telecom.Connection
import android.telecom.DisconnectCause

// VoipConnection class for handling VoIP connections
class VoipConnection : Connection() {

    // Overrides the onAnswer method to handle the answer action
    override fun onAnswer() {
        super.onAnswer()
        // reject the call
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
    }

    // Overrides the onReject method to handle the reject action
    override fun onReject() {
        super.onReject()
        // reject the call
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
    }
}
