package berlin.prototype.callerid

import com.facebook.react.bridge.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object ReactBridgeTools {

  @Throws(JSONException::class)
  fun convertJsonToMap(jsonObject: JSONObject): WritableMap {
    val map: WritableMap = WritableNativeMap()

    val iterator: Iterator<String> = jsonObject.keys()
    while (iterator.hasNext()) {
      val key = iterator.next()
      val value = jsonObject.get(key)
      when (value) {
        is JSONObject -> map.putMap(key, convertJsonToMap(value))
        is JSONArray -> map.putArray(key, convertJsonToArray(value))
        is Boolean -> map.putBoolean(key, value)
        is Int -> map.putInt(key, value)
        is Double -> map.putDouble(key, value)
        is String -> map.putString(key, value)
        else -> map.putString(key, value.toString())
      }
    }
    return map
  }

  @Throws(JSONException::class)
  fun convertJsonToArray(jsonArray: JSONArray): WritableArray {
    val array: WritableArray = WritableNativeArray()
    for (i in 0 until jsonArray.length()) {
      val value = jsonArray.get(i)
      when (value) {
        is JSONObject -> array.pushMap(convertJsonToMap(value))
        is JSONArray -> array.pushArray(convertJsonToArray(value))
        is Boolean -> array.pushBoolean(value)
        is Int -> array.pushInt(value)
        is Double -> array.pushDouble(value)
        is String -> array.pushString(value)
        else -> array.pushString(value.toString())
      }
    }
    return array
  }

  @Throws(JSONException::class)
  fun convertMapToJson(readableMap: ReadableMap): JSONObject {
    val jsonObject = JSONObject()
    val iterator = readableMap.keySetIterator()
    while (iterator.hasNextKey()) {
      val key = iterator.nextKey()
      when (readableMap.getType(key)) {
        ReadableType.Null -> jsonObject.put(key, JSONObject.NULL)
        ReadableType.Boolean -> jsonObject.put(key, readableMap.getBoolean(key))
        ReadableType.Number -> jsonObject.put(key, readableMap.getDouble(key))
        ReadableType.String -> jsonObject.put(key, readableMap.getString(key))
        ReadableType.Map -> jsonObject.put(key, convertMapToJson(readableMap.getMap(key)!!))
        ReadableType.Array -> jsonObject.put(key, convertArrayToJson(readableMap.getArray(key)!!))
      }
    }
    return jsonObject
  }

  @Throws(JSONException::class)
  fun convertArrayToJson(readableArray: ReadableArray): JSONArray {
    val jsonArray = JSONArray()
    for (i in 0 until readableArray.size()) {
      when (readableArray.getType(i)) {
        ReadableType.Null -> {
          // No-op for null values
        }
        ReadableType.Boolean -> jsonArray.put(readableArray.getBoolean(i))
        ReadableType.Number -> jsonArray.put(readableArray.getDouble(i))
        ReadableType.String -> jsonArray.put(readableArray.getString(i))
        ReadableType.Map -> jsonArray.put(convertMapToJson(readableArray.getMap(i)))
        ReadableType.Array -> jsonArray.put(convertArrayToJson(readableArray.getArray(i)))
      }
    }
    return jsonArray
  }
}
