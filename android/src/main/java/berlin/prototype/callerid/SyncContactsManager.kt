package berlin.prototype.callerid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
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
import java.io.ByteArrayOutputStream

const val MAX_OPS_PER_BATCH = 400
const val MAX_SQL_PARAM_LIMIT = 999
const val OPS_PER_TOAST = MAX_OPS_PER_BATCH * 4

// TODO: No two syncs in parallel

class SyncContactsManager(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val context = reactContext
    private var sCAGroupID: Long = -1L        // Global group ID. Set by getOrCreateContactGroupId.
    private val permissionsHelper = PermissionsHelper(reactContext)

    private val notificationManager: NotificationManager by lazy {
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private lateinit var notificationBuilder: NotificationCompat.Builder

    // Declare the module name
    override fun getName() = "SyncContactsManager"

    companion object {
        const val CONTACT_GROUP_NAME = "SCA"
        const val CONTACT_PREFIX= "SCA-"
        const val NOTIFICATION_CHANNEL_ID = "sync_contacts_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Contact Sync"
        const val NOTIFICATION_ID = 101
    }

    // Flag to track the execution state of syncContacts
    @Volatile
    private var isSyncing = false

    fun syncContacts(options: ReadableMap, isVacationModeActive: Boolean) {
      Log.d("SyncContactsManager", "syncContacts")

        // Check if a sync is already in progress
        if (isSyncing) {
            Log.d("SyncContactsManager", "Sync is already in progress. Aborting new request.")
            return
        }

        // Set the flag to true to indicate the sync has started
        isSyncing = true

        if (!permissionsHelper.hasContactPermissions()) {
            //promise.reject("NO_ACTIVITY", "Cannot request permissions: no active activity.")
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
                  sCAGroupID = getOrCreateContactGroupId(CONTACT_GROUP_NAME)
                  Log.d("SyncContactsManager", "Assigned group ID: $sCAGroupID")

                    val newContacts = getMappedContacts(items, isVacationModeActive)
                    val oldContacts = getMappedRawIds()

                    val newHashes = newContacts.keys
                    val oldHashes = oldContacts.keys

                    val hashesToUpdate = oldHashes.intersect(newHashes)
                    val hashesToDelete = oldHashes.subtract(newHashes)

                    val contactToInsert = newContacts.filterKeys { it !in oldHashes }

                    val rawIdsToUpdate = oldContacts.filterKeys { it in hashesToUpdate }.values
                    val rawIdsToDelete = oldContacts.filterKeys { it in hashesToDelete }.values

                    Log.d("SyncContactsManager", "unchanged: ${rawIdsToUpdate.size}, toInsert: ${contactToInsert.size}, toDelete: ${rawIdsToDelete.size}")

                    deleteContactsByRawIds(rawIdsToDelete)
                    updateSendToVoicemailFlag(rawIdsToUpdate, isVacationModeActive)

                    if (isVacationModeActive) {
                      val favoritesHashes = newContacts
                        .filterKeys { it in hashesToUpdate }
                        .filterValues { !it.sendToVoicemail }.keys

                      val rawIdsToUnblock = oldContacts.filterKeys { it in favoritesHashes }.values
                      Log.d("SyncContactsManager", "vacationModeActive, unblocking favorites: ${rawIdsToUnblock.size}")

                      updateSendToVoicemailFlag(rawIdsToUnblock, false)
                    }

                    // Insert the contacts in batch with progress updates.
                    insertContacts(contactToInsert, context.contentResolver)

                    // Stop the foreground service when done.
                    context.stopService(serviceIntent)
                    //promise.resolve("synced to local contacts")
                } catch (e: Exception) {
                    e.printStackTrace()
                    //promise.reject("SYNC_ERROR", "Failed to sync contacts", e)
                } finally {
                    // Reset the flag to allow future syncs
                    isSyncing = false
                }
            }.start()
        } catch (e: JSONException) {
            e.printStackTrace()
            // promise.reject("PARSE_ERROR", "Failed to parse JSON", e)
            isSyncing = false // Reset the flag when the sync is complete
        }
    }

    // function to get the raw id of a contact by its guid
    private fun getRawIdByGuid(guid: String): Long? {
        Log.d("getRawIdByGuid guid:", guid)
        val curContactsRawIds = getMappedRawIds()
        // extract rawID from MAP curContactsRawIds() <String, Long> where key = "SCA-${guid}"
        val rawId = curContactsRawIds["SCA-$guid"]
        Log.d("getRawIdByGuid rawId:", rawId.toString())
        return rawId
    }

    // block a single contact by guid
    fun blockLocalContact(guid: String) {
        getRawIdByGuid(guid)?.let { rawID ->
            updateSendToVoicemailFlag(listOf(rawID), true)
            Log.d("block", "guid: $guid, block rawId: $rawID")
        }
    }

    // unblock a single contact by guid
    fun unblockLocalContact(guid: String) {
        getRawIdByGuid(guid)?.let { rawID ->
            updateSendToVoicemailFlag(listOf(rawID), false)
            Log.d("unblock", "guid: $guid, unblock rawId: $rawID")
        }
    }

  // Create a dictionary (map) to hold contacts to insert.
  // Key: Source id (SCA-${hash}; Value: Contact model.
  private fun getMappedContacts(
      items: JSONArray,
      isVacationModeActive: Boolean
  ): MutableMap<String, Contact> {
    val contactsToInsert = mutableMapOf<String, Contact>()
    val gson = Gson()
    // Process each contact JSON object.
    for (i in 0 until items.length()) {
      val contactJson = items.getJSONObject(i)
      val protoContact = gson.fromJson(contactJson.toString(), IProtoContact::class.java)
      val contact = transform(protoContact, isVacationModeActive)
      contactsToInsert[contact.sourceId] = contact
    }

    return contactsToInsert
  }


  // Create a dictionary (map) to hold existing contact raw ids
  // Key: Source id (SCA-${hash}; Value: raw id of contact
  fun getMappedRawIds(): Map<String, Long> {
    Log.d("SyncContactsManager", "getMappedRawIds")

    val contentResolver = context.contentResolver
    val mappedIds = mutableMapOf<String, Long>()

    val selection = "${ContactsContract.RawContacts.SOURCE_ID} LIKE ? AND ${ContactsContract.RawContacts.DELETED} = 0"
    val selectionArgs = arrayOf("${CONTACT_PREFIX}%")

    val cursor = contentResolver.query(
      ContactsContract.RawContacts.CONTENT_URI,
      arrayOf(ContactsContract.RawContacts.SOURCE_ID, ContactsContract.RawContacts._ID),
      selection,
      selectionArgs,
      null
    )

    cursor?.use {
      val sourceIdIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.SOURCE_ID)
      val rawIdIndex = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)

      while (cursor.moveToNext()) {
        val sourceId = cursor.getString(sourceIdIndex)
        val rawId = cursor.getLong(rawIdIndex)
        if (!sourceId.isNullOrBlank()) {
          mappedIds[sourceId] = rawId
        }
      }
    }

    Log.d("SyncContactsManager", "Mapped ${mappedIds.size} sourceIds to raw IDs")
    return mappedIds
  }

  fun clearContacts() {
    Log.d("SyncContactsManager", "clearContacts: Clearing all contacts with prefix $CONTACT_PREFIX")

    val oldContactsRawIds = getMappedRawIds().values
    deleteContactsByRawIds(oldContactsRawIds)
  }

  fun deleteContactsByRawIds(rawContactIds: Collection<Long>) {
    Log.d("SyncContactsManager", "deleteContactsByRawIds: ${rawContactIds.size}")

    if (rawContactIds.isEmpty()) return

    val contentResolver = context.contentResolver
    val chunkSize = MAX_SQL_PARAM_LIMIT // SQLite parameter limit
    var totalDeleted = 0

    val notifyProgress = rawContactIds.size > MAX_OPS_PER_BATCH

    if (notifyProgress) {
      prepareNotify("Prepare deleting contacts")
    }

    rawContactIds.chunked(chunkSize).forEach { chunk ->
      val placeholders = chunk.joinToString(",") { "?" }
      val selection = "${ContactsContract.RawContacts._ID} IN ($placeholders)"

      try {
        val deletedCount = contentResolver.delete(
          ContactsContract.RawContacts.CONTENT_URI,
          selection,
          chunk.map { it.toString() }.toTypedArray()
        )
        totalDeleted += deletedCount
        Log.d("SyncContactsManager", "Deleted $deletedCount raw contacts in chunk")

        if (notifyProgress) {
          val progress = (totalDeleted.toDouble() / rawContactIds.size * 100).toInt()
          val updateToast = totalDeleted % OPS_PER_TOAST == 0
          notifyProgress("Deleting contacts", progress, updateToast)
        }
      } catch (e: Exception) {
        Log.e("SyncContactsManager", "Failed to delete raw contacts in chunk", e)
      }
    }

    if (notifyProgress) {
      completeNotify("Finished deleting contacts")
    }

    Log.d("SyncContactsManager", "Total deleted raw contacts: $totalDeleted")
  }

  fun updateSendToVoicemailFlag(rawContactIds: Collection<Long>, value: Boolean) {
    Log.d("SyncContactsManager", "updateSendToVoicemailFlag: ${rawContactIds.size} contacts to ${if (value) "enable" else "disable"} voicemail flag")

    if (rawContactIds.isEmpty()) return

    val contentResolver = context.contentResolver
    val chunkSize = MAX_SQL_PARAM_LIMIT
    var totalUpdated = 0

    rawContactIds.chunked(chunkSize).forEach { chunk ->
      val placeholders = chunk.joinToString(",") { "?" }
      val selection = "${ContactsContract.RawContacts._ID} IN ($placeholders)"
      val selectionArgs = chunk.map { it.toString() }.toTypedArray()
      val contentValues = android.content.ContentValues().apply {
        put(ContactsContract.RawContacts.SEND_TO_VOICEMAIL, if (value) 1 else 0)
      }

      try {
        val updatedCount = contentResolver.update(
          ContactsContract.RawContacts.CONTENT_URI,
          contentValues,
          selection,
          selectionArgs
        )
        totalUpdated += updatedCount
        Log.d("SyncContactsManager", "Updated $updatedCount raw contacts in chunk")
      } catch (e: Exception) {
        Log.e("SyncContactsManager", "Failed to update sendToVoicemail flag in chunk", e)
      }
    }

    Log.d("SyncContactsManager", "Total updated sendToVoicemail flags: $totalUpdated")
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
      contentResolver: ContentResolver
    ) {
      Log.e("SyncContactsManager", "insertContacts, ${contactsDict.size} contacts")

      if (contactsDict.isEmpty()) return

      val ops = ArrayList<ContentProviderOperation>()
      val totalContacts = contactsDict.size
      var counter = 1

      val notifyProgress = totalContacts > MAX_OPS_PER_BATCH

      if (notifyProgress) {
        prepareNotify("Prepare syncing contacts")
      }

      contactsDict.forEach { (sourceContactID, contact) ->
        createInsertContactOps(ops, contact)
        counter++

        if (ops.size >= MAX_OPS_PER_BATCH) {
          contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
          ops.clear()

        }

        if (notifyProgress && counter % MAX_OPS_PER_BATCH == 0) {
          val progress = (counter.toDouble() / totalContacts * 100).toInt()
          val updateToast = counter % OPS_PER_TOAST == 0
          Log.d("SyncContactsManager", "insertContacts counter: $counter OPS_PER_TOAST: $OPS_PER_TOAST counter % OPS_PER_TOAST: ${counter % OPS_PER_TOAST}")
          notifyProgress("Syncing contacts", progress, updateToast)
        }
      }

      // Apply remaining ops
      if (ops.isNotEmpty()) {
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
      }

      bulkUpdateAvatars(context, contentResolver)

      if (notifyProgress) {
        completeNotify("Finished syncing contacts")
      }
    }

    /**
     * Creates a batch of operations for inserting one contact.
     */
    private fun createInsertContactOps(
        ops: MutableList<ContentProviderOperation>,
        contact: Contact
    ) {
        val rawInsertIndex = ops.size

        // Insert a new raw contact with a source ID.
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
              .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, "com.android.localprofile")
              .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, "Local Contacts")
              .withValue(ContactsContract.RawContacts.SEND_TO_VOICEMAIL, if (contact.sendToVoicemail) 1 else 0)
              .withValue(ContactsContract.RawContacts.SOURCE_ID, contact.sourceId)
              .build()
        )

        // Add the contact to the SCA group.
        if (sCAGroupID != -1L) {
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawInsertIndex)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, sCAGroupID)
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

      // Insert placeholder image as avatar, to be replaced in bulk by SCA logo
      val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(Color.TRANSPARENT) // or Color.BLACK, Color.WHITE
      val stream = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
      val onePxImageBytes = stream.toByteArray()

      ops.add(
        buildInsertOp(
          backReference = rawInsertIndex,
          mimeType = ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
          values = mapOf(
            ContactsContract.CommonDataKinds.Photo.PHOTO to onePxImageBytes
          )
        )
      )
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

  private fun ensureNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
        val channel = NotificationChannel(
          NOTIFICATION_CHANNEL_ID,
          NOTIFICATION_CHANNEL_NAME,
          NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
      }
    }
  }

  fun bulkUpdateAvatars(context: Context, contentResolver: ContentResolver) {
    val rawContactIds = getMappedRawIds().values

    Log.d("SyncContactsManager", "bulkUpdateAvatars: ${rawContactIds.size} contacts")

    if (rawContactIds.isEmpty()) return

    // Load the logo drawable
    val drawable = ContextCompat.getDrawable(context, R.drawable.logo) ?: return
    val bitmap = if (drawable is BitmapDrawable) {
      drawable.bitmap
    } else {
      val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
      val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
      val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      drawable.setBounds(0, 0, canvas.width, canvas.height)
      drawable.draw(canvas)
      bmp
    }

    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    val appIconBytes = stream.toByteArray()

    val ops = mutableListOf<ContentProviderOperation>()

    rawContactIds.chunked(MAX_SQL_PARAM_LIMIT).forEach { chunk ->
      val placeholders = chunk.joinToString(",") { "?" }
      val selection = "${ContactsContract.Data.RAW_CONTACT_ID} IN ($placeholders) AND ${ContactsContract.Data.MIMETYPE} = ?"
      val selectionArgs = chunk.map { it.toString() } + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE

      ops.add(
        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
          .withSelection(selection, selectionArgs.toTypedArray())
          .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, appIconBytes)
          .build()
      )
    }

    if (ops.isNotEmpty()) {
      try {
        contentResolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
        Log.d("SyncContactsManager", "Updated avatars for ${rawContactIds.size} contacts")
      } catch (e: Exception) {
        Log.e("SyncContactsManager", "Failed to bulk update avatars", e)
      }
    }
  }

  private fun prepareNotify(title: String) {
    ensureNotificationChannel(context)

    notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
      .setContentTitle(title)
      .setContentText("Starting...")
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setProgress(100, 0, false)
      .setOngoing(true)

    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

    showToastOnMainThread(title)
  }

  fun notifyProgress(title: String, progressPercent: Int, updateToast: Boolean = false) {
    Log.d("SyncContactsManager", "notifyProgress ${title} ${progressPercent}, updateToast: ${updateToast}")
    if (updateToast) {
      showToastOnMainThread("$title: $progressPercent%")
    }

    notificationBuilder
      .setContentTitle(title)
      .setContentText("Progress: $progressPercent%")
      .setProgress(100, progressPercent, false)

    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
  }

  private fun completeNotify(title: String = "Done") {
    notificationBuilder
      .setContentTitle(title)
      .setContentText("")
      .setProgress(0, 0, false)
      .setOngoing(false)

    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

    showToastOnMainThread(title)
  }

  private fun showToastOnMainThread(message: String) {
    Log.d("SyncContactsManager", "showToastOnMainThread $message")
    Handler(Looper.getMainLooper()).post {
      Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
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
