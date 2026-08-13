package com.example.agriscout

import com.example.agriscout.detection.DetectionFusionEngine
import com.example.agriscout.detection.DetectionResult
import com.example.agriscout.detection.ImageClassificationResult
import com.example.agriscout.detection.ImageLabelMapping
import com.example.agriscout.detection.IssueTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionFusionEngineTest {
    private val engine = DetectionFusionEngine()

    private val unknownText = DetectionResult(
        issueType = IssueTypes.UNKNOWN,
        issueName = "Needs expert review",
        confidence = 0,
        matchedRuleId = null,
        matchedKeywords = emptyList(),
        treatment = "Consult an agronomist.",
        prevention = "Continue scouting."
    )

    private val tomatoBlightText = DetectionResult(
        issueType = IssueTypes.DISEASE,
        issueName = "Tomato Late Blight",
        confidence = 72,
        matchedRuleId = "tomato-late-blight",
        matchedKeywords = listOf("leaf blight"),
        treatment = "Apply fungicide.",
        prevention = "Improve airflow."
    )

    @Test
    fun prefersImageWhenTextIsUnknown() {
        val image = imageResult("Tomato___Late_blight", 88)
        val fused = engine.fuse("Tomato", unknownText, image)

        assertEquals("tomato-late-blight", fused.matchedRuleId)
        assertEquals(88, fused.confidence)
        assertTrue(fused.matchedKeywords.any { it.startsWith("image:") })
    }

    @Test
    fun boostsConfidenceWhenTextAndImageAgree() {
        val image = imageResult("Tomato___Late_blight", 80)
        val fused = engine.fuse("Tomato", tomatoBlightText, image)

        assertEquals("tomato-late-blight", fused.matchedRuleId)
        assertEquals(82, fused.confidence)
    }

    @Test
    fun ignoresImageWhenCropDoesNotMatch() {
        val image = imageResult("Potato___Late_blight", 90)
        val fused = engine.fuse("Tomato", unknownText, image)

        assertEquals(unknownText.issueName, fused.issueName)
        assertEquals(0, fused.confidence)
    }

    private fun imageResult(label: String, confidence: Int): ImageClassificationResult {
        val info = ImageLabelMapping.resolve(label)!!
        return ImageClassificationResult(label = label, confidence = confidence, labelInfo = info)
    }
}
