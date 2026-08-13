package com.example.agriscout.detection

import kotlin.math.min

class DetectionFusionEngine {
    fun fuse(
        cropType: String,
        textResult: DetectionResult,
        imageResult: ImageClassificationResult?
    ): DetectionResult {
        if (imageResult == null) return textResult

        val labelInfo = imageResult.labelInfo
        if (!ImageLabelMapping.matchesCrop(labelInfo, cropType)) {
            return textResult.copy(
                explanation = listOfNotNull(
                    textResult.explanation.takeIf { it.isNotBlank() },
                    "Image label ${imageResult.label} ignored — crop mismatch for $cropType."
                ).joinToString(" ")
            )
        }

        val imageConfidence = imageResult.confidence
        if (imageConfidence < MIN_IMAGE_CONFIDENCE) {
            return textResult.copy(
                explanation = listOfNotNull(
                    textResult.explanation.takeIf { it.isNotBlank() },
                    "Image confidence ${imageConfidence}% below ${MIN_IMAGE_CONFIDENCE}% threshold; kept text/rules result."
                ).joinToString(" ")
            )
        }

        if (ImageLabelMapping.isHealthyLabel(imageResult.label)) {
            return fuseHealthy(textResult, imageResult)
        }

        val imageDetection = DetectionResult(
            issueType = labelInfo.issueType,
            issueName = labelInfo.issueName,
            confidence = imageConfidence,
            matchedRuleId = labelInfo.ruleId,
            matchedKeywords = listOf("image:${imageResult.label}"),
            treatment = textResult.treatment,
            prevention = textResult.prevention,
            explanation = "Image model suggested ${labelInfo.issueName} at ${imageConfidence}% for $cropType.",
            analysisSource = "model"
        )

        if (textResult.matchedRuleId == null || textResult.issueType == IssueTypes.UNKNOWN) {
            return imageDetection.copy(
                explanation = "${imageDetection.explanation} Text rules had no strong match."
            )
        }

        if (textResult.matchedRuleId == labelInfo.ruleId) {
            return textResult.copy(
                confidence = min(96, textResult.confidence + 10),
                matchedKeywords = (textResult.matchedKeywords + imageDetection.matchedKeywords).distinct(),
                explanation = listOfNotNull(
                    textResult.explanation.takeIf { it.isNotBlank() },
                    "Image agreed (${imageResult.label} @ ${imageConfidence}%), confidence boosted."
                ).joinToString(" "),
                analysisSource = "fused"
            )
        }

        return if (imageConfidence >= textResult.confidence) {
            imageDetection.copy(
                explanation = "${imageDetection.explanation} Preferred over text match ${textResult.issueName} (${textResult.confidence}%).",
                analysisSource = "fused"
            )
        } else {
            textResult.copy(
                explanation = listOfNotNull(
                    textResult.explanation.takeIf { it.isNotBlank() },
                    "Kept text/rules over image ${imageResult.label} (${imageConfidence}%)."
                ).joinToString(" "),
                analysisSource = "fused"
            )
        }
    }

    private fun fuseHealthy(
        textResult: DetectionResult,
        imageResult: ImageClassificationResult
    ): DetectionResult {
        if (textResult.matchedRuleId != null && textResult.issueType != IssueTypes.UNKNOWN) {
            return textResult.copy(
                matchedKeywords = (textResult.matchedKeywords + "image:${imageResult.label}").distinct(),
                prevention = listOfNotNull(
                    textResult.prevention.takeIf { it.isNotBlank() },
                    "Image scan suggests the plant may be healthy; continue scouting to confirm."
                ).joinToString(" "),
                explanation = listOfNotNull(
                    textResult.explanation.takeIf { it.isNotBlank() },
                    "Image looks healthy (${imageResult.confidence}%), but symptom rules still matched — prefer field confirmation."
                ).joinToString(" "),
                analysisSource = "fused"
            )
        }
        return DetectionResult(
            issueType = IssueTypes.UNKNOWN,
            issueName = imageResult.labelInfo.issueName,
            confidence = imageResult.confidence,
            matchedRuleId = null,
            matchedKeywords = listOf("image:${imageResult.label}"),
            treatment = "No immediate treatment needed. Monitor the crop and record any new symptoms.",
            prevention = "Continue regular scouting and maintain good field hygiene.",
            explanation = "Image model suggests healthy tissue at ${imageResult.confidence}%. Not a guarantee — keep scouting.",
            analysisSource = "model"
        )
    }

    companion object {
        private const val MIN_IMAGE_CONFIDENCE = 55
    }
}
