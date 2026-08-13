package com.example.agriscout.data.repository

import android.content.Context
import android.net.Uri
import com.example.agriscout.data.local.DiseaseCatalogDao
import com.example.agriscout.detection.DetectionFusionEngine
import com.example.agriscout.detection.DetectionResult
import com.example.agriscout.detection.DetectionRule
import com.example.agriscout.detection.ImageDiseaseClassifier
import com.example.agriscout.detection.RuleBasedDetectionEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

class DetectionRepository(
    private val context: Context,
    private val diseaseCatalogDao: DiseaseCatalogDao,
    private val engine: RuleBasedDetectionEngine,
    private val imageClassifier: ImageDiseaseClassifier,
    private val fusionEngine: DetectionFusionEngine
) {
    private var cachedRules: List<DetectionRule>? = null

    suspend fun analyze(
        cropType: String,
        symptoms: String,
        imageUri: String? = null
    ): DetectionResult = withContext(Dispatchers.Default) {
        val rules = loadRules()
        val textResult = enrichFromCatalog(engine.detect(cropType, symptoms, rules))
        val imageResult = imageUri?.let { uriValue ->
            runCatching {
                imageClassifier.classify(Uri.parse(uriValue), context.contentResolver)
            }.getOrNull()
        }
        val fused = fusionEngine.fuse(cropType, textResult, imageResult)
        enrichFromRules(enrichFromCatalog(fused), rules)
    }

    private fun enrichFromRules(result: DetectionResult, rules: List<DetectionRule>): DetectionResult {
        val ruleId = result.matchedRuleId ?: return result
        val rule = rules.firstOrNull { it.id == ruleId } ?: return result
        return result.copy(
            issueType = rule.issueType,
            issueName = rule.issueName,
            treatment = rule.treatment.ifBlank { result.treatment },
            prevention = rule.prevention.ifBlank { result.prevention }
        )
    }

    private suspend fun enrichFromCatalog(baseResult: DetectionResult): DetectionResult {
        val catalogMatch = diseaseCatalogDao.getCatalog().firstOrNull { catalog ->
            catalog.diseaseName.equals(baseResult.issueName, ignoreCase = true) ||
                baseResult.issueName.normalize().contains(catalog.diseaseName.normalize()) ||
                catalog.diseaseName.normalize().contains(baseResult.issueName.normalize())
        }
        return if (catalogMatch == null) {
            baseResult
        } else {
            baseResult.copy(
                treatment = catalogMatch.treatment.ifBlank { baseResult.treatment },
                prevention = catalogMatch.prevention.ifBlank { baseResult.prevention }
            )
        }
    }

    private fun loadRules(): List<DetectionRule> {
        cachedRules?.let { return it }
        val json = context.assets.open("detection_rules.json").bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val rules = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    DetectionRule(
                        id = item.getString("id"),
                        issueType = item.getString("issueType"),
                        issueName = item.getString("issueName"),
                        crops = item.getJSONArray("crops").toStringList(),
                        symptomKeywords = item.getJSONArray("symptomKeywords").toStringList(),
                        severityKeywords = item.optJSONArray("severityKeywords")?.toStringList().orEmpty(),
                        treatment = item.optString("treatment"),
                        prevention = item.optString("prevention")
                    )
                )
            }
        }
        cachedRules = rules
        return rules
    }
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (index in 0 until length()) {
        add(getString(index))
    }
}

private fun String.normalize(): String = trim().lowercase(Locale.getDefault())
