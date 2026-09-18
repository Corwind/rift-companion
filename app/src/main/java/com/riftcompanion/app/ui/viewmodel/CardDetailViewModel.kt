package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.CardPrintingDao
import com.riftcompanion.app.data.db.EntityConverter
import com.riftcompanion.app.data.db.InventoryLineDao
import com.riftcompanion.app.data.db.InventoryLocationDao
import com.riftcompanion.app.data.db.LocationPolicyDao
import com.riftcompanion.app.data.repository.RiftRepository
import com.riftcompanion.app.domain.model.CardAvailability
import com.riftcompanion.app.domain.model.CataloguePrintingMetadata
import com.riftcompanion.app.domain.model.InventoryLocationQuantityEdit
import com.riftcompanion.app.domain.model.LocationKind
import com.riftcompanion.app.domain.model.LocationPolicy
import com.riftcompanion.app.domain.model.LocationQuantity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CardDetail(
    val identity: com.riftcompanion.app.domain.model.CardIdentity,
    val imageURL: String?,
    val availability: CardAvailability?,
    val expansionSlugs: List<String>,
    val rarities: List<String>,
    val printingCount: Int?,
    val preferredPrinting: CataloguePrintingMetadata?,
    val finish: String?,
    val language: String?,
    val locations: List<LocationQuantity>,
    val priceEur: Double? = null,
    val priceUsd: Double? = null,
    val priceChange7d: Double? = null,
    val priceMarket: com.riftcompanion.app.data.prefs.PriceMarket = com.riftcompanion.app.data.prefs.PriceMarket.EUR,
)

