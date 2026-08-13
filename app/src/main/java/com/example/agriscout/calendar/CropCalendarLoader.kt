package com.example.agriscout.calendar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

interface CropProfileProvider {
    fun loadProfile(cropType: String): CropCalendarProfile
}

class CropCalendarLoader(private val context: Context) : CropProfileProvider {
    private val cache = mutableMapOf<String, CropCalendarProfile>()

    override fun loadProfile(cropType: String): CropCalendarProfile {
        val cropId = resolveCropId(cropType)
        cache[cropId]?.let { return it }
        val fileName = if (cropId == "default") "default.json" else "$cropId.json"
        val json = context.assets.open("crop_calendars/$fileName").bufferedReader().use { it.readText() }
        return parseProfile(JSONObject(json)).also { cache[cropId] = it }
    }

    fun resolveCropId(cropType: String): String {
        val normalized = cropType.trim().lowercase(Locale.getDefault())
        return when {
            "rice" in normalized || "paddy" in normalized -> "rice"
            "maize" in normalized || "corn" in normalized -> "maize"
            "tomato" in normalized -> "tomato"
            "wheat" in normalized -> "wheat"
            else -> "default"
        }
    }

    private fun parseProfile(json: JSONObject): CropCalendarProfile = CropCalendarProfile(
        cropId = json.getString("cropId"),
        displayName = json.getString("displayName"),
        stages = json.getJSONArray("stages").toStageSchedules(),
        diseaseWatchWindows = json.optJSONArray("diseaseWatchWindows")?.toDiseaseWatchWindows().orEmpty(),
        harvestWindow = json.getJSONObject("harvestWindow").toHarvestWindow()
    )

    private fun JSONArray.toStageSchedules(): List<CropStageSchedule> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                CropStageSchedule(
                    stage = item.getString("stage"),
                    dayRange = item.getJSONArray("dayRange").toIntRange(),
                    activities = item.getJSONArray("activities").toActivityTemplates()
                )
            )
        }
    }

    private fun JSONArray.toActivityTemplates(): List<CalendarActivityTemplate> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                CalendarActivityTemplate(
                    id = item.getString("id"),
                    type = item.getString("type"),
                    title = item.getString("title"),
                    productCategory = item.optString("productCategory"),
                    preferredProducts = item.optJSONArray("preferredProducts")?.toStringList().orEmpty(),
                    dosePerAcreKg = item.optDouble("dosePerAcreKg"),
                    unit = item.optString("unit"),
                    minSoilMoisture = item.optDoubleOrNull("minSoilMoisture"),
                    maxTemperatureCelsius = item.optDoubleOrNull("maxTemperatureCelsius"),
                    notes = item.optString("notes")
                )
            )
        }
    }

    private fun JSONArray.toDiseaseWatchWindows(): List<DiseaseWatchWindow> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            val trigger = item.getJSONObject("triggerActivity")
            add(
                DiseaseWatchWindow(
                    issueId = item.getString("issueId"),
                    stage = item.getString("stage"),
                    dayRange = item.getJSONArray("dayRange").toIntRange(),
                    triggerActivity = TriggerActivityTemplate(
                        id = trigger.getString("id"),
                        type = trigger.getString("type"),
                        title = trigger.getString("title"),
                        productCategory = trigger.getString("productCategory"),
                        preferredProducts = trigger.optJSONArray("preferredProducts")?.toStringList().orEmpty(),
                        doseNote = trigger.optString("doseNote")
                    )
                )
            )
        }
    }

    private fun JSONObject.toHarvestWindow(): HarvestWindow = HarvestWindow(
        dayRange = getJSONArray("dayRange").toIntRange(),
        yieldPerAcreTonnesBaseline = getDouble("yieldPerAcreTonnesBaseline")
    )

    private fun JSONArray.toIntRange(): IntRange {
        require(length() >= 2) { "dayRange requires start and end values" }
        return getInt(0)..getInt(1)
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (index in 0 until length()) add(getString(index))
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else getDouble(key)
}
