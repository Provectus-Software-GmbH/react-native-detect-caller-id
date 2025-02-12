package berlin.prototype.callerid

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
fun transform(source: IProtoContact, sendToVoicemail: Boolean = false): Contact {
    // Determine the contact's display name.
    // If displayName is provided, use it.
    // Otherwise, if a given name is available, combine surname and givenname.
    // Else, fall back to just the surname.
    val name = if (!source.displayName.isNullOrBlank()) {
        source.displayName
    } else if (source.givenname.isNotBlank()) {
        "${source.surname}, ${source.givenname}"
    } else {
        source.surname
    }

    // Map phone numbers from the source structure.
    val phoneNumbers = source.phonenumbers.map { item ->
        PhoneNumber(number = item.value, label = item.label)
    }

    // Map email addresses from the source structure.
    val emails = source.emailaddresses.map { item ->
        Email(email = item.value, label = item.label)
    }

    return Contact(
        contactType = "person",
        name = name,
        lastName = source.surname,
        firstName = source.givenname,
        company = source.company,
        jobTitle = source.jobtitle,
        department = source.department,
        sendToVoicemail = sendToVoicemail,
        phoneNumbers = phoneNumbers,
        emails = emails
    )
}