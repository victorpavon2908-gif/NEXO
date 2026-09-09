package ni.nexo.app.data

import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.security.MessageDigest
import java.util.Locale

data class DeviceContact(
    val localName: String,
    val phoneE164: String,
    val phoneHash: String
)

object DeviceContacts {
    fun read(context: Context): List<DeviceContact> {
        val result = linkedMapOf<String, DeviceContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex).orEmpty().trim().ifBlank { "Contacto" }
                val rawNumber = cursor.getString(numberIndex).orEmpty()
                val e164 = normalizeToE164(context, rawNumber) ?: continue
                val hash = hashPhone(e164)
                result.putIfAbsent(hash, DeviceContact(name, e164, hash))
                // El backend recibe lotes de 500, pero la agenda local puede ser mayor.
                if (result.size >= 5_000) break
            }
        }
        return result.values.toList()
    }

    fun normalizeToE164(context: Context, rawNumber: String): String? {
        val raw = rawNumber.trim()
        if (raw.isBlank()) return null
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val countryIso = telephony?.networkCountryIso
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.US)
            ?: Locale.getDefault().country.takeIf { it.isNotBlank() }?.uppercase(Locale.US)
            ?: "NI"

        PhoneNumberUtils.formatNumberToE164(raw, countryIso)?.let { return it }

        val digits = raw.filter(Char::isDigit)
        if (raw.startsWith("+") && digits.length in 8..15) return "+$digits"
        // Fallback útil en Nicaragua cuando el operador no informa countryIso.
        if (countryIso == "NI" && digits.length == 8) return "+505$digits"
        return null
    }

    fun hashPhone(phoneE164: String): String {
        val canonical = phoneE164.filter(Char::isDigit)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
