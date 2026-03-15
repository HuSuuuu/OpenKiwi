package com.orizon.openkiwi.core.tool.builtin

import android.content.Context
import android.location.Geocoder
import android.location.LocationManager
import com.orizon.openkiwi.core.tool.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class LocationTool(private val context: Context) : Tool {
    override val definition = ToolDefinition(
        name = "location",
        description = "Get current GPS location, geocode addresses, and reverse geocode coordinates",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.DANGEROUS.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "Action: get_location, geocode, reverse_geocode", true,
                enumValues = listOf("get_location", "geocode", "reverse_geocode")),
            "address" to ToolParamDef("string", "Address for geocoding"),
            "latitude" to ToolParamDef("string", "Latitude"),
            "longitude" to ToolParamDef("string", "Longitude")
        ),
        requiredParams = listOf("action")
    )

    @Suppress("MissingPermission")
    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val action = params["action"]?.toString() ?: return@withContext errorResult("Missing action")
        runCatching {
            when (action) {
                "get_location" -> {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (location == null) {
                        ToolResult(definition.name, false, "", "Location unavailable. Ensure GPS is enabled.")
                    } else {
                        val info = buildString {
                            appendLine("Latitude: ${location.latitude}")
                            appendLine("Longitude: ${location.longitude}")
                            appendLine("Accuracy: ${location.accuracy}m")
                            appendLine("Altitude: ${location.altitude}m")
                            appendLine("Speed: ${location.speed}m/s")
                            appendLine("Provider: ${location.provider}")
                        }
                        ToolResult(definition.name, true, info)
                    }
                }
                "geocode" -> {
                    val address = params["address"]?.toString() ?: return@runCatching errorResult("Missing address")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocationName(address, 5)
                    if (results.isNullOrEmpty()) {
                        ToolResult(definition.name, true, "No results for '$address'")
                    } else {
                        val sb = StringBuilder("Geocode results for '$address':\n")
                        results.forEach { r ->
                            sb.appendLine("  ${r.latitude}, ${r.longitude} — ${r.getAddressLine(0) ?: ""}")
                        }
                        ToolResult(definition.name, true, sb.toString())
                    }
                }
                "reverse_geocode" -> {
                    val lat = params["latitude"]?.toString()?.toDoubleOrNull() ?: return@runCatching errorResult("Missing latitude")
                    val lon = params["longitude"]?.toString()?.toDoubleOrNull() ?: return@runCatching errorResult("Missing longitude")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocation(lat, lon, 3)
                    if (results.isNullOrEmpty()) {
                        ToolResult(definition.name, true, "No address found at ($lat, $lon)")
                    } else {
                        val sb = StringBuilder("Address at ($lat, $lon):\n")
                        results.forEach { r -> sb.appendLine("  ${r.getAddressLine(0) ?: ""}") }
                        ToolResult(definition.name, true, sb.toString())
                    }
                }
                else -> errorResult("Unknown action: $action")
            }
        }.getOrElse { ToolResult(definition.name, false, "", it.message) }
    }
    private fun errorResult(msg: String) = ToolResult(definition.name, false, "", msg)
}
