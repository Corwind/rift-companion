package com.riftcompanion.app.data.deck

import com.riftcompanion.app.domain.model.CardIdentityInfo
import com.riftcompanion.app.domain.model.DeckValidationIssue
import com.riftcompanion.app.domain.model.DeckZone
import com.riftcompanion.app.domain.model.ValidationSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckRulesEngineTest {

    private fun entry(slug: String, zone: DeckZone = DeckZone.main, qty: Int = 1) =
        DeckEntryData(zone = zone, nameSlug = slug, quantity = qty)

    private fun identity(slug: String, name: String = slug, maxCopies: Int? = null) =
        CardIdentityInfo(
            nameSlug = slug,
            displayName = name,
            maxCopies = maxCopies,
        )

    private fun validate(
        entries: List<DeckEntryData>,
        identities: Map<String, CardIdentityInfo> = emptyMap(),
    ) = DeckRulesEngine.validate(entries, identities)

    @Test
    fun `exceeding standard copy limit of 3 produces error`() {
        val entries = listOf(
            entry("card-a", qty = 4),
        )
        val identities = mapOf("card-a" to identity("card-a", "Card A"))

        val issues = validate(entries, identities)
        assertTrue(issues.any { it.code == "copy_limit" })
    }

    @Test
    fun `per-card maxCopies of 1 is enforced`() {
        val entries = listOf(
            entry("card-a", qty = 2),
        )
        val identities = mapOf("card-a" to identity("card-a", "Card A", maxCopies = 1))

        val issues = validate(entries, identities)
        assertTrue(issues.any { it.code == "copy_limit" })
        val copyIssue = issues.first { it.code == "copy_limit" }
        assertTrue(copyIssue.message.contains("limit is 1"))
    }

    @Test
    fun `per-card maxCopies of 2 allows 2 copies but not 3`() {
        val identities = mapOf("card-a" to identity("card-a", "Card A", maxCopies = 2))

        val issuesOk = validate(listOf(entry("card-a", qty = 2)), identities)
        assertFalse(issuesOk.any { it.code == "copy_limit" })

        val issuesOver = validate(listOf(entry("card-a", qty = 3)), identities)
        assertTrue(issuesOver.any { it.code == "copy_limit" })
        val copyIssue = issuesOver.first { it.code == "copy_limit" }
        assertTrue(copyIssue.message.contains("limit is 2"))
    }

    @Test
    fun `null maxCopies falls back to ruleset default of 3`() {
        val entries = listOf(
            entry("card-a", qty = 3),
        )
        val identities = mapOf("card-a" to identity("card-a", "Card A", maxCopies = null))

        val issues = validate(entries, identities)
        assertFalse(issues.any { it.code == "copy_limit" })
    }

    @Test
    fun `copy limit counts across main and sideboard combined`() {
        val entries = listOf(
            entry("card-a", zone = DeckZone.main, qty = 2),
            entry("card-a", zone = DeckZone.sideboard, qty = 2),
        )
        val identities = mapOf("card-a" to identity("card-a", "Card A"))

        val issues = validate(entries, identities)
        assertTrue(issues.any { it.code == "copy_limit" })
    }

    @Test
    fun `spiderlings is exempt from copy limit`() {
        val entries = listOf(
            entry("spiderlings", qty = 10),
        )
        val identities = mapOf("spiderlings" to identity("spiderlings", "Spiderlings"))

        val issues = validate(entries, identities)
        assertFalse(issues.any { it.code == "copy_limit" })
    }

    @Test
    fun `zero quantity entries produce invalid_quantity error`() {
        val entries = listOf(entry("card-a", qty = 0))
        val identities = mapOf("card-a" to identity("card-a"))

        val issues = validate(entries, identities)
        assertTrue(issues.any { it.code == "invalid_quantity" })
    }

    @Test
    fun `unknown cards produce unknown_cards error`() {
        val entries = listOf(entry("unknown-card"))
        val identities = emptyMap<String, CardIdentityInfo>()

        val issues = validate(entries, identities)
        assertTrue(issues.any { it.code == "unknown_cards" })
    }

    @Test
    fun `copy limit error message includes card display name`() {
        val entries = listOf(entry("card-a", qty = 5))
        val identities = mapOf("card-a" to identity("card-a", "Beautiful Card"))

        val issues = validate(entries, identities)
        val copyIssue = issues.first { it.code == "copy_limit" }
        assertTrue(copyIssue.message.contains("Beautiful Card"))
    }

    @Test
    fun `per-card maxCopies error message includes the effective limit`() {
        val entries = listOf(entry("card-a", qty = 4))
        val identities = mapOf("card-a" to identity("card-a", "Card A", maxCopies = 2))

        val issues = validate(entries, identities)
        val copyIssue = issues.first { it.code == "copy_limit" }
        assertTrue(copyIssue.message.contains("limit is 2"))
        assertFalse(copyIssue.message.contains("limit is 3"))
    }
}
