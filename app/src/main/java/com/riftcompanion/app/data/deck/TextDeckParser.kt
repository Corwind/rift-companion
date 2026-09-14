package com.riftcompanion.app.data.deck

import com.riftcompanion.app.domain.model.DeckZone
import com.riftcompanion.app.domain.model.TextDeckDocument
import com.riftcompanion.app.domain.model.TextDeckEntry

/**
 * Parses Piltover Archive text export format.
 *
 * Format:
 * Legend:
 * 1 Diana, Scorn of the Moon
 *
 * Champion:
 * 1 Diana, Lunari
 *
 * MainDeck:
 * 3 Stupefy
 * ...
 *
 * Battlefields:
 * 1 Startipped Peak
 * ...
 *
 * Runes: (or "Rune Pool:")
 * 7 Chaos Rune
 * ...
 *
 * Sideboard:
 * 2 Bellows Breath
 * ...
 */
object TextDeckParser {

    private val sections = mapOf(
        "legend" to DeckZone.legend,
        "champion" to DeckZone.chosenChampion,
        "maindeck" to DeckZone.main,
        "battlefields" to DeckZone.battlefield,
        "rune pool" to DeckZone.rune,
        "runes" to DeckZone.rune,
        "sideboard" to DeckZone.sideboard,
    )

    fun parse(text: String): Result<TextDeckDocument> = runCatching {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        val lines = normalized.split("\n")
        var currentZone: DeckZone? = null
        val entries = mutableListOf<TextDeckEntry>()

        for ((index, rawLine) in lines.withIndex()) {
            val lineNumber = index + 1
            var line = rawLine.trim()
            if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                line = line.removePrefix("\uFEFF").trim()
            }
            if (line.isEmpty()) continue

            if (line.endsWith(":")) {
                val heading = line.dropLast(1).trim()
                val key = normalizeName(heading)
                currentZone = sections[key]
                    ?: throw IllegalArgumentException("Unknown section: $heading")
                continue
            }

            val zone = currentZone
                ?: throw IllegalArgumentException("Card entry before any section: $line")

            val (quantity, name) = parseEntry(line, lineNumber)
            entries.add(TextDeckEntry(zone, name, quantity, lineNumber))
        }

        if (entries.isEmpty()) {
            throw IllegalArgumentException("No card entries found")
        }

        TextDeckDocument(entries = entries, suggestedDeckName = suggestDeckName(entries))
    }

    private fun parseEntry(line: String, lineNumber: Int): Pair<Int, String> {
        val separator = line.indexOfFirst { it.isWhitespace() }
        if (separator == -1) {
            throw IllegalArgumentException("Line $lineNumber: missing card name")
        }
        val quantityToken = line.substring(0, separator)
        val quantity = quantityToken.toIntOrNull()
            ?: throw IllegalArgumentException("Line $lineNumber: invalid quantity '$quantityToken'")
        if (quantity <= 0) {
            throw IllegalArgumentException("Line $lineNumber: quantity must be positive")
        }
        val name = line.substring(separator).trim()
        if (name.isEmpty()) {
            throw IllegalArgumentException("Line $lineNumber: missing card name")
        }
        return quantity to name
    }

    private fun suggestDeckName(entries: List<TextDeckEntry>): String? {
        val legendNames = entries.filter { it.zone == DeckZone.legend }.map { it.displayName }
        val championNames = entries.filter { it.zone == DeckZone.chosenChampion }.map { it.displayName }
        if (legendNames.size > 1 || championNames.size > 1) return null

        val hero = legendNames.firstOrNull()?.let { heroName(it) }
            ?: championNames.firstOrNull()?.let { heroName(it) }
        return hero?.let { "$it Deck" }
    }

    private fun heroName(displayName: String): String? {
        val candidate = displayName.split(",").firstOrNull()?.trim()
        return if (candidate.isNullOrEmpty()) null else candidate
    }

    private fun normalizeName(name: String): String {
        return name.lowercase().trim()
    }
}
