package com.riftcompanion.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CardIdentityEntity::class,
        CardPrintingEntity::class,
        InventoryLineEntity::class,
        InventoryLocationEntity::class,
        LocationPolicyEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class RiftDatabase : RoomDatabase() {
    abstract fun cardIdentityDao(): CardIdentityDao
    abstract fun cardPrintingDao(): CardPrintingDao
    abstract fun inventoryLineDao(): InventoryLineDao
    abstract fun inventoryLocationDao(): InventoryLocationDao
    abstract fun locationPolicyDao(): LocationPolicyDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