data class CardDetailUiState(
    val card: CardDetail? = null,
    val isLoading: Boolean = false,
    val isEditingLocations: Boolean = false,
    val isSavingLocations: Boolean = false,
    val locationDrafts: Map<String, Int> = emptyMap(),
    val saveError: String? = null,
    val saveSuccess: String? = null,
    val locationsForEditing: List<LocationPolicy> = emptyList(),
)

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val cardIdentityDao: CardIdentityDao,
    private val cardPrintingDao: CardPrintingDao,
    private val inventoryLineDao: InventoryLineDao,
    private val inventoryLocationDao: InventoryLocationDao,
    private val locationPolicyDao: LocationPolicyDao,
    private val cardPriceDao: com.riftcompanion.app.data.db.CardPriceDao,
    private val settingsDataStore: com.riftcompanion.app.data.prefs.SettingsDataStore,
    private val repository: RiftRepository,
) : ViewModel() {

    private var cachedBannedCards: Set<String> = emptySet()
    private var cachedBannedBattlefields: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            repository.banlistFlow().collect { entries ->
                cachedBannedCards = entries
                    .filter { it.cardType == com.riftcompanion.app.domain.model.BanlistEntryType.CARD }
                    .map { it.cardName.lowercase() }.toSet()
                cachedBannedBattlefields = entries
                    .filter { it.cardType == com.riftcompanion.app.domain.model.BanlistEntryType.BATTLEFIELD }
                    .map { it.cardName.lowercase() }.toSet()
            }
        }
    }

    fun isCardBanned(displayName: String): Boolean = displayName.lowercase() in cachedBannedCards
    fun isBattlefieldBanned(displayName: String): Boolean = displayName.lowercase() in cachedBannedBattlefields

    private val _uiState = MutableStateFlow(CardDetailUiState(isLoading = true))
    val uiState: StateFlow<CardDetailUiState> = _uiState.asStateFlow()

    fun loadCard(nameSlug: String, isFromInventory: Boolean) {
        viewModelScope.launch {
            _uiState.value = CardDetailUiState(isLoading = true)
            val identityEntity = cardIdentityDao.getByIds(listOf(nameSlug)).firstOrNull()
            if (identityEntity == null) {
                _uiState.value = CardDetailUiState(isLoading = false)
                return@launch
            }
            val identity = EntityConverter.toDomain(identityEntity)
            val printings = cardPrintingDao.getByNameSlug(nameSlug)
            val preferred = printings.firstOrNull { it.imageURL != null } ?: printings.firstOrNull()

            val lines = inventoryLineDao.getAll().first()
            val locations = inventoryLocationDao.getAll().first()
            val policies = locationPolicyDao.getAll().first()

            val productIds = printings.map { it.productID }.toSet()
            val cardLines = lines.filter { it.productId in productIds }

            val totalOwned = cardLines.sumOf { it.quantity }
            val locationQuantities = cardLines.filter { it.quantity > 0 }
                .groupBy { it.locationName ?: "Unlocated" }
                .map { (locName, locLines) ->
                    val policy = policies.firstOrNull { it.name == locName }
                    val locEntity = locations.firstOrNull { it.name == locName }
                    LocationQuantity(
                        locationName = locName,
                        displayName = locEntity?.displayName ?: policy?.displayName ?: "Unlocated",
                        color = policy?.color ?: locEntity?.color,
                        icon = policy?.icon ?: locEntity?.icon,
                        kind = policy?.kind ?: "storage",
                        quantity = locLines.sumOf { it.quantity },
                        isAvailable = policy?.countsAsAvailable ?: true,
                    )
                }

            val availableInStorage = locationQuantities
                .filter { it.kind == "storage" && it.isAvailable }
                .sumOf { it.quantity }
            val otherwiseUnavailable = locationQuantities
                .filter { !it.isAvailable }
                .sumOf { it.quantity }

            val availability = if (isFromInventory || totalOwned > 0) {
                CardAvailability(
                    totalOwned = totalOwned,
                    availableInStorage = availableInStorage,
                    otherwiseUnavailable = otherwiseUnavailable,
                )
            } else null

            val firstLine = cardLines.firstOrNull()

            // Get editable locations (non-unavailable, non-hidden)
            val editableLocations = policies
                .filter { it.kind != LocationKind.Unavailable.storageValue && !it.hidden }
                .map { e ->
                    LocationPolicy(
                        name = e.name,
                        displayName = e.displayName,
                        color = e.color,
                        icon = e.icon,
                        kind = LocationKind.fromStorageValue(e.kind),
                        countsAsAvailable = e.countsAsAvailable,
                        hidden = e.hidden,
                    )
                }
                .sortedWith(
                    compareByDescending<LocationPolicy> { it.name == "Unlocated" }
                        .thenBy { it.displayName.lowercase() }
                )

            // Load price for preferred printing
            val price = preferred?.let { cardPriceDao.getByProductId(it.productID) }

            _uiState.value = CardDetailUiState(
                card = CardDetail(
                    identity = identity,
                    imageURL = preferred?.imageURL,
                    availability = availability,
                    expansionSlugs = printings.mapNotNull { it.expansionSlug }.distinct(),
                    rarities = printings.mapNotNull { it.rarity }.distinct(),
                    printingCount = printings.size,
                    preferredPrinting = preferred?.let {
                        CataloguePrintingMetadata(
                            productID = it.productID,
                            printingSlug = it.printingSlug,
                            expansionSlug = it.expansionSlug,
                            printNumber = it.printNumber,
                            rarity = it.rarity,
                            imageURL = it.imageURL,
                        )
                    },
                    finish = firstLine?.finish,
                    language = firstLine?.language,
                    locations = locationQuantities,
                    priceEur = price?.cardmarketMarketValue,
                    priceUsd = price?.tcgplayerMarketValue,
                    priceChange7d = price?.cardmarketChange7d,
                    priceMarket = settingsDataStore.settingsFlow.first().priceMarket,
                ),
                isLoading = false,
                locationsForEditing = editableLocations,
            )
        }
    }

    fun startEditingLocations() {
        _uiState.value = _uiState.value.copy(
            isEditingLocations = true,
            saveError = null,
            saveSuccess = null,
        )
    }

    fun cancelEditingLocations() {
        _uiState.value = _uiState.value.copy(
            isEditingLocations = false,
            locationDrafts = emptyMap(),
            saveError = null,
        )
    }

    fun setDraftQuantity(locationName: String, quantity: Int) {
        val card = _uiState.value.card ?: return
        val original = card.locations
            .filter { it.locationName == locationName }
            .sumOf { it.quantity }
        val clamped = maxOf(0, quantity)
        val newDrafts = _uiState.value.locationDrafts.toMutableMap()
        if (clamped == original) {
            newDrafts.remove(locationName)
        } else {
            newDrafts[locationName] = clamped
        }
        _uiState.value = _uiState.value.copy(locationDrafts = newDrafts)
    }

    fun getDraftQuantity(locationName: String): Int {
        val card = _uiState.value.card ?: return 0
        return _uiState.value.locationDrafts[locationName]
            ?: card.locations.filter { it.locationName == locationName }.sumOf { it.quantity }
    }

    val hasLocationEdits: Boolean
        get() = _uiState.value.locationDrafts.isNotEmpty()

    fun changedLocationCount(): Int = _uiState.value.locationDrafts.size

    fun saveLocationEdits() {
        val card = _uiState.value.card ?: return
        if (!hasLocationEdits) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingLocations = true, saveError = null, saveSuccess = null)

            val quantitiesByLocation = mutableMapOf<String, Int>()
            // Include all current locations
            for (loc in card.locations) {
                quantitiesByLocation[loc.locationName] = loc.quantity
            }
            // Apply drafts
            for ((locName, qty) in _uiState.value.locationDrafts) {
                quantitiesByLocation[locName] = qty
            }

            val edit = InventoryLocationQuantityEdit(
                nameSlug = card.identity.nameSlug,
                quantitiesByLocation = quantitiesByLocation,
            )

            val result = repository.saveInventoryLocationQuantities(listOf(edit))

            if (result.isSuccess) {
                val res = result.getOrThrow()
                val parts = mutableListOf<String>()
                if (res.addedQuantity > 0) parts.add("added ${res.addedQuantity}")
                if (res.removedQuantity > 0) parts.add("removed ${res.removedQuantity}")
                if (res.movedQuantity > 0) parts.add("moved ${res.movedQuantity}")
                val summary = if (parts.isEmpty()) "updated quantities" else parts.joinToString(", ")
                _uiState.value = _uiState.value.copy(
                    isSavingLocations = false,
                    isEditingLocations = false,
                    locationDrafts = emptyMap(),
                    saveSuccess = "Saved: $summary.",
                )
                // Reload card to reflect changes
                loadCard(card.identity.nameSlug, isFromInventory = true)
            } else {
                _uiState.value = _uiState.value.copy(
                    isSavingLocations = false,
                    saveError = "Failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}",
                )
            }
        }
    }
}
