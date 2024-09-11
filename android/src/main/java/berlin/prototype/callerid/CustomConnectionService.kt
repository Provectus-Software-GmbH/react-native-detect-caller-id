package berlin.prototype.callerid

import android.telecom.DisconnectCause
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log
import android.net.Uri

// CustomConnectionService class for handling incoming calls
class CustomConnectionService : ConnectionService() {
    private var connection: VoipConnection? = null

    // Overrides the onCreateIncomingConnection method to handle incoming calls
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle,
        request: ConnectionRequest
    ): Connection {
        // Calls the base class (ConnectionService) implementation of the onCreateIncomingConnection method to provide a default connection
        super.onCreateIncomingConnection(connectionManagerPhoneAccount, request)
        // Extract PhoneNumber
        val phoneNumber = request.extras.getString("from")
        // Creates a new instance of the VoipConnection class and sets connection properties
        connection = VoipConnection().apply {
            connectionProperties = Connection.PROPERTY_SELF_MANAGED
        }

        try {
            // Sets the call address to "tel:..." and the presentation to allowed
            connection?.setAddress(Uri.parse("tel:$phoneNumber"), 1)
            // Sets the call state to "ringing"
            connection?.setRinging()
            // Sets the call state to "initializing"
            connection?.setInitializing()
            // Sets the call state to "active"
            connection?.setActive()
        } catch (ex: Exception) {
            Log.e("CallConnectionService", ex.message ?: "Unknown error")
        }

        // Returns the created custom connection
        return connection ?: Connection.createFailedConnection(DisconnectCause(DisconnectCause.ERROR))
    }

    // Overrides the onDestroy method to handle the destruction of the connection
    override fun onDestroy() {
        super.onDestroy()
        connection = null
    }
}
