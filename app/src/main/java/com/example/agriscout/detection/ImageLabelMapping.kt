package com.example.agriscout.detection

/**
 * Maps PlantVillage class labels from the on-device CNN to AgriScout detection rules.
 */
object ImageLabelMapping {
    data class LabelInfo(
        val ruleId: String?,
        val issueType: String,
        val issueName: String,
        val cropAliases: List<String>
    )

    private val mappings = mapOf(
        "Potato___Early_blight" to LabelInfo(
            ruleId = "potato-early-blight",
            issueType = IssueTypes.DISEASE,
            issueName = "Potato Early Blight",
            cropAliases = listOf("potato")
        ),
        "Potato___Late_blight" to LabelInfo(
            ruleId = "potato-late-blight",
            issueType = IssueTypes.DISEASE,
            issueName = "Potato Late Blight",
            cropAliases = listOf("potato")
        ),
        "Potato___healthy" to LabelInfo(
            ruleId = null,
            issueType = IssueTypes.UNKNOWN,
            issueName = "Potato appears healthy",
            cropAliases = listOf("potato")
        ),
        "Strawberry___Leaf_scorch" to LabelInfo(
            ruleId = "strawberry-leaf-scorch",
            issueType = IssueTypes.DISEASE,
            issueName = "Strawberry Leaf Scorch",
            cropAliases = listOf("strawberry")
        ),
        "Strawberry___healthy" to LabelInfo(
            ruleId = null,
            issueType = IssueTypes.UNKNOWN,
            issueName = "Strawberry appears healthy",
            cropAliases = listOf("strawberry")
        ),
        "Tomato___Bacterial_spot" to LabelInfo(
            ruleId = "tomato-bacterial-spot",
            issueType = IssueTypes.DISEASE,
            issueName = "Tomato Bacterial Spot",
            cropAliases = listOf("tomato")
        ),
        "Tomato___Early_blight" to LabelInfo(
            ruleId = "tomato-early-blight",
            issueType = IssueTypes.DISEASE,
            issueName = "Tomato Early Blight",
            cropAliases = listOf("tomato")
        ),
        "Tomato___Late_blight" to LabelInfo(
            ruleId = "tomato-late-blight",
            issueType = IssueTypes.DISEASE,
            issueName = "Tomato Late Blight",
            cropAliases = listOf("tomato")
        ),
        "Tomato___Leaf_Mold" to LabelInfo(
            ruleId = "tomato-leaf-mold",
            issueType = IssueTypes.DISEASE,
            issueName = "Tomato Leaf Mold",
            cropAliases = listOf("tomato")
        ),
        "Tomato___Septoria_leaf_spot" to LabelInfo(
            ruleId = "tomato-septoria-leaf-spot",
            issueType = IssueTypes.DISEASE,
            issueName = "Tomato Septoria Leaf Spot",
            cropAliases = listOf("tomato")
        ),
        "Tomato___Spider_mites Two-spotted_spider_mite" to LabelInfo(
            ruleId = "tomato-spider-mites",
            issueType = IssueTypes.PEST,
            issueName = "Tomato Spider Mites",
            cropAliases = listOf("tomato")
        ),
        "Tomato___Target_Spot" to LabelInfo(
            ruleId = "tomato-target-spot",
            issueType = IssueTypes.DISEASE,
            issueName = "Tomato Target Spot",
            cropAliases = listOf("tomato")
        ),
        "Tomato___Tomato_Yellow_Leaf_Curl_Virus" to LabelInfo(
            ruleId = "tomato-yellow-leaf-curl-virus",
            issueType = IssueTypes.DISEASE,
            issueName = "Tomato Yellow Leaf Curl Virus",
            cropAliases = listOf("tomato")
        ),
        "Tomato___Tomato_mosaic_virus" to LabelInfo(
            ruleId = "tomato-mosaic-virus",
            issueType = IssueTypes.DISEASE,
            issueName = "Tomato Mosaic Virus",
            cropAliases = listOf("tomato")
        ),
        "Tomato___healthy" to LabelInfo(
            ruleId = null,
            issueType = IssueTypes.UNKNOWN,
            issueName = "Tomato appears healthy",
            cropAliases = listOf("tomato")
        )
    )

    fun resolve(label: String): LabelInfo? = mappings[label]

    fun matchesCrop(labelInfo: LabelInfo, cropType: String): Boolean {
        val normalizedCrop = cropType.trim().lowercase()
        if (normalizedCrop.isBlank()) return true
        return labelInfo.cropAliases.any { alias ->
            normalizedCrop.contains(alias) || alias.contains(normalizedCrop)
        }
    }

    fun isHealthyLabel(label: String): Boolean = label.endsWith("healthy")
}
