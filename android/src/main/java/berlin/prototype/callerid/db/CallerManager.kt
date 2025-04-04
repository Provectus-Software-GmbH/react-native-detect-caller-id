package berlin.prototype.callerid.db

import android.content.Context
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

    // allowed and blocked callers are handled by system contacts app
    if (workProfileAvailable) {
      return
    }

    allowedCallers.addAll(getSavedCallerList(ALLOWED_CALLERS_FILE))
    blockedCallers.addAll(getSavedCallerList(BLOCKED_CALLERS_FILE))

    Log.d("CallerManager", "get saved allowed callers: ${allowedCallers.size}")
  }

  fun ensureContext(context: Context) {
    if (appContext == null) {
      appContext = context.applicationContext as? ReactApplicationContext
        ?: throw IllegalStateException("Context is not a ReactApplicationContext")
    }
  }

  fun updateCallers(items: JSONArray, type: String) {
    Log.d("CallerManager", "updateCallers type: $type count: ${items.length()}")
    when (type) {
      "clearAll" -> clearAllCallerList()
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
    Log.d("CallerManager", "identifyCallers count: ${items.length()}")

    val currentAllowedCallers = allowedCallers.toSet()

    for (i in 0 until items.length()) {
      val item = items.getJSONObject(i)
      val phoneNumber = item.getLong("phonenumber")
      val label = item.getString("label")

      Log.d("CallerManager", "identifyCallers phoneNumber: $phoneNumber label: ${label}")
      allowedCallers.add(Caller(phoneNumber.toString(), label))
    }

    Log.d("CallerManager", "allowedCallers count: ${allowedCallers.size} currentAllowedCallers: ${currentAllowedCallers.size}")
    if (currentAllowedCallers != allowedCallers) {
      Log.d("CallerManager", "save identifyCallers  (" + allowedCallers.size + ")")
      saveCallerList(allowedCallers, ALLOWED_CALLERS_FILE)
    }
  }

  private fun blockCallers(items: JSONArray) {
    Log.d("CallerManager", "blockCallers count: ${items.length()}")
    val currentBlockedCallers = blockedCallers.toSet()

    for (i in 0 until items.length()) {
      val item = items.getJSONObject(i)
      val phoneNumber = item.getLong("phonenumber")
      val isBlocked = item.getBoolean("isBlocked")

      if (isBlocked) {
        blockedCallers.add(Caller(phoneNumber.toString(), item.getString("label")))
        Log.d("CallerManager", "blocked: ${phoneNumber}")

      } else {
        Log.d("CallerManager", "did not block: ${phoneNumber}")
      }
    }

    if (currentBlockedCallers != blockedCallers) {
      Log.d("CallerManager", "save blockCallers (" + blockedCallers.size + ")")
      saveCallerList(blockedCallers, BLOCKED_CALLERS_FILE)
    }
  }

  private fun unblockCallers(items: JSONArray) {
    Log.d("CallerManager", "unblockCallers count: ${items.length()}")
    val currentBlockedCallers = blockedCallers.toSet()

    for (i in 0 until items.length()) {
      val item = items.getJSONObject(i)
      val phoneNumber = item.getLong("phonenumber")
      blockedCallers.removeIf { it.phoneNumber == phoneNumber.toString() }
    }

    if (currentBlockedCallers != blockedCallers) {
      Log.d("CallerManager", "save unblockCallers (" + blockedCallers.size + ")")
      saveCallerList(blockedCallers, BLOCKED_CALLERS_FILE)
    }
  }

  fun clearAllCallerList() {
    Log.d("CallerManager", "clearAllCallerList")
    Log.d("CallerManager", "clear allowed callers (" + allowedCallers.size + ")")
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


  private fun getSavedCallerList(filename: String): List<Caller> {
    Log.d("CallerManager", "getSavedCallerList filename: $filename")

    if (appContext == null) {
      Log.e("CallerManager", "appContext is null – did you forget to initialize?")
      return emptyList()
    }

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
    Log.d("CallerManager", "getSavedCallerList filename: $filename callers: ${callers.size}")
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
    Log.d("CallerManager", "clearCallerList filename: $filename callers: ${callers.size}")
    callers.clear()

    val context = appContext ?: return
    val file = File(context.filesDir, filename)

    if (file.exists()) {
      file.delete()
    }
  }
}
