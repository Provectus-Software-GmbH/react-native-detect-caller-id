package berlin.prototype.callerid.db

data class User(
  var id: String = "",
  var number: String = "",
  var fullName: String = "",
  var appointment: String = "",
  var city: String = ""
) {
  // providing default getters and setters (getId, setId, etc)
  // will result in in a conflict due to the same method signature
  // so we just skip them here
}
