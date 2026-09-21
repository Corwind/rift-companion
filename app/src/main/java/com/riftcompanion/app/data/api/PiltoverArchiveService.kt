package com.riftcompanion.app.data.api

import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.CardPrintingDao
import com.riftcompanion.app.data.db.DeckDao
import com.riftcompanion.app.data.db.DeckEntryEntity
import com.riftcompanion.app.data.db.DeckEntity
import com.riftcompanion.app.data.repository.RiftRepository
import com.riftcompanion.app.domain.model.DeckZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs inventory and deck definitions from CardNexus (source of truth) to Piltover Archive.
 *
 * Flow:
 * 1. Read from CardNexus (re-sync inventory + locations)
 * 2. Fetch all PA cards, build cardName → cardId map
 * 3. Push inventory as collection to Piltover Archive
 * 4. Push deck definitions to Piltover Archive (create or update)
 * 5. Pull decks from Piltover Archive that don't exist in the app → create them locally
 * 6. Link decks between app and Piltover Archive (store piltoverArchiveId on DeckEntity)
 */
@Singleton
class PiltoverArchiveService @Inject constructor(
    private val piltoverArchiveClient: PiltoverArchiveClient,
    private val repository: RiftRepository,
    private val cardNexusClient: CardNexusClient,
    private val cardIdentityDao: CardIdentityDao,
    private val cardPrintingDao: CardPrintingDao,
    private val deckDao: DeckDao,
) {
    data class SyncResult(
        val inventorySynced: Boolean,
        val cardsMapped: Int,
        val collectionEntriesPushed: Int,
        val decksPushed: Int,
        val decksPulled: Int,
        val errors: List<String> = emptyList(),
    )

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 1. Re-sync from CardNexus (source of truth)
        try {
            android.util.Log.d("PiltoverSync", "Step 1: CardNexus sync...")
            repository.synchronize().getOrThrow()
            android.util.Log.d("PiltoverSync", "Step 1: CardNexus sync done")
        } catch (e: Exception) {
            android.util.Log.d("PiltoverSync", "Step 1 FAILED: ${e.message}")
            errors.add("CardNexus sync failed: ${e.message}")
        }

        // 2. Fetch all PA cards and build name → cardId map
        android.util.Log.d("PiltoverSync", "Step 2: Fetching PA cards...")
        val paCardMap = try {
            val map = piltoverArchiveClient.fetchAllCards().getOrThrow()
            android.util.Log.d("PiltoverSync", "Step 2: Got ${map.size} PA cards")
            map
        } catch (e: Exception) {
            android.util.Log.d("PiltoverSync", "Step 2 FAILED: ${e.message}")
            errors.add("PA card fetch failed: ${e.message}")
            return@withContext SyncResult(
                inventorySynced = errors.none { it.startsWith("CardNexus") },
                cardsMapped = 0,
                collectionEntriesPushed = 0,
                decksPushed = 0,
                decksPulled = 0,
                errors = errors,
            )
        }

        // Build our nameSlug → displayName map for matching
        val identities = cardIdentityDao.getAllCards()
        android.util.Log.d("PiltoverSync", "Step 2b: ${identities.size} local card identities")
        val slugToPaCardId = mutableMapOf<String, String>()
        for (identity in identities) {
            val paCardId = paCardMap[identity.displayName]
            if (paCardId != null) {
                slugToPaCardId[identity.nameSlug] = paCardId
            }
        }
        android.util.Log.d("PiltoverSync", "Step 2c: mapped ${slugToPaCardId.size} slugs to PA card IDs")

        // 3. Push inventory as collection to Piltover Archive
        var collectionEntriesPushed = 0
        try {
            val lines = cardNexusClient.fetchAllInventoryLines().getOrThrow()
            val printings = cardPrintingDao.getAll().first()
            val printingByProduct = printings.associateBy { it.productID }

            val collectionUpdates = mutableListOf<PiltoverArchiveClient.CollectionUpdate>()
            for (line in lines) {
                if (line.quantity <= 0) continue
                val printing = printingByProduct[line.productId] ?: continue
                val paCardId = slugToPaCardId[printing.nameSlug] ?: continue
                collectionUpdates.add(PiltoverArchiveClient.CollectionUpdate(
                    cardId = paCardId,
                    variantId = null,
                    quantity = line.quantity,
                ))
            }

            // Push in batches of 50
            for (batch in collectionUpdates.chunked(50)) {
                piltoverArchiveClient.updateCollection(batch).getOrThrow()
            }
            collectionEntriesPushed = collectionUpdates.size
        } catch (e: Exception) {
            errors.add("Collection push failed: ${e.message}")
        }

        // 4. Push deck definitions to Piltover Archive (create or update)
        // Only push decks that exist in the app — do NOT pull public decks from PA
        var decksPushed = 0
        try {
            val decks = deckDao.getAllDecks().first()
            for (deck in decks) {
                val entries = deckDao.getEntriesForDeck(deck.id)
                val paDeck = buildPaDeck(deck.name, entries, slugToPaCardId)
                if (deck.piltoverArchiveId != null) {
                    piltoverArchiveClient.updateDeck(deck.piltoverArchiveId, paDeck).getOrThrow()
                } else {
                    val created = piltoverArchiveClient.createDeck(paDeck).getOrThrow()
                    deckDao.insertDeck(deck.copy(piltoverArchiveId = created.id))
                }
                decksPushed++
            }
        } catch (e: Exception) {
            errors.add("Deck push failed: ${e.message}")
        }

        SyncResult(
            inventorySynced = errors.none { it.startsWith("CardNexus") },
            cardsMapped = slugToPaCardId.size,
            collectionEntriesPushed = collectionEntriesPushed,
            decksPushed = decksPushed,
            decksPulled = 0,
            errors = errors,
        )
    }

    private fun buildPaDeck(
        name: String,
        entries: List<DeckEntryEntity>,
        slugToPaCardId: Map<String, String>,
    ): PiltoverArchiveClient.DeckWrite {
        val champions = mutableListOf<PiltoverArchiveClient.PaDeckCardEntry>()
        val battlefields = mutableListOf<PiltoverArchiveClient.PaDeckCardEntry>()
        val runes = mutableListOf<PiltoverArchiveClient.PaDeckCardEntry>()
        val maindeck = mutableListOf<PiltoverArchiveClient.PaDeckCardEntry>()
        val sideboard = mutableListOf<PiltoverArchiveClient.PaDeckCardEntry>()

        for (entry in entries) {
            val paCardId = slugToPaCardId[entry.nameSlug] ?: continue
            val paEntry = PiltoverArchiveClient.PaDeckCardEntry(
                cardId = paCardId,
                variantId = null,
                quantity = entry.quantity,
            )
            val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
            when (zone) {
                DeckZone.legend -> {} // Legend handled separately
                DeckZone.chosenChampion -> champions.add(paEntry)
                DeckZone.battlefield -> battlefields.add(paEntry)
                DeckZone.rune -> runes.add(paEntry)
                DeckZone.main -> maindeck.add(paEntry)
                DeckZone.sideboard -> sideboard.add(paEntry)
            }
        }

        return PiltoverArchiveClient.DeckWrite(
            name = name,
            champions = champions,
            battlefields = battlefields,
            runes = runes,
            maindeck = maindeck,
            sideboard = sideboard,
        )
    }
}
