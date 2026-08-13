package com.example.agriscout

import com.example.agriscout.detection.DetectionRule
import com.example.agriscout.detection.IssueTypes
import com.example.agriscout.detection.RuleBasedDetectionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedDetectionEngineTest {
    private val engine = RuleBasedDetectionEngine()
    private val rules = listOf(
        DetectionRule(
            id = "maize-fall-armyworm",
            issueType = IssueTypes.PEST,
            issueName = "Fall Armyworm",
            crops = listOf("maize", "corn"),
            symptomKeywords = listOf("frass", "whorl damage", "ragged leaves"),
            severityKeywords = listOf("severe"),
            treatment = "Target treatment into the whorl.",
            prevention = "Scout weekly."
        ),
        DetectionRule(
            id = "rice-blast",
            issueType = IssueTypes.DISEASE,
            issueName = "Rice Blast",
            crops = listOf("rice"),
            symptomKeywords = listOf("leaf spots", "gray center"),
            severityKeywords = listOf("rapid"),
            treatment = "Use registered fungicide.",
            prevention = "Avoid excess nitrogen."
        )
    )

    @Test
    fun detectsPestFromCropAndSymptoms() {
        val result = engine.detect("Maize", "Fresh frass with whorl damage and ragged leaves", rules)

        assertEquals(IssueTypes.PEST, result.issueType)
        assertEquals("Fall Armyworm", result.issueName)
        assertEquals("maize-fall-armyworm", result.matchedRuleId)
        assertTrue(result.confidence >= 80)
        assertTrue(result.explanation.contains("maize-fall-armyworm"))
        assertEquals("rules", result.analysisSource)
    }

    @Test
    fun returnsUnknownWhenSymptomsDoNotMatchRules() {
        val result = engine.detect("Onion", "Plant looks unusual", rules)

        assertEquals(IssueTypes.UNKNOWN, result.issueType)
        assertEquals(0, result.confidence)
    }
}
