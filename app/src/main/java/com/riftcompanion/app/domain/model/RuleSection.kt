package com.riftcompanion.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A major section of the Riftbound Core Rules (e.g., "100. Game Concepts").
 * Contains sub-sections (e.g., "110. Setup Process") which hold the actual content.
 */
@Serializable
data class RuleSection(
    val id: String,
    val sectionId: String? = null,
    val title: String,
    val blocks: List<RuleBlock> = emptyList(),
    val subSections: List<RuleSubSection> = emptyList(),
)

@Serializable
data class RuleSubSection(
    val id: String,
    val title: String,
    val blocks: List<RuleBlock> = emptyList(),
)

@Serializable
data class RulesData(
    val intro: String? = null,
    val sections: List<RuleSection> = emptyList(),
    val keywords: Map<String, String> = emptyMap(),
    val sectionIndex: Map<String, String> = emptyMap(),
)

@Serializable
sealed class RuleBlock {
    @Serializable
    @SerialName("paragraph")
    data class Paragraph(val segments: List<TextSegment>) : RuleBlock()

    @Serializable
    @SerialName("heading")
    data class Heading(val text: String) : RuleBlock()

    @Serializable
    @SerialName("bullets")
    data class Bullets(val items: List<List<TextSegment>>) : RuleBlock()

    @Serializable
    @SerialName("numbered")
    data class Numbered(val items: List<List<TextSegment>>) : RuleBlock()

    @Serializable
    @SerialName("table")
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
    ) : RuleBlock()
}

@Serializable
data class TextSegment(
    val type: String,  // "text", "bold", "italic"
    val text: String,
)
