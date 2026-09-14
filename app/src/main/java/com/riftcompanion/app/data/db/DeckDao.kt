package com.riftcompanion.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    fun getAllDecks(): Flow<List<DeckEntity>>

    @Query("SELECT * FROM decks WHERE id = :id")
    suspend fun getDeck(id: String): DeckEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeck(deck: DeckEntity)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeck(id: String)

    @Query("SELECT * FROM deck_entries WHERE deckId = :deckId ORDER BY zone, nameSlug")
    suspend fun getEntriesForDeck(deckId: String): List<DeckEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<DeckEntryEntity>)

    @Query("DELETE FROM deck_entries WHERE deckId = :deckId")
    suspend fun deleteEntriesForDeck(deckId: String)

    @Query("SELECT COUNT(*) FROM deck_entries WHERE deckId = :deckId")
    suspend fun getEntryCount(deckId: String): Int
}
