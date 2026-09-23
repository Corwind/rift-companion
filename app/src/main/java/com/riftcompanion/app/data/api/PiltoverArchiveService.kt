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
            repository.synchronize().getOrThrow()
        } catch (e: Exception) {
            // Don't add to errors — we can still sync local data to PA
        }

        // 2. Fetch all PA cards and build variantNumber → (cardId, variantId) map + card info map
        val (paVariantMap, paCardInfoMap) = try {
            val result = piltoverArchiveClient.fetchAllCards().getOrThrow()
            result
        } catch (e: Exception) {
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
        val identities = cardIdentityDao.getAllCards()
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
        // Case-insensitive PA variant lookup: variantNumber (lowercase) → List of PaVariantMatch
        val paVariantByLower = paVariantMap.mapKeys { (k, _) -> k.lowercase() }
        
        // Determine if a CN expansion is a promo set
        val promoExpansions = setOf(
            "origins-promo-cards", "spiritforged-promo-cards",
            "unleashed-promo-cards", "vendetta-promo-cards", "riftbound-promos",
        )
        
        // Map productId → (cardId, variantId) for exact printing match (push direction)
        val productToPaIds = mutableMapOf<Long, Pair<String, String>>()
        var unmatchedCount = 0
        // Build variantId → printingSlug map for exact deck matching (pull direction)
        // This must cover ALL PA variants, not just the best match per CN printing
        val variantIdToPrintingSlug = mutableMapOf<String, String>()
        // Build variantNumber → printingSlug map for legend matching via PaDeckLegend.variantNumber
        val variantNumberToPrintingSlug = mutableMapOf<String, String>()
        
        // Build variantId → productId reverse map for deck pull (to set preferredProductId)
        val variantIdToProductId = mutableMapOf<String, Long>()
        
        // Group CN printings by variantNumber to match against PA variants
        val cnPrintingsByVariantNumber = mutableMapOf<String, MutableList<com.riftcompanion.app.data.db.CardPrintingEntity>>()
        for (printing in printings) {
            val prefix = expansionToPrefix[printing.expansionSlug]
            val printNumber = printing.printNumber
            if (prefix != null && !printNumber.isNullOrBlank()) {
                val paPrintNumber = if (printNumber.endsWith("s") && printNumber.length > 1 && printNumber[printNumber.length - 2].isDigit()) {
                    printNumber.dropLast(1) + "*"
                } else {
                    printNumber
                }
                val variantNumber = "$prefix-$paPrintNumber"
                cnPrintingsByVariantNumber.getOrPut(variantNumber.lowercase()) { mutableListOf() }.add(printing)
            }
        }
        
        for ((variantNumberLower, cnPrintings) in cnPrintingsByVariantNumber) {
            val paMatches = paVariantByLower[variantNumberLower]
            if (paMatches.isNullOrEmpty()) {
                unmatchedCount += cnPrintings.size
                continue
            }
            
            // Match each CN printing to the best PA variant
            for (cnPrinting in cnPrintings) {
                val isCnPromo = cnPrinting.expansionSlug in promoExpansions
                val bestPaMatch = if (paMatches.size == 1) {
                    paMatches.first()
                } else if (isCnPromo) {
                    paMatches.firstOrNull { it.variantType != null && it.variantType != "Standard" }
                        ?: paMatches.first()
                } else {
                    paMatches.firstOrNull { it.variantType == null || it.variantType == "Standard" }
                        ?: paMatches.first()
                }
                val paIds = bestPaMatch.cardId to bestPaMatch.variantId
                productToPaIds[cnPrinting.productID] = paIds
                variantIdToPrintingSlug[bestPaMatch.variantId] = cnPrinting.nameSlug
                variantIdToProductId[bestPaMatch.variantId] = cnPrinting.productID
            }
            
            // Also map ALL remaining PA variantIds to the closest CN printing slug
            // so deck pull can find them even if the PA variant doesn't have an exact CN match
            for (paMatch in paMatches) {
                if (paMatch.variantId !in variantIdToPrintingSlug) {
                    // Pick the CN printing that best matches this PA variant type
                    val isPaPromo = paMatch.variantType != null && paMatch.variantType != "Standard"
                    val bestCn = cnPrintings.firstOrNull { (it.expansionSlug in promoExpansions) == isPaPromo }
                        ?: cnPrintings.first()
                    variantIdToPrintingSlug[paMatch.variantId] = bestCn.nameSlug
                    variantIdToProductId[paMatch.variantId] = bestCn.productID
                }
            }
            
            // Map variantNumber to the standard CN printing slug for legend matching
            val standardCn = cnPrintings.firstOrNull { it.expansionSlug !in promoExpansions } ?: cnPrintings.first()
            variantNumberToPrintingSlug[variantNumberLower] = standardCn.nameSlug
        }
        
        // Also build nameSlug → (cardId, variantId) for deck sync (use first matched printing per slug)
        val slugToPaIds = mutableMapOf<String, Pair<String, String>>()
        for (printing in printings) {
            val paIds = productToPaIds[printing.productID]
            if (paIds != null && printing.nameSlug !in slugToPaIds) {
                slugToPaIds[printing.nameSlug] = paIds
            }
        }

        // 2d. Enrich local card identities with PA data (power, mightBonus, maxCopies, banEffectiveDate)
        for (identity in identities) {
            // Strip suffixes and try exact match, then comma substitution
            val baseName = identity.displayName
                .removeSuffix(" (alt)")
                .removeSuffix(" (Signed)")
                .removeSuffix(" (DE)")
                .removeSuffix(" (NX)")
                .removeSuffix(" (ZN)")
                .removeSuffix(" (Worlds 2025)")
            val paCardInfo = paCardInfoMap[baseName]
                ?: paCardInfoMap[baseName.replace(" - ", ", ")]
            if (paCardInfo != null) {
                cardIdentityDao.updatePaFields(
                    nameSlug = identity.nameSlug,
                    power = paCardInfo.power,
                    mightBonus = paCardInfo.mightBonus,
                    maxCopies = paCardInfo.maxCopies,
                    banEffectiveDate = paCardInfo.banEffectiveDate,
                )
            }
        }

        // 3. Sync collection: read local inventory → export PA → PATCH/POST/DELETE
        var collectionEntriesPushed = 0
        var paCollectionEntries: List<PiltoverArchiveClient.CollectionEntry> = emptyList()
        try {
            val lines = inventoryLineDao.getAll().first()

            // Export existing PA collection
            val paCollectionResult = piltoverArchiveClient.getCollection()
            if (paCollectionResult.isFailure) {
            }
            val paCollection = paCollectionResult.getOrNull() ?: emptyList()
            paCollectionEntries = paCollection
            val paVariantIds = paCollection.mapNotNull { it.variantId }.toSet()

            // Build CN variantId → quantity map (using exact productId → variantId mapping)
            val cnQuantitiesByVariantId = mutableMapOf<String, Int>()
            for (line in lines) {
                if (line.quantity <= 0) continue
                val paIds = productToPaIds[line.productId] ?: continue
                cnQuantitiesByVariantId[paIds.second] = (cnQuantitiesByVariantId[paIds.second] ?: 0) + line.quantity
            }

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
        } catch (e: Exception) {
            errors.add("Collection push failed: ${e.message}")
        }

        // 4. Push deck definitions to Piltover Archive (create or update)
        var decksPushed = 0
        try {
            val decks = deckDao.getAllDecks().first()
            for (deck in decks) {
                val entries = deckDao.getEntriesForDeck(deck.id)
                val paDeck = buildPaDeck(deck.name, entries, slugToPaIds, productToPaIds)
                if (deck.piltoverArchiveId != null) {
                    val updated = piltoverArchiveClient.updateDeckSafe(deck.piltoverArchiveId, paDeck).getOrNull()
                } else {
                    val created = piltoverArchiveClient.createDeck(paDeck).getOrThrow()
                    deckDao.insertDeck(deck.copy(piltoverArchiveId = created.id))
                }
                decksPushed++
            }
        } catch (e: Exception) {
            errors.add("Deck push failed: ${e.message}")
        }

        // 5. Pull user's decks from PA that don't exist in the app
        var decksPulled = 0
        try {
            val localDecks = deckDao.getAllDecks().first()
            val knownPaIds = localDecks.mapNotNull { it.piltoverArchiveId }.toSet()

            // Use /decks/my to get all our decks (including private/draft)
            val paDecks = piltoverArchiveClient.getMyDecks().getOrNull() ?: emptyList()

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
                
                // Add legend entry — use variantNumber for exact match, fall back to variantId
                val legendVariantNumber = detail.legend?.variantNumber
                val legendId = detail.legend?.id
                val legendSlug = if (legendVariantNumber != null) {
                    variantNumberToPrintingSlug[legendVariantNumber.lowercase()]
                } else if (legendId != null) {
                    variantIdToPrintingSlug[legendId]
                } else {
                    null
                }
                if (legendSlug != null) {
                    val legendProductId = legendId?.let { variantIdToProductId[it] }
                    entries.add(DeckEntryEntity(
                        deckId = deckId,
                        zone = DeckZone.legend.name,
                        nameSlug = legendSlug,
                        quantity = 1,
                        preferredProductId = legendProductId,
                    ))
                }
                
                fun addEntries(zone: DeckZone, cards: List<PiltoverArchiveClient.PaDeckCardEntry>) {
                    for (card in cards) {
                        // Use variantId only for exact printing match — do NOT fall back to cardId
                        // (cardId is shared across all printings of a card)
                        val nameSlug = card.variantId?.let { variantIdToPrintingSlug[it] } ?: continue
                        val productId = card.variantId?.let { variantIdToProductId[it] }
                        entries.add(DeckEntryEntity(
                            deckId = deckId,
                            zone = zone.name,
                            nameSlug = nameSlug,
                            quantity = card.quantity ?: 1,
                            preferredProductId = productId,
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
            }
        } catch (e: Exception) {
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
        productToPaIds: Map<Long, Pair<String, String>>,
    ): PiltoverArchiveClient.DeckWrite {
        val champions = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        val battlefields = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        val runes = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        val maindeck = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        val sideboard = mutableListOf<PiltoverArchiveClient.PaDeckWriteEntry>()
        var legendId: String? = null

        for (entry in entries) {
            // Use preferredProductId for exact printing match if available, fall back to slug
            val paIds = if (entry.preferredProductId != null) {
                productToPaIds[entry.preferredProductId] ?: slugToPaIds[entry.nameSlug]
            } else {
                slugToPaIds[entry.nameSlug]
            } ?: continue
            val paEntry = PiltoverArchiveClient.PaDeckWriteEntry(
                cardId = paIds.first,
                variantId = paIds.second,
                quantity = entry.quantity,
            )
            val zone = DeckZone.fromString(entry.zone) ?: DeckZone.main
            when (zone) {
                DeckZone.legend -> {
                    legendId = paIds.second
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
