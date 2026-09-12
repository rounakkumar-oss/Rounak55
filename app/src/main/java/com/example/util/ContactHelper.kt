package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

object ContactHelper {
    fun searchContact(context: Context, queryName: String): ContactMatch? {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val cleanQuery = queryName.trim().lowercase()
        if (cleanQuery.isBlank()) return null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        var bestMatch: ContactMatch? = null

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                if (nameIndex == -1 || numberIndex == -1) return null

                while (it.moveToNext()) {
                    val displayName = it.getString(nameIndex) ?: ""
                    val phoneNumber = it.getString(numberIndex) ?: ""
                    val lowerName = displayName.lowercase()

                    // Exact match
                    if (lowerName == cleanQuery) {
                        return ContactMatch(displayName, cleanPhoneNumber(phoneNumber))
                    }

                    // Starts with or contains
                    if (bestMatch == null && (lowerName.startsWith(cleanQuery) || lowerName.contains(cleanQuery))) {
                        bestMatch = ContactMatch(displayName, cleanPhoneNumber(phoneNumber))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return bestMatch
    }

    private fun cleanPhoneNumber(phone: String): String {
        return phone.replace(" ", "").replace("-", "")
    }
}
