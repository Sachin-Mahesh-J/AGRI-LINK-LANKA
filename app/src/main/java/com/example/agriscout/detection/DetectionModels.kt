package com.example.agriscout.detection

object IssueTypes {
    const val DISEASE = "DISEASE"
    const val PEST = "PEST"
    const val NUTRIENT = "NUTRIENT"
    const val UNKNOWN = "UNKNOWN"
}

data class DetectionRule(
    val id: String,
    val issueType: String,
    val issueName: String,
    val crops: List<String>,
    val symptomKeywords: List<String>,
    val severityKeywords: List<String>,
    val treatment: String,
    val prevention: String
)

data class DetectionResult(
    val issueType: String,
    val issueName: String,
    val confidence: Int,
    val matchedRuleId: String?,
    val matchedKeywords: List<String>,
    val treatment: String,
    val prevention: String,
    /** Short practical explanation of inputs and why this match was chosen. */
    val explanation: String = "",
    /** Layer that produced the primary signal: rules, model, or fused. */
    val analysisSource: String = "rules"
) {
    val recommendation: String
        get() = listOf(
            explanation.takeIf { it.isNotBlank() }?.let { "Why: $it" },
            treatment.takeIf { it.isNotBlank() }?.let { "Treatment: $it" },
            prevention.takeIf { it.isNotBlank() }?.let { "Prevention: $it" }
        ).filterNotNull().joinToString("\n")

    /** Structured composition for officers: issue → need → action → category. */
    fun decisionSupportSummary(productCategory: String = "Crop protection"): String = buildString {
        appendLine("Issue: $issueName ($issueType)")
        appendLine(
            "Need: ${
                when (issueType) {
                    IssueTypes.DISEASE -> "Disease management and canopy hygiene"
                    IssueTypes.PEST -> "Pest pressure reduction"
                    IssueTypes.NUTRIENT -> "Nutrient correction"
                    else -> "Expert confirmation and continued scouting"
                }
            }"
        )
        appendLine("Action: ${treatment.ifBlank { "Record evidence and consult an agronomist before treating." }}")
        append("Category: $productCategory (confirm approved products locally — not a guaranteed product pick)")
        if (explanation.isNotBlank()) {
            appendLine()
            append("Why: $explanation")
        }
        appendLine()
        append("Certainty: $confidence% · Source: $analysisSource · Decision support only.")
    }
}
