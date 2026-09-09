package com.riftcompanion.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.CardPrintingDao
import com.riftcompanion.app.data.db.EntityConverter
import com.riftcompanion.app.data.db.InventoryLineDao
import com.riftcompanion.app.data.db.InventoryLocationDao
import com.riftcompanion.app.data.db.LocationPolicyDao
import com.riftcompanion.app.domain.model.CardAvailability
import com.riftcompanion.app.domain.model.CataloguePrintingMetadata
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
)

data class CardDetailUiState(
    val card: CardDetail? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class CardDetailViewModel @Inject constructor(
    private val cardIdentityDao: CardIdentityDao,
    private val cardPrintingDao: CardPrintingDao,
    private val inventoryLineDao: InventoryLineDao,
    private val inventoryLocationDao: InventoryLocationDao,
    private val locationPolicyDao: LocationPolicyDao,
) : ViewModel() {

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
                ),
                isLoading = false,
            )
        }
    }
}
