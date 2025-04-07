package berlin.prototype.callerid

import android.util.Log

// --- Input (JSON) data structures ---
data class IProtoContact(
    //val addresses: List<IAddress>,
    val company: String,
    val custom1: String,
    val datasource: String,
    val datasourceID: String,
    val datasourcePrio: String,
    val department: String,
    val emailaddresses: List<IContactItem>,
    val givenname: String,
    val guid: String,
    val ihash: String,
    val jobtitle: String,
    val officeLocations: List<IContactItem>,
    val phonenumbers: List<IContactItem>,
    val realDatasourceID: String,
    val surname: String,
    val signature: String,
    // Optional fields:
    val displayName: String? = null,
    val base64ProfileImage: String? = null,
    val givenNameFirstChar: String? = null,
    val surnameFirstChar: String? = null,
    val initials: String? = null,
    val isFavorite: Boolean? = null,
    //val metadata: List<ContactMetaData>? = null,
    val realDatasourceIDs: List<String>? = null
)

/**
 * A simple representation of a contact item (phone number, email, or office location).
 */
data class IContactItem(
    val value: String,
    val label: String
)

// --- Target contact data structures ---

/**
 * Represents the target contact structure for syncing.
 */
data class Contact(
    val guid: String, // We are using ihash as the unique identifier.
    val contactType: String = "person",
    val name: String,
    val lastName: String,
    val firstName: String,
    val company: String,
    val jobTitle: String,
    val department: String,
    val sendToVoicemail: Boolean,
    val phoneNumbers: List<PhoneNumber> = emptyList(),
    val emails: List<Email> = emptyList()
)

data class PhoneNumber(
    val number: String,
    val label: String
)

data class Email(
    val email: String,
    val label: String
)


// --- Transformation Function ---
fun transform(source: IProtoContact, isVacationModeActive: Boolean = false): Contact {
  val name = when {
    !source.displayName.isNullOrBlank() -> source.displayName
    !source.givenname.isNullOrBlank() && !source.surname.isNullOrBlank() -> "${source.surname}, ${source.givenname}"
    !source.surname.isNullOrBlank() -> source.surname
    else -> "Unknown"
  }

  val phoneNumbers = source.phonenumbers.orEmpty()
    .filter { !it.value.isNullOrBlank() }
    .map { PhoneNumber(it.value, it.label) }

  val emails = source.emailaddresses.orEmpty()
    .filter { !it.value.isNullOrBlank() }
    .map { Email(it.value, it.label) }

  val sendToVoicemail = if (isVacationModeActive) !(source.isFavorite ?: false) else false

  return Contact(
    guid = source.ihash,
    contactType = "person",
    name = name,
    lastName = source.surname.orEmpty(),
    firstName = source.givenname.orEmpty(),
    company = source.company.orEmpty(),
    jobTitle = source.jobtitle.orEmpty(),
    department = source.department.orEmpty(),
    sendToVoicemail = sendToVoicemail,
    phoneNumbers = phoneNumbers,
    emails = emails
  )
}
