package com.riftcompanion.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val state: String,
    val rulesetId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val linkedLocationName: String? = null,
)

@Entity(tableName = "deck_entries")
data class DeckEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: String,
    val zone: String,
    val nameSlug: String,
    val quantity: Int,
    val preferredProductId: Long? = null,
    val preferredFinish: String? = null,
    val preferredLanguage: String? = null,
)
