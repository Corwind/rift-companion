package com.riftcompanion.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CardIdentityDao {

    @Query("SELECT * FROM card_identities")
    fun getAll(): Flow<List<CardIdentityEntity>>

    @Query("SELECT * FROM card_identities WHERE nameSlug IN (:nameSlugs)")
    suspend fun getByIds(nameSlugs: List<String>): List<CardIdentityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CardIdentityEntity>)

    @Query("DELETE FROM card_identities")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<CardIdentityEntity>) {
        deleteAll()
        insertAll(entities)
    }
}

@Dao
interface CardPrintingDao {

    @Query("SELECT * FROM card_printings")
    fun getAll(): Flow<List<CardPrintingEntity>>

    @Query("SELECT * FROM card_printings WHERE nameSlug = :nameSlug")
    suspend fun getByNameSlug(nameSlug: String): List<CardPrintingEntity>

    @Query("SELECT * FROM card_printings WHERE productID = :productId LIMIT 1")
    suspend fun getByProductId(productId: Long): CardPrintingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CardPrintingEntity>)

    @Query("DELETE FROM card_printings")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<CardPrintingEntity>) {
        deleteAll()
        insertAll(entities)
    }
}

@Dao
interface InventoryLineDao {

    @Query("SELECT * FROM inventory_lines")
    fun getAll(): Flow<List<InventoryLineEntity>>

    @Query("SELECT * FROM inventory_lines WHERE locationName = :locationName COLLATE NOCASE")
    suspend fun getByLocation(locationName: String): List<InventoryLineEntity>

    @Query("SELECT * FROM inventory_lines WHERE id = :nameSlug AND locationName = :locationName LIMIT 1")
    suspend fun getBySlugAndLocation(nameSlug: String, locationName: String): InventoryLineEntity?

    @Query("SELECT * FROM inventory_lines WHERE id = :nameSlug")
    suspend fun getBySlug(nameSlug: String): List<InventoryLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<InventoryLineEntity>)

    @Query("UPDATE inventory_lines SET locationName = :newLocation, quantity = :newQty WHERE id = :lineId")
    suspend fun updateLocationAndQuantity(lineId: String, newLocation: String?, newQty: Int)

    @Query("DELETE FROM inventory_lines")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<InventoryLineEntity>) {
        deleteAll()
        insertAll(entities)
    }
}

@Dao
interface InventoryLocationDao {

    @Query("SELECT * FROM inventory_locations")
    fun getAll(): Flow<List<InventoryLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<InventoryLocationEntity>)

    @Query("DELETE FROM inventory_locations")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<InventoryLocationEntity>) {
        deleteAll()
        insertAll(entities)
    }
}

@Dao
interface LocationPolicyDao {

    @Query("SELECT * FROM location_policies")
    fun getAll(): Flow<List<LocationPolicyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocationPolicyEntity)

    @Query("DELETE FROM location_policies WHERE normalizedName = :normalizedName")
    suspend fun delete(normalizedName: String)

    @Query("SELECT * FROM location_policies WHERE normalizedName = :normalizedName")
    suspend fun get(normalizedName: String): LocationPolicyEntity?

    @Query("SELECT * FROM location_policies WHERE kind = :kind")
    suspend fun getByKind(kind: String): List<LocationPolicyEntity>

    @Query("SELECT * FROM location_policies WHERE kind != 'unavailable' AND hidden = 0")
    suspend fun getVisibleNonUnavailable(): List<LocationPolicyEntity>

    @Query("SELECT * FROM location_policies WHERE kind = 'storage'")
    suspend fun getStorageLocations(): List<LocationPolicyEntity>

    @Query("SELECT * FROM location_policies WHERE normalizedName = :name LIMIT 1")
    suspend fun getByName(name: String): LocationPolicyEntity?
}

@Dao
interface SyncMetadataDao {

    @Query("SELECT value FROM sync_metadata WHERE key = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(entity: SyncMetadataEntity)

    @Query("DELETE FROM sync_metadata WHERE key = :key")
    suspend fun delete(key: String)

    suspend fun set(key: String, value: String) {
        set(SyncMetadataEntity(key = key, value = value))
    }
}
