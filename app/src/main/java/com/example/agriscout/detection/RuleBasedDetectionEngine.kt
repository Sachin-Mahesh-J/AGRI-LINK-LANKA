package com.example.agriscout.detection

import java.util.Locale
import kotlin.math.min

class RuleBasedDetectionEngine {
    fun detect(cropType: String, symptoms: String, rules: List<DetectionRule>): DetectionResult {
        val normalizedCrop = cropType.normalize()
        val normalizedSymptoms = symptoms.normalize()
        if (normalizedCrop.isBlank() || normalizedSymptoms.isBlank()) {
            return unknownResult("Crop type and symptoms are required for rule-based analysis.")
        }

        val ranked = rules.mapNotNull { rule ->
            val cropMatches = rule.crops.any { crop ->
                val normalizedRuleCrop = crop.normalize()
                normalizedRuleCrop == "all" ||
                    normalizedCrop.contains(normalizedRuleCrop) ||
                    normalizedRuleCrop.contains(normalizedCrop)
            }
            val matchedKeywords = rule.symptomKeywords
                .filter { keyword -> normalizedSymptoms.contains(keyword.normalize()) }
                .distinct()
            val severityMatches = rule.severityKeywords.count { keyword -> normalizedSymptoms.contains(keyword.normalize()) }

            if (!cropMatches && matchedKeywords.isEmpty()) return@mapNotNull null

            val cropScore = if (cropMatches) 30 else 0
            val symptomScore = min(55, matchedKeywords.size * 18)
            val severityScore = min(15, severityMatches * 5)
            val confidence = (cropScore + symptomScore + severityScore).coerceIn(20, 96)
            ScoredRule(rule, confidence, matchedKeywords, cropMatches, severityMatches)
        }.sortedWith(
            compareByDescending<ScoredRule> { it.confidence }
                .thenByDescending { it.matchedKeywords.size }
        )

        val best = ranked.firstOrNull() ?: return unknownResult(
            "No rule matched these symptoms for $cropType. Capture clearer notes or a leaf photo for expert review."
        )
        val explanation = buildString {
            append("Matched rule ${best.rule.id}")
            if (best.cropMatches) append(" for crop $cropType")
            if (best.matchedKeywords.isNotEmpty()) {
                append(" using keywords: ${best.matchedKeywords.joinToString(", ")}")
            }
            if (best.severityMatches > 0) append(" (severity cues noted)")
            append(". Certainty ${best.confidence}% — decision support only.")
        }
        return DetectionResult(
            issueType = best.rule.issueType,
            issueName = best.rule.issueName,
            confidence = best.confidence,
            matchedRuleId = best.rule.id,
            matchedKeywords = best.matchedKeywords,
            treatment = best.rule.treatment,
            prevention = best.rule.prevention,
            explanation = explanation,
            analysisSource = "rules"
        )
    }

    private fun unknownResult(explanation: String): DetectionResult = DetectionResult(
        issueType = IssueTypes.UNKNOWN,
        issueName = "Needs expert review",
        confidence = 0,
        matchedRuleId = null,
        matchedKeywords = emptyList(),
        treatment = "Record clear symptoms, capture image evidence, and consult an agronomist before applying treatment.",
        prevention = "Continue regular scouting and isolate severely affected plants where practical.",
        explanation = explanation,
        analysisSource = "rules"
    )

    private data class ScoredRule(
        val rule: DetectionRule,
        val confidence: Int,
        val matchedKeywords: List<String>,
        val cropMatches: Boolean,
        val severityMatches: Int
    )
}

private fun String.normalize(): String = trim().lowercase(Locale.getDefault())
