package com.riftcompanion.app.data.api

import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.CardPrintingDao
import com.riftcompanion.app.data.db.DeckDao
import com.riftcompanion.app.data.db.DeckEntryEntity
import com.riftcompanion.app.data.db.DeckEntity
import com.riftcompanion.app.data.db.InventoryLineDao
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
 * 2. Fetch all PA card variants, build name → (cardId, variantId) map
 * 3. Push inventory as collection to Piltover Archive (POST /collection with variantId)
 * 4. Push deck definitions to Piltover Archive (create or update, entries need cardId + variantId)
 */
@Singleton
class PiltoverArchiveService @Inject constructor(
    private val piltoverArchiveClient: PiltoverArchiveClient,
    private val repository: RiftRepository,
    private val cardIdentityDao: CardIdentityDao,
    private val cardPrintingDao: CardPrintingDao,
    private val inventoryLineDao: InventoryLineDao,
    private val deckDao: DeckDao,
) {
    data class SyncResult(
        val inventorySynced: Boolean,
        val cardsMapped: Int,
        val collectionEntriesPushed: Int,
        val decksPushed: Int,
        val decksPulled: Int = 0,
        val errors: List<String> = emptyList(),
    )

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 1. Re-sync from CardNexus (source of truth) — best effort, continue with local data if rate-limited
        try {
            android.util.Log.d("PiltoverSync", "Step 1: CardNexus sync...")
            repository.synchronize().getOrThrow()
            android.util.Log.d("PiltoverSync", "Step 1: CardNexus sync done")
        } catch (e: Exception) {
            android.util.Log.d("PiltoverSync", "Step 1: CardNexus sync failed (will use local data): ${e.message}")
            // Don't add to errors — we can still sync local data to PA
        }

        // 2. Fetch all PA cards and build variantNumber → (cardId, variantId) map
        android.util.Log.d("PiltoverSync", "Step 2: Fetching PA cards...")
        val paVariantMap = try {
            val map = piltoverArchiveClient.fetchAllCards().getOrThrow()
            android.util.Log.d("PiltoverSync", "Step 2: Got ${map.size} PA card variants")
            map
        } catch (e: Exception) {
            android.util.Log.d("PiltoverSync", "Step 2 FAILED: ${e.message}")
            errors.add("PA card fetch failed: ${e.message}")
            return@withContext SyncResult(
                inventorySynced = errors.none { it.startsWith("CardNexus") },
                cardsMapped = 0,
                collectionEntriesPushed = 0,
                decksPushed = 0,
                errors = errors,
            )
        }

        // Build productId → (cardId, variantId) map using variantNumber matching
        // Each printing has a specific expansionSlug + printNumber → exact PA variantNumber
        val printings = cardPrintingDao.getAll().first()
        android.util.Log.d("PiltoverSync", "Step 2b: ${printings.size} local printings")
        // Map expansionSlug → PA set prefix
        val expansionToPrefix = mapOf(
            "arcane-box-set" to "ARC",
            "origins-main-set" to "OGN",
            "origins-promo-cards" to "OGN",
            "origins-proving-grounds" to "OGS",
            "radiance" to "RAD",
            "spiritforged" to "SFD",
            "spiritforged-promo-cards" to "SFD",
            "unleashed" to "UNL",
            "unleashed-promo-cards" to "UNL",
            "vendetta" to "VEN",
            "vendetta-promo-cards" to "VEN",
            "riftbound-promos" to "WRLD25",
        )
        // Case-insensitive PA variant lookup
        val paVariantByLower = paVariantMap.mapKeys { (k, _) -> k.lowercase() }
        
        // Map productId → (cardId, variantId) for exact printing match
        val productToPaIds = mutableMapOf<Long, Pair<String, String>>()
        var unmatchedCount = 0
        for (printing in printings) {
            val prefix = expansionToPrefix[printing.expansionSlug]
            val printNumber = printing.printNumber
            if (prefix != null && !printNumber.isNullOrBlank()) {
                val variantNumber = "$prefix-$printNumber"
                val paIds = paVariantByLower[variantNumber.lowercase()]
                if (paIds != null) {
                    productToPaIds[printing.productID] = paIds
                } else {
                    unmatchedCount++
                }
            }
        }
        android.util.Log.d("PiltoverSync", "Step 2c: mapped ${productToPaIds.size} productIds to PA card IDs, $unmatchedCount unmatched")
        
        // Also build nameSlug → (cardId, variantId) for deck sync (use first matched printing per slug)
        val slugToPaIds = mutableMapOf<String, Pair<String, String>>()
        for (printing in printings) {
            val paIds = productToPaIds[printing.productID]
            if (paIds != null && printing.nameSlug !in slugToPaIds) {
                slugToPaIds[printing.nameSlug] = paIds
            }
        }

        // 3. Sync collection: read local inventory → export PA → PATCH/POST/DELETE
        var collectionEntriesPushed = 0
        var paCollectionEntries: List<PiltoverArchiveClient.CollectionEntry> = emptyList()
        try {
            android.util.Log.d("PiltoverSync", "Step 3: Reading local inventory...")
            val lines = inventoryLineDao.getAll().first()
            android.util.Log.d("PiltoverSync", "Step 3: Got ${lines.size} local inventory lines")

            // Export existing PA collection
            val paCollectionResult = piltoverArchiveClient.getCollection()
            if (paCollectionResult.isFailure) {
                android.util.Log.d("PiltoverSync", "Step 3: collection export failed: ${paCollectionResult.exceptionOrNull()?.message}")
            }
            val paCollection = paCollectionResult.getOrNull() ?: emptyList()
            paCollectionEntries = paCollection
            val paVariantIds = paCollection.mapNotNull { it.variantId }.toSet()
            android.util.Log.d("PiltoverSync", "Step 3: ${paCollection.size} existing PA collection entries")

            // Build CN variantId → quantity map (using exact productId → variantId mapping)
            val cnQuantitiesByVariantId = mutableMapOf<String, Int>()
            for (line in lines) {
                if (line.quantity <= 0) continue
                val paIds = productToPaIds[line.productId] ?: continue
                cnQuantitiesByVariantId[paIds.second] = (cnQuantitiesByVariantId[paIds.second] ?: 0) + line.quantity
            }
            android.util.Log.d("PiltoverSync", "Step 3: ${cnQuantitiesByVariantId.size} unique cards to sync")

            // PATCH existing entries with updated quantities, DELETE removed entries
            var patched = 0
            var created = 0
            var deleted = 0
            for (entry in paCollection) {
                val entryVariantId = entry.variantId ?: continue
                val entryId = entry.id ?: continue
                val cnQty = cnQuantitiesByVariantId[entryVariantId]
                if (cnQty != null) {
                    if (cnQty != entry.quantity) {
                        piltoverArchiveClient.updateCollectionEntry(entryId, cnQty).getOrThrow()
                        patched++
                    }
                } else {
                    // In PA but not in CN — delete
                    piltoverArchiveClient.deleteCollectionEntry(entryId).getOrThrow()
                    deleted++
                }
            }

            // POST new entries that don't exist in PA
            for ((variantId, quantity) in cnQuantitiesByVariantId) {
                if (variantId !in paVariantIds) {
                    piltoverArchiveClient.createCollectionEntry(
                        PiltoverArchiveClient.CollectionUpdate(variantId, quantity)
                    ).getOrThrow()
                    created++
                }
            }

            collectionEntriesPushed = cnQuantitiesByVariantId.size
            android.util.Log.d("PiltoverSync", "Step 3: done — patched=$patched created=$created deleted=$deleted total=${cnQuantitiesByVariantId.size}")
        } catch (e: Exception) {
            android.util.Log.d("PiltoverSync", "Step 3 FAILED: ${e.message}")
            errors.add("Collection push failed: ${e.message}")
        }

        // 4. Push deck definitions to Piltover Archive (create or update)
        var decksPushed = 0
        try {
            val decks = deckDao.getAllDecks().first()
            android.util.Log.d("PiltoverSync", "Step 4: ${decks.size} decks to push")
            for (deck in decks) {
                val entries = deckDao.getEntriesForDeck(deck.id)
                val paDeck = buildPaDeck(deck.name, entries, slugToPaIds)
                android.util.Log.d("PiltoverSync", "Step 4: pushing deck '${deck.name}' (${entries.size} entries)")
                if (deck.piltoverArchiveId != null) {
                    val updated = piltoverArchiveClient.updateDeckSafe(deck.piltoverArchiveId, paDeck).getOrNull()
                } else {
                    val created = piltoverArchiveClient.createDeck(paDeck).getOrThrow()
                    deckDao.insertDeck(deck.copy(piltoverArchiveId = created.id))
                    android.util.Log.d("PiltoverSync", "Step 4: created deck in PA with id=${created.id}")
                }
                decksPushed++
            }
            android.util.Log.d("PiltoverSync", "Step 4: done, pushed $decksPushed decks")
        } catch (e: Exception) {
            android.util.Log.d("PiltoverSync", "Step 4 FAILED: ${e.message}")
            errors.add("Deck push failed: ${e.message}")
        }

        // 5. Pull user's decks from PA that don't exist in the app
        var decksPulled = 0
        try {
            val localDecks = deckDao.getAllDecks().first()
            val knownPaIds = localDecks.mapNotNull { it.piltoverArchiveId }.toSet()

            // Use /decks/my to get all our decks (including private/draft)
            val paDecks = piltoverArchiveClient.getMyDecks().getOrNull() ?: emptyList()
            android.util.Log.d("PiltoverSync", "Step 5: ${paDecks.size} PA decks, ${knownPaIds.size} already linked")

            // Reverse map: variantId → nameSlug
            val paIdToSlug = mutableMapOf<String, String>()
            for ((slug, paIds) in slugToPaIds) {
                paIdToSlug[paIds.second] = slug
                paIdToSlug[paIds.first] = slug
            }

            for (paDeck in paDecks) {
                if (paDeck.id in knownPaIds) continue

                // Fetch full deck detail
                val detail = piltoverArchiveClient.getDeck(paDeck.id).getOrNull() ?: continue

                // Create deck in app
                val deckId = java.util.UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                deckDao.insertDeck(DeckEntity(
                    id = deckId,
                    name = detail.name,
                    state = "planned",
                    rulesetId = "riftbound",
                    createdAt = now,
                    updatedAt = now,
                    piltoverArchiveId = detail.id,
                ))

                // Create entries
                val entries = mutableListOf<DeckEntryEntity>()
                
                // Add legend entry
                val legendId = detail.legend?.id
                if (legendId != null) {
                    val legendSlug = paIdToSlug[legendId]
                    if (legendSlug != null) {
                        entries.add(DeckEntryEntity(
                            deckId = deckId,
                            zone = DeckZone.legend.name,
                            nameSlug = legendSlug,
                            quantity = 1,
                        ))
                    } else {
                        android.util.Log.d("PiltoverSync", "Step 5: could not map legend variantId=$legendId to slug")
                    }
                }
                
                fun addEntries(zone: DeckZone, cards: List<PiltoverArchiveClient.PaDeckCardEntry>) {
                    for (card in cards) {
                        val nameSlug = paIdToSlug[card.cardId] ?: continue
                        entries.add(DeckEntryEntity(
                            deckId = deckId,
                            zone = zone.name,
                            nameSlug = nameSlug,
                            quantity = card.quantity ?: 1,
                        ))
                    }
                }
                detail.champions.let { addEntries(DeckZone.chosenChampion, it) }
                detail.battlefields.let { addEntries(DeckZone.battlefield, it) }
                detail.runes.let { addEntries(DeckZone.rune, it) }
                detail.maindeck.let { addEntries(DeckZone.main, it) }
                detail.sideboard.let { addEntries(DeckZone.sideboard, it) }
                if (entries.isNotEmpty()) {
                    deckDao.insertEntries(entries)
                }
                decksPulled++
                android.util.Log.d("PiltoverSync", "Step 5: pulled deck '${detail.name}' with ${entries.size} entries")
            }
            android.util.Log.d("PiltoverSync", "Step 5: done, pulled $decksPulled decks")
        } catch (e: Exception) {
            android.util.Log.d("PiltoverSync", "Step 5 FAILED: ${e.message}")
            errors.add("Deck pull failed: ${e.message}")
        }

        SyncResult(
            inventorySynced = errors.none { it.startsWith("CardNexus") },
            cardsMapped = slugToPaIds.size,
            collectionEntriesPushed = collectionEntriesPushed,
            decksPushed = decksPushed,
            errors = errors,
        )
    }

    private fun buildPaDeck(
        name: String,
        entries: List<DeckEntryEntity>,
        slugToPaIds: Map<String, Pair<String, String>>,
    ): PiltoverArchiveClient.DeckWrite {
        val champions = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        val battlefields = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        val runes = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        val maindeck = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        val sideboard = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        var legendId: String? = null

        for (entry in entries) {
            val paIds = slugToPaIds[entry.nameSlug] ?: continue
            val paEntry = PiltoverArchiveClient.PaDeckWriteEntry(
                cardId = paIds.first,
                variantId = paIds.second,
                quantity = entry.quantity,
            )
            val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
            when (zone) {
                DeckZone.legend -> {
                    legendId = paIds.second
                    android.util.Log.d("PiltoverSync", "Step 4: legend '${entry.nameSlug}' → variantId=${paIds.second}")
                }
                DeckZone.chosenChampion -> champions.add(paEntry)
                DeckZone.battlefield -> battlefields.add(paEntry)
                DeckZone.rune -> runes.add(paEntry)
                DeckZone.main -> maindeck.add(paEntry)
                DeckZone.sideboard -> sideboard.add(paEntry)
            }
        }

        return PiltoverArchiveClient.DeckWrite(
            name = name,
            legendId = legendId,
            champions = champions,
            battlefields = battlefields,
            runes = runes,
            maindeck = maindeck,
            sideboard = sideboard,
        )
    }
}
