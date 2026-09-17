package com.riftcompanion.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.riftcompanion.app.data.api.BanlistFetcher
import com.riftcompanion.app.data.api.CardNexusClient
import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.CardPrintingDao
import com.riftcompanion.app.data.db.DeckDao
import com.riftcompanion.app.data.db.InventoryLineDao
import com.riftcompanion.app.data.db.InventoryLocationDao
import com.riftcompanion.app.data.db.LocationPolicyDao
import com.riftcompanion.app.data.db.RiftDatabase
import com.riftcompanion.app.data.db.SyncMetadataDao
import com.riftcompanion.app.data.repository.RiftRepository
import com.riftcompanion.app.security.CredentialStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okio.Path.Companion.toOkioPath

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Migration from v1 → v2: add deck tables and linkedDeckId column
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS decks (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    state TEXT NOT NULL,
                    rulesetId TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    linkedLocationName TEXT
                )
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS deck_entries (
                    id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    deckId TEXT NOT NULL,
                    zone TEXT NOT NULL,
                    nameSlug TEXT NOT NULL,
                    quantity INTEGER NOT NULL,
                    preferredProductId INTEGER,
                    preferredFinish TEXT,
                    preferredLanguage TEXT,
                    sourceLocationName TEXT,
                    sourceLineId TEXT,
                    isBuilt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            db.execSQL("ALTER TABLE location_policies ADD COLUMN linkedDeckId TEXT")
        }
    }

    // Migration from v2 → v3: add linkedDeckId column to location_policies
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE location_policies ADD COLUMN linkedDeckId TEXT")
        }
    }

    // Migration from v3 → v4: rename normalizedName PK to name (use exact API name as identity)
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE inventory_locations RENAME COLUMN normalizedName TO name")
            db.execSQL("ALTER TABLE location_policies RENAME COLUMN normalizedName TO name")
        }
    }

    // Migration from v4 → v5: add banlist table (now removed, but kept for upgrade path)
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS banlist (
                    cardName TEXT NOT NULL PRIMARY KEY,
                    cardType TEXT NOT NULL,
                    format TEXT NOT NULL,
                    effectiveDate TEXT,
                    sourceUrl TEXT
                )
            """.trimIndent())
        }
    }

    // Migration from v5 → v6: drop banlist table (banlist is now static, not stored in DB)
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS banlist")
        }
    }

    // Migration from v6 → v7: rename mightCost column to might in card_identities
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // SQLite doesn't support RENAME COLUMN before 3.25, so recreate the table
            db.execSQL("""
                CREATE TABLE card_identities_new (
                    nameSlug TEXT NOT NULL PRIMARY KEY,
                    gameID TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    cardType TEXT,
                    superType TEXT,
                    domainsCsv TEXT NOT NULL,
                    tagsCsv TEXT NOT NULL,
                    energyCost INTEGER,
                    might INTEGER,
                    attributesJson TEXT NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO card_identities_new (nameSlug, gameID, displayName, cardType, superType, domainsCsv, tagsCsv, energyCost, might, attributesJson)
                SELECT nameSlug, gameID, displayName, cardType, superType, domainsCsv, tagsCsv, energyCost, mightCost, attributesJson
                FROM card_identities
            """.trimIndent())
            db.execSQL("DROP TABLE card_identities")
            db.execSQL("ALTER TABLE card_identities_new RENAME TO card_identities")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RiftDatabase {
        return Room.databaseBuilder(
            context,
            RiftDatabase::class.java,
            "rift_companion.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideCardIdentityDao(db: RiftDatabase): CardIdentityDao = db.cardIdentityDao()
    @Provides fun provideCardPrintingDao(db: RiftDatabase): CardPrintingDao = db.cardPrintingDao()
    @Provides fun provideInventoryLineDao(db: RiftDatabase): InventoryLineDao = db.inventoryLineDao()
    @Provides fun provideInventoryLocationDao(db: RiftDatabase): InventoryLocationDao = db.inventoryLocationDao()
    @Provides fun provideLocationPolicyDao(db: RiftDatabase): LocationPolicyDao = db.locationPolicyDao()
    @Provides fun provideSyncMetadataDao(db: RiftDatabase): SyncMetadataDao = db.syncMetadataDao()
    @Provides fun provideDeckDao(db: RiftDatabase): DeckDao = db.deckDao()

    @Provides
    @Singleton
    fun provideCredentialStore(@ApplicationContext context: Context): CredentialStore {
        return CredentialStore.create(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideCardNexusClient(
        okHttpClient: OkHttpClient,
        credentialStore: CredentialStore,
    ): CardNexusClient {
        return CardNexusClient(okHttpClient, credentialStore)
    }

    @Provides
    @Singleton
    fun provideBanlistFetcher(): BanlistFetcher {
        return BanlistFetcher()
    }

    @Provides
    @Singleton
    fun provideRepository(
        client: CardNexusClient,
        banlistFetcher: BanlistFetcher,
        cardIdentityDao: CardIdentityDao,
        cardPrintingDao: CardPrintingDao,
        inventoryLineDao: InventoryLineDao,
        inventoryLocationDao: InventoryLocationDao,
        locationPolicyDao: LocationPolicyDao,
        syncMetadataDao: SyncMetadataDao,
    ): RiftRepository {
        return RiftRepository(
            client, banlistFetcher, cardIdentityDao, cardPrintingDao,
            inventoryLineDao, inventoryLocationDao,
            locationPolicyDao, syncMetadataDao,
        )
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader {
        val diskCacheDir = File(context.cacheDir, "image_cache")
        return ImageLoader.Builder(context)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(diskCacheDir.toOkioPath())
                    .maxSizeBytes(200L * 1024 * 1024) // 200 MB persistent disk cache for card artwork
                    .build()
            }
            .build()
    }
}
