package berlin.prototype.callerid

import android.content.*
import android.content.res.AssetFileDescriptor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import berlin.prototype.callerid.R.raw.scaprofilpic
import berlin.prototype.callerid.db.CallerManager

@RequiresApi(Build.VERSION_CODES.N)
class CallerIdProvider : ContentProvider() {

  companion object {
    const val AUTHORITY = "berlin.prototype.callerid.calleridprovider"
    const val PRIMARY_PHOTO_URI = "photo/primary_photo"
    const val DIRECTORIES = 1
    const val PHONE_LOOKUP = 2
    const val PRIMARY_PHOTO = 3

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
      addURI(AUTHORITY, "directories", DIRECTORIES)
      addURI(AUTHORITY, "phone_lookup/*", PHONE_LOOKUP)
      addURI(AUTHORITY, "$PRIMARY_PHOTO_URI/*", PRIMARY_PHOTO)
    }
  }

  private val authorityUri = Uri.parse("content://$AUTHORITY")

  override fun onCreate(): Boolean {
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<String>?,
    selection: String?,
    selectionArgs: Array<String>?,
    sortOrder: String?
  ): MatrixCursor? {

    val safeProjection = projection ?: arrayOf("contact_id", "display_name", "label", "photo_thumb_uri", "photo_uri")
    val cursor = MatrixCursor(safeProjection)
    val rowBuilder = cursor.newRow()

    when (uriMatcher.match(uri)) {
      DIRECTORIES -> {
        rowBuilder.add("accountName", "SecContactsCallerID11")
        rowBuilder.add("accountType", "SecContactsCallerID11")
        rowBuilder.add("displayName", "SecContacts CallerID 11 Provider")
        rowBuilder.add("typeResourceId", R.string.caller_id_label)
        rowBuilder.add("exportSupport", 2)
        rowBuilder.add("shortcutSupport", 1)
        rowBuilder.add("photoSupport", 3)
      }

      PHONE_LOOKUP -> {
        CallerManager.ensureContext(context ?: return null)
        val normalizedPhoneNumber = CallerManager.getNormalizedPhoneNumber(uri.lastPathSegment)
        Log.d("CallerIdProvider", "look up caller with phone number: $normalizedPhoneNumber")

        val caller = CallerManager.getCallerByNumber(normalizedPhoneNumber)

        if (caller != null) {
          rowBuilder.add("contact_id", -1)
          rowBuilder.add("display_name", caller.label)
          rowBuilder.add("label", null)
          rowBuilder.add("photo_thumb_uri", null)
          rowBuilder.add(
            "photo_uri",
            Uri.withAppendedPath(authorityUri, "$PRIMARY_PHOTO_URI/scaprofilpic.png")
          )
        } else {
          rowBuilder.add("contact_id", null)
          rowBuilder.add("display_name", null)
          rowBuilder.add("label", null)
          rowBuilder.add("photo_thumb_uri", null)
          rowBuilder.add("photo_uri", null)
        }
      }

      else -> throw IllegalArgumentException("Unknown Uri: $uri")
    }

    return cursor
  }

  override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
    if (uriMatcher.match(uri) == PRIMARY_PHOTO) {
      // Always return a fixed image (e.g., SCA logo)
      return context?.resources?.openRawResourceFd(scaprofilpic)
    }
    return null
  }

  override fun getType(uri: Uri): String? {
    return null
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
    throw UnsupportedOperationException()
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    throw UnsupportedOperationException()
  }

  override fun update(
    uri: Uri,
    values: ContentValues?,
    selection: String?,
    selectionArgs: Array<String>?
  ): Int {
    throw UnsupportedOperationException()
  }
}
