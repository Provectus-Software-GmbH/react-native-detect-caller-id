package berlin.prototype.callerid.db

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.facebook.react.bridge.ReactApplicationContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@RequiresApi(Build.VERSION_CODES.N)
object CallerManager {
  var contentProviderAvailable = false // true for default mode, false for compatibility mode
  var workProfileAvailable = false // true for work profile mode (SyncToLocalContacts)

  private val blockedCallers = mutableListOf<Caller>()
  private val allowedCallers = mutableListOf<Caller>()

  private const val BLOCKED_CALLERS_FILE = "blockedCallers.txt"
  private const val ALLOWED_CALLERS_FILE = "allowedCallers.txt"

  private var appContext: ReactApplicationContext? = null

  fun initialize(context: ReactApplicationContext, contentProvider: Boolean, workProfile: Boolean) {
    appContext = context
    contentProviderAvailable = contentProvider
    workProfileAvailable = workProfile

    // allowed and blocked callers are handled by system contacts app / expo-contacts plugin
    if (workProfileAvailable) {
      return
    }

    allowedCallers.addAll(getSavedCallerList(ALLOWED_CALLERS_FILE))
    blockedCallers.addAll(getSavedCallerList(BLOCKED_CALLERS_FILE))

    Log.d("CallerManager", "get saved allowed callers: ${allowedCallers.size}")
    Log.d("CallerManager", "get saved blocked callers: ${blockedCallers.size}")
  }

  fun updateCallers(items: JSONArray, type: String) {
    when (type) {
      "clearAll" -> clearAllCallerLists()
      "block" -> blockCallers(items)
      "unblock" -> unblockCallers(items)
      "identify" -> identifyCallers(items)
      else -> {
        // Optional: handle unknown type
        throw IllegalArgumentException("Unknown type: $type")
      }
    }
  }

  private fun identifyCallers(items: JSONArray) {
    val currentAllowedCallers = allowedCallers.toSet()

    for (i in 0 until items.length()) {
      val item = items.getJSONObject(i)
      val phoneNumber = item.getLong("phonenumber")
      val isBlocked = item.getBoolean("isBlocked")

      if (isBlocked) {
        addCaller(phoneNumber.toString(), item.getString("label"), false)
      }
    }

    if (currentAllowedCallers != allowedCallers) {
      Log.d("CallerManager", "save identifyCallers  (" + allowedCallers.size + ")")
      saveCallerList(allowedCallers, ALLOWED_CALLERS_FILE)
    }
  }

  private fun blockCallers(items: JSONArray) {
    val currentBlockedCallers = blockedCallers.toSet()

    for (i in 0 until items.length()) {
      val item = items.getJSONObject(i)
      val phoneNumber = item.getLong("phonenumber")
      val isBlocked = item.getBoolean("isBlocked")

      if (isBlocked) {
        addCaller(phoneNumber.toString(), item.getString("label"), true)
      }
    }

    if (currentBlockedCallers != blockedCallers) {
      Log.d("CallerManager", "save blockCallers (" + blockedCallers.size + ")")
      saveCallerList(blockedCallers, BLOCKED_CALLERS_FILE)
    }
  }

  private fun unblockCallers(items: JSONArray) {
    val currentBlockedCallers = blockedCallers.toSet()

    for (i in 0 until items.length()) {
      val item = items.getJSONObject(i)
      val phoneNumber = item.getLong("phonenumber")
      removeCaller(phoneNumber.toString(), true)
    }

    if (currentBlockedCallers != blockedCallers) {
      Log.d("CallerManager", "save unblockCallers (" + blockedCallers.size + ")")
      saveCallerList(blockedCallers, BLOCKED_CALLERS_FILE)
    }
  }

  fun clearAllCallerLists() {
    Log.d("CallerManager", "clear allowed callers (" + allowedCallers.size + ")")
    Log.d("CallerManager", "clear blocked callers (" + blockedCallers.size + ")")
    clearCallerList(allowedCallers, ALLOWED_CALLERS_FILE)
    clearCallerList(blockedCallers, BLOCKED_CALLERS_FILE)
  }

  // remove "+" and white space from phone number
  fun getNormalizedPhoneNumber(phoneNumber: String?): String {
    return phoneNumber?.replace(Regex("[+\\s]"), "") ?: ""
  }

  fun getCallerByNumber(phoneNumber: String): Caller? {
    return allowedCallers.find { it.phoneNumber == phoneNumber }
  }

  fun isPhoneNumberBlocked(phoneNumber: String): Boolean {
    return blockedCallers.any { it.phoneNumber == phoneNumber }
  }

  private fun addCaller(phoneNumber: String, label: String, isBlocked: Boolean) {
    val callers = if (isBlocked) blockedCallers else allowedCallers

    callers.add(Caller(phoneNumber, label))
  }

  private fun removeCaller(phoneNumber: String, isBlocked: Boolean) {
    val callers = if (isBlocked) blockedCallers else allowedCallers
    callers.removeIf { it.phoneNumber == phoneNumber }
  }

  private fun getSavedCallerList(filename: String): List<Caller> {
    return try {
      val file = File(appContext?.filesDir, filename)
      if (!file.exists()) {
        emptyList()
      } else {
        val callers = mutableListOf<Caller>()
        val inputStream = FileInputStream(file)
        val reader = BufferedReader(InputStreamReader(inputStream))

        var line: String? = reader.readLine()
        while (line != null) {
          val parts = line.split("|") // Assuming format "phoneNumber,label"
          if (parts.size == 2) {
            callers.add(Caller(parts[0], parts[1]))
          }
          line = reader.readLine()
        }

        reader.close()
        callers
      }
    } catch (e: Exception) {
      e.printStackTrace()
      emptyList()
    }
  }

  private fun saveCallerList(callers: MutableList<Caller>, filename: String) {
    if (callers.isEmpty()) {
      return clearCallerList(callers, filename)
    }

    val context = appContext ?: return

    val file = File(context.filesDir, filename)
    val outputStream = FileOutputStream(file, false)
    val writer = OutputStreamWriter(outputStream)

    try {
      for (caller in callers) {
        writer.write("${caller.phoneNumber}|${caller.label}\n")
      }
    } finally {
      writer.flush()
      writer.close()
    }
  }

  // New function to delete the file
  private fun clearCallerList(callers: MutableList<Caller>, filename: String) {
    callers.clear()

    val context = appContext ?: return
    val file = File(context.filesDir, filename)

    if (file.exists()) {
      file.delete()
    }
  }
}
