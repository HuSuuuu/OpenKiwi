package com.orizon.openkiwi.core.tool.builtin

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import com.orizon.openkiwi.core.tool.*

class ContactsTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "contacts",
        description = "Read, search, and add contacts from the phone's address book",
        category = ToolCategory.COMMUNICATION.name,
        permissionLevel = PermissionLevel.SENSITIVE.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: list, search, add", true,
                enumValues = listOf("list", "search", "add")),
            "query" to ToolParamDef("string", "Search query for name or number"),
            "name" to ToolParamDef("string", "Contact name (for add)"),
            "phone" to ToolParamDef("string", "Phone number (for add)"),
            "limit" to ToolParamDef("string", "Max results", false, "20")
        ),
        requiredParams = listOf("action")
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult {
        val action = params["action"]?.toString() ?: return errorResult("Missing action")
        val limit = params["limit"]?.toString()?.toIntOrNull() ?: 20
        return runCatching {
            when (action) {
                "list" -> {
                    val contacts = queryContacts(null, limit)
                    ToolResult(definition.name, true, "Contacts (${contacts.size}):\n${contacts.joinToString("\n")}")
                }
                "search" -> {
                    val query = params["query"]?.toString() ?: return@runCatching errorResult("Missing query")
                    val contacts = queryContacts(query, limit)
                    if (contacts.isEmpty()) ToolResult(definition.name, true, "No contacts matching '$query'")
                    else ToolResult(definition.name, true, "Found ${contacts.size} contacts:\n${contacts.joinToString("\n")}")
                }
                "add" -> {
                    val name = params["name"]?.toString() ?: return@runCatching errorResult("Missing name")
                    val phone = params["phone"]?.toString() ?: return@runCatching errorResult("Missing phone")
                    addContact(name, phone)
                    ToolResult(definition.name, true, "Added contact: $name ($phone)")
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }

    private fun queryContacts(query: String?, limit: Int): List<String> {
        val results = mutableListOf<String>()
        val selection = if (query != null) "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?" else null
        val args = if (query != null) arrayOf("%$query%") else null
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            selection, args, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )
        cursor?.use {
            var count = 0
            while (it.moveToNext() && count < limit) {
                val name = it.getString(0)
                val number = it.getString(1)
                results.add("$name: $number")
                count++
            }
        }
        return results
    }

    private fun addContact(name: String, phone: String) {
        val ops = arrayListOf(
            android.content.ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null).build(),
            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build(),
            android.content.ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build()
        )
        context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
