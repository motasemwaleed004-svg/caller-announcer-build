package dev.callerannouncer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract

object ContactLookup {
    fun displayNameForNumber(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
        ) return null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )

        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME_PRIMARY,
            ContactsContract.PhoneLookup.DISPLAY_NAME
        )

        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    null
                } else {
                    val primaryIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME_PRIMARY)
                    val fallbackIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val primary = primaryIndex.takeIf { it >= 0 }?.let(cursor::getString)?.trim()
                    val fallback = fallbackIndex.takeIf { it >= 0 }?.let(cursor::getString)?.trim()
                    primary?.takeIf(String::isNotBlank) ?: fallback?.takeIf(String::isNotBlank)
                }
            }
        }.getOrNull()
    }
}
