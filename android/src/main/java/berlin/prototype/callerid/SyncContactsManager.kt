package berlin.prototype.callerid

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import berlin.prototype.callerid.permissions.PermissionsHelper
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReadableMap
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

const val MAX_OPS_PER_BATCH = 100

class SyncContactsManager(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val context = reactContext
    private var SCAGroupID: Long = -1L        // Global group ID. Set by getOrCreateContactGroupId.
    private var progressCallback: ((Int) -> Unit)? = null
    private val permissionsHelper = PermissionsHelper(reactContext)

    // Declare the module name
    override fun getName() = "SyncContactsManager"

    companion object {
        const val CONTACT_GROUP_NAME = "SCA"
        const val NOTIFICATION_CHANNEL_ID = "sync_contacts_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Contact Sync"
        const val NOTIFICATION_ID = 101
    }

    fun syncContacts(options: ReadableMap, isVacationModeActive: Boolean, promise: Promise) {
      Log.d("SyncContactsManager", "syncContacts")

        if (!permissionsHelper.hasContactPermissions()) {
            promise.reject("NO_ACTIVITY", "Cannot request permissions: no active activity.")
            return
        }

        try {
            val json = (options.toHashMap() as Map<*, *>?)?.let { JSONObject(it) }
            val items: JSONArray = json?.getJSONArray("items") ?: return
            // val type: String = json.getString("type")

            // Start the foreground service to prevent process death during long operations.
            val serviceIntent = Intent(context, SyncContactsForegroundService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)

            // Run the sync on a background thread.
            Thread {
                try {
                    // Create or get the SCA group ID.
                  SCAGroupID = getOrCreateContactGroupId(CONTACT_GROUP_NAME)
                  Log.d("SyncContactsManager", "Assigned group ID: $SCAGroupID")

                    // Create a dictionary (map) to hold contacts to insert.
                    // Key: Unique source identifier; Value: Contact model.
                    val contactsToInsert = mutableMapOf<String, Contact>()

                    val gson = Gson()
                    // Process each contact JSON object.
                    for (i in 0 until items.length()) {
                        val contactJson = items.getJSONObject(i)
                        val protoContact = gson.fromJson(contactJson.toString(), IProtoContact::class.java)
                        val contact = transform(protoContact, isVacationModeActive)
                        contactsToInsert[protoContact.guid] = contact
                    }

                    val existingHashes = getExistingIhashesInGroup(SCAGroupID)
                    val newHashes = contactsToInsert.keys

                    val toUpdate = existingHashes.intersect(newHashes)
                    val toInsert = contactsToInsert.filterKeys { it !in existingHashes }
                    val toDelete = existingHashes.subtract(newHashes)
                    Log.d("SyncContactsManager", "toUpdate: ${toUpdate.size}, toInsert: ${toInsert.size}, toDelete: ${toDelete.size}")

                    deleteContactsBySourceIds(toDelete)
                    updateSendToVoicemailFlag(toUpdate, isVacationModeActive)

                    if (isVacationModeActive) {
                      val favoriteIhashes = contactsToInsert
                        .filter { !it.value.sendToVoicemail }
                        .map { it.key }
                        .toSet()
                      updateSendToVoicemailFlag(favoriteIhashes, false)
                    }

                    // Insert the contacts in batch with progress updates.
                    insertContacts(toInsert, context.contentResolver, context)

                    // Stop the foreground service when done.
                    context.stopService(serviceIntent)
                    promise.resolve("synced to local contacts")
                } catch (e: Exception) {
                    e.printStackTrace()
                    promise.reject("SYNC_ERROR", "Failed to sync contacts", e)
                }
            }.start()
        } catch (e: JSONException) {
            e.printStackTrace()
            promise.reject("PARSE_ERROR", "Failed to parse JSON", e)
        }
    }

  fun clearContacts() {
    Log.d("SyncContactsManager", "clearContacts: Clearing all contacts in group $SCAGroupID")

    if (SCAGroupID == -1L) {
      Log.e("SyncContactsManager", "SCAGroupID is not set. Cannot clear contacts.")
      return
    }

    val contentResolver = context.contentResolver
    val rawContactIds = mutableSetOf<Long>()

    // Step 1: Query RAW_CONTACT_IDs that belong to the target group
    val groupCursor = contentResolver.query(
      ContactsContract.Data.CONTENT_URI,
      arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
      "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
      arrayOf(
        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
        SCAGroupID.toString()
      ),
      null
    )

    groupCursor?.use {
      while (it.moveToNext()) {
        val rawId = it.getLong(0)
        rawContactIds.add(rawId)
      }
    }

    if (rawContactIds.isEmpty()) {
      Log.d("SyncContactsManager", "No contacts found in group $SCAGroupID to delete.")
      return
    }

    // Step 2: Delete contacts in batches to optimize performance
    val batchSize = MAX_OPS_PER_BATCH // Adjust based on testing (e.g., 500 or 1000 for larger datasets)
    val ops = mutableListOf<ContentProviderOperation>()
    var deletedCount = 0

    val serviceIntent = Intent(context, SyncContactsForegroundService::class.java)
    ContextCompat.startForegroundService(context, serviceIntent)

    rawContactIds.chunked(batchSize).forEachIndexed { batchIndex, chunk ->
      ops.clear()

      chunk.forEach { rawContactId ->
        ops.add(
          ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
            .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString()))
            .build()
        )
      }

      try {
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
        deletedCount += ops.size
        Log.d("SyncContactsManager", "Batch $batchIndex: Deleted ${ops.size} contacts")
      } catch (e: Exception) {
        Log.e("SyncContactsManager", "Failed to delete batch $batchIndex", e)
      }
    }
    context.stopService(serviceIntent)

    Log.d("SyncContactsManager", "Total deleted contacts: $deletedCount")
  }


  private fun updateSendToVoicemailFlag(sourceIds: Set<String>, value: Boolean) {
    Log.d("SyncContactsManager", "updateSendToVoicemailFlag: $value")
    val contentResolver = context.contentResolver

    val BATCH_SIZE = 100
    val ops = arrayListOf<ContentProviderOperation>()
    var updatedCount = 0

    sourceIds.chunked(BATCH_SIZE).forEachIndexed { batchIndex, chunk ->
      ops.clear()

      chunk.forEach { ihash ->
        val cursor = contentResolver.query(
          ContactsContract.RawContacts.CONTENT_URI,
          arrayOf(ContactsContract.RawContacts._ID),
          "${ContactsContract.RawContacts.SOURCE_ID} = ?",
          arrayOf(ihash),
          null
        )

        cursor?.use {
          if (it.moveToFirst()) {
            val rawContactId = it.getLong(0)
            ops.add(
              ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
                .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString()))
                .withValue(ContactsContract.RawContacts.SEND_TO_VOICEMAIL, if (value) 1 else 0)
                .build()
            )
          }
        }
      }

      if (ops.isNotEmpty()) {
        try {
          contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
          updatedCount += ops.size
          Log.d("SyncContactsManager", "Batch $batchIndex: Updated ${ops.size} contacts")
        } catch (e: Exception) {
          Log.e("SyncContactsManager", "Failed to update batch $batchIndex", e)
        }
      }
    }

    Log.d("SyncContactsManager", "Total sendToVoicemail = $value updates: $updatedCount")
  }

  fun getExistingIhashesInGroup(groupId: Long): Set<String> {
    Log.d("SyncContactsManager", "getExistingIhashesInGroup")
    val contentResolver = context.contentResolver
    val rawContactIds = mutableSetOf<Long>()

    // Step 1: Get all RAW_CONTACT_IDs that are members of the target group
    val groupCursor = contentResolver.query(
      ContactsContract.Data.CONTENT_URI,
      arrayOf(ContactsContract.Data.RAW_CONTACT_ID),
      "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
      arrayOf(
        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
        groupId.toString()
      ),
      null
    )

    groupCursor?.use {
      while (it.moveToNext()) {
        val rawId = it.getLong(0)
        rawContactIds.add(rawId)
      }
    }

    if (rawContactIds.isEmpty()) return emptySet()

    // Step 2: Query RawContacts for SOURCE_IDs matching those RAW_CONTACT_IDs
    val ihashes = mutableSetOf<String>()
    val selection = "${ContactsContract.RawContacts._ID} IN (${rawContactIds.joinToString(",")})"
    val cursor = contentResolver.query(
      ContactsContract.RawContacts.CONTENT_URI,
      arrayOf(ContactsContract.RawContacts.SOURCE_ID),
      selection,
      null,
      null
    )

    cursor?.use {
      while (it.moveToNext()) {
        val sourceId = it.getString(0)
        if (!sourceId.isNullOrBlank()) {
          ihashes.add(sourceId)
        }
      }
    }

    Log.d("SyncContactsManager", "Found ${ihashes.size} ihashes in group $groupId")
    return ihashes
  }

  fun deleteContactsBySourceIds(sourceIds: Set<String>) {
    Log.d("SyncContactsManager", "deleteContactsBySourceIds: ${sourceIds.size}")

    val contentResolver = context.contentResolver
    val batchSize = 100
    val ops = mutableListOf<ContentProviderOperation>()

    var deleted = 0

    sourceIds.chunked(batchSize).forEach { chunk ->
      chunk.forEach { ihash ->
        ops.add(
          ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
            .withSelection("${ContactsContract.RawContacts.SOURCE_ID} = ?", arrayOf(ihash))
            .build()
        )
      }

      if (ops.isNotEmpty()) {
        try {
          contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
          deleted += ops.size
        } catch (e: Exception) {
          Log.e("SyncContactsManager", "Batch delete failed", e)
        }
        ops.clear()
      }
    }

    Log.d("SyncContactsManager", "Deleted $deleted contacts from SOURCE_IDs")
  }

    private fun getOrCreateContactGroupId(groupName: String): Long {
      Log.d("SyncContactsManager", "getOrCreateContactGroupId: ${groupName}")
        val contentResolver: ContentResolver = context.contentResolver
        var groupId: Long = -1

        // Try to find the group first
        val cursor: Cursor? = contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups.DELETED} = ? AND ${ContactsContract.Groups.TITLE} = ?",
            arrayOf("0", groupName),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                groupId = it.getLong(it.getColumnIndexOrThrow(ContactsContract.Groups._ID))
            }
        }

        // If not found, create the group
        if (groupId == -1L) {
            Log.d("SyncContactsManager", "${groupName} not found, creating new group")
            val op = ContentProviderOperation.newInsert(ContactsContract.Groups.CONTENT_URI)
                .withValue(ContactsContract.Groups.TITLE, groupName)
                .build()
            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, arrayListOf(op))
            // Retrieve the new group ID from the results
            groupId = results[0].uri?.lastPathSegment?.toLong() ?: -1L
        }
        return groupId
    }


    /**
     * Helper to build an insert ContentProviderOperation.
     */
    private fun buildInsertOp(
        backReference: Int,
        mimeType: String,
        values: Map<String, Any?>
    ): ContentProviderOperation {
        val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, backReference)
            .withValue(ContactsContract.Data.MIMETYPE, mimeType)
        values.forEach { (key, value) ->
            if (value != null) {
                builder.withValue(key, value)
            }
        }
        return builder.build()
    }

    /**
     * Inserts contacts in batch with progress updates and toast notifications.
     */
    fun insertContacts(
      contactsDict: Map<String, Contact>,
      contentResolver: ContentResolver,
      context: Context
    ) {
      val ops = ArrayList<ContentProviderOperation>()
      val totalContacts = contactsDict.size
      var counter = 1

      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

      contactsDict.forEach { (sourceContactID, contact) ->
        createInsertContactOps(ops, contact, sourceContactID)

        if (ops.size >= MAX_OPS_PER_BATCH) {
          contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
          ops.clear()

          val progress = (counter.toDouble() / totalContacts * 100).toInt()

          val updatedNotification = NotificationCompat.Builder(context, SyncContactsManager.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Syncing Contacts")
            .setContentText("Progress: $progress%")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setProgress(100, progress, false)
            .build()

          notificationManager.notify(SyncContactsManager.NOTIFICATION_ID, updatedNotification)
        }

        counter++
      }

      if (ops.isNotEmpty()) {
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
      }

      // Final update — done
      val doneNotification = NotificationCompat.Builder(context, SyncContactsManager.NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Sync Complete")
        .setContentText("Contacts successfully synced")
        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
        .setProgress(0, 0, false)
        .build()

      notificationManager.notify(SyncContactsManager.NOTIFICATION_ID, doneNotification)
    }

    /**
     * Creates a batch of operations for inserting one contact.
     */
    private fun createInsertContactOps(
        ops: MutableList<ContentProviderOperation>,
        contact: Contact,
        sourceContactID: String
    ) {
        val rawInsertIndex = ops.size

        // Insert a new raw contact with a source ID.
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
              .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.android.localprofile")
              .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, "Local Contacts")
              .withValue(ContactsContract.RawContacts.SEND_TO_VOICEMAIL, if (contact.sendToVoicemail) 1 else 0)
              .withValue(ContactsContract.RawContacts.SOURCE_ID, sourceContactID)
              .build()
        )

        // Add the contact to the SCA group.
        if (SCAGroupID != -1L) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, SCAGroupID)
                    .withValue(ContactsContract.Data.IS_READ_ONLY, 1)
                    .build()
            )
        }

        // Insert the structured name
        val displayName = "${contact.firstName} ${contact.lastName}"
        ops.add(
            buildInsertOp(
                backReference = rawInsertIndex,
                mimeType = ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                values = mapOf(
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME to displayName,
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME to contact.firstName,
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME to contact.lastName
                )
            )
        )

        // Insert organization (company, job title, and department).
        ops.add(
            buildInsertOp(
                backReference = rawInsertIndex,
                mimeType = ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                values = mapOf(
                    ContactsContract.CommonDataKinds.Organization.COMPANY to contact.company,
                    ContactsContract.CommonDataKinds.Organization.TITLE to contact.jobTitle,
                    ContactsContract.CommonDataKinds.Organization.DEPARTMENT to contact.department
                )
            )
        )

        // Insert a default note.
        ops.add(
            buildInsertOp(
                backReference = rawInsertIndex,
                mimeType = ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                values = mapOf(
                    ContactsContract.CommonDataKinds.Note.NOTE to "contact managed by SCA"
                )
            )
        )

        // Insert phone numbers with type mapping.
        contact.phoneNumbers.forEach { phone ->
            val phoneType = when (phone.label.toLowerCase()) {
                "business" -> ContactsContract.CommonDataKinds.Phone.TYPE_WORK
                "mobile" -> ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                "home" -> ContactsContract.CommonDataKinds.Phone.TYPE_HOME
                else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
            }
            ops.add(
                buildInsertOp(
                    backReference = rawInsertIndex,
                    mimeType = ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                    values = mapOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER to phone.number,
                        ContactsContract.CommonDataKinds.Phone.TYPE to phoneType,
                        ContactsContract.CommonDataKinds.Phone.LABEL to phone.label
                    )
                )
            )
        }

        // Insert emails with type mapping.
        contact.emails.forEach { email ->
            val emailType = when (email.label.toLowerCase()) {
                "business" -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
                "home" -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
                else -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
            }
            ops.add(
                buildInsertOp(
                    backReference = rawInsertIndex,
                    mimeType = ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                    values = mapOf(
                        ContactsContract.CommonDataKinds.Email.ADDRESS to email.email,
                        ContactsContract.CommonDataKinds.Email.TYPE to emailType,
                        ContactsContract.CommonDataKinds.Email.LABEL to email.label
                    )
                )
            )
        }
    }
}

/**
 * A simple foreground service to keep the app alive during contact sync.
 * This service creates a notification that must be displayed while running.
 */
class SyncContactsForegroundService : Service() {

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, SyncContactsManager.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Syncing Contacts")
            .setContentText("Please wait while contacts are being synced...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()

        // Start the service in the foreground
        startForeground(SyncContactsManager.NOTIFICATION_ID, notification)

        // If the system kills the service, do not recreate it (or use START_STICKY if preferred)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    /**
     * Creates the notification channel required for foreground services on Android O and above.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SyncContactsManager.NOTIFICATION_CHANNEL_ID,
                SyncContactsManager.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
