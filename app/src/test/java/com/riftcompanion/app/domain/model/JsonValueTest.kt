package com.riftcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Non-regression tests for JsonValue helpers used in card detail rendering.
 */
class JsonValueTest {

    @Test
    fun `firstText returns string value for matching key`() {
        val attrs = mapOf("rulesText" to JsonValue.Str("Deal 2 damage."))
        val result = attrs.firstText(listOf("rulesText", "rules_text", "rules"))
        assertEquals("Deal 2 damage.", result)
    }

    @Test
    fun `firstText falls through to next key if first is missing`() {
        val attrs = mapOf("rules" to JsonValue.Str("Draw a card."))
        val result = attrs.firstText(listOf("rulesText", "rules_text", "rules"))
        assertEquals("Draw a card.", result)
    }

    @Test
    fun `firstText joins array values with newlines`() {
        val attrs = mapOf(
            "rulesText" to JsonValue.Arr(
                listOf(JsonValue.Str("Line 1"), JsonValue.Str("Line 2"))
            )
        )
        val result = attrs.firstText(listOf("rulesText"))
        assertEquals("Line 1\nLine 2", result)
    }

    @Test
    fun `firstText returns null when no keys match`() {
        val attrs = mapOf("other" to JsonValue.Str("value"))
        val result = attrs.firstText(listOf("rulesText", "rules"))
        assertNull(result)
    }

    @Test
    fun `firstText skips blank strings`() {
        val attrs = mapOf("rulesText" to JsonValue.Str("  "))
        val result = attrs.firstText(listOf("rulesText"))
        assertNull(result)
    }

    @Test
    fun `firstDisplayValue returns string for string value`() {
        val attrs = mapOf("power" to JsonValue.Str("5"))
        assertEquals("5", attrs.firstDisplayValue(listOf("power", "attack")))
    }

    @Test
    fun `firstDisplayValue returns integer string for whole number`() {
        val attrs = mapOf("energyCost" to JsonValue.Num(3.0))
        assertEquals("3", attrs.firstDisplayValue(listOf("energyCost")))
    }

    @Test
    fun `firstDisplayValue returns decimal string for fractional number`() {
        val attrs = mapOf("weight" to JsonValue.Num(3.14))
        assertEquals("3.14", attrs.firstDisplayValue(listOf("weight")))
    }

    @Test
    fun `firstDisplayValue returns Yes for true boolean`() {
        val attrs = mapOf("isToken" to JsonValue.Bool(true))
        assertEquals("Yes", attrs.firstDisplayValue(listOf("isToken")))
    }

    @Test
    fun `firstDisplayValue returns No for false boolean`() {
        val attrs = mapOf("isToken" to JsonValue.Bool(false))
        assertEquals("No", attrs.firstDisplayValue(listOf("isToken")))
    }

    @Test
    fun `firstDisplayValue returns null when key missing`() {
        val attrs = emptyMap<String, JsonValue>()
        assertNull(attrs.firstDisplayValue(listOf("power")))
    }
}
