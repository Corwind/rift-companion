package com.riftcompanion.app.di

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.riftcompanion.app.data.api.CardNexusClient
import com.riftcompanion.app.data.db.CardIdentityDao
import com.riftcompanion.app.data.db.CardPrintingDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RiftDatabase {
        return Room.databaseBuilder(
            context,
            RiftDatabase::class.java,
            "rift_companion.db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideCardIdentityDao(db: RiftDatabase): CardIdentityDao = db.cardIdentityDao()
    @Provides fun provideCardPrintingDao(db: RiftDatabase): CardPrintingDao = db.cardPrintingDao()
    @Provides fun provideInventoryLineDao(db: RiftDatabase): InventoryLineDao = db.inventoryLineDao()
    @Provides fun provideInventoryLocationDao(db: RiftDatabase): InventoryLocationDao = db.inventoryLocationDao()
    @Provides fun provideLocationPolicyDao(db: RiftDatabase): LocationPolicyDao = db.locationPolicyDao()
    @Provides fun provideSyncMetadataDao(db: RiftDatabase): SyncMetadataDao = db.syncMetadataDao()

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
    fun provideRepository(
        client: CardNexusClient,
        cardIdentityDao: CardIdentityDao,
        cardPrintingDao: CardPrintingDao,
        inventoryLineDao: InventoryLineDao,
        inventoryLocationDao: InventoryLocationDao,
        locationPolicyDao: LocationPolicyDao,
        syncMetadataDao: SyncMetadataDao,
    ): RiftRepository {
        return RiftRepository(
            client, cardIdentityDao, cardPrintingDao,
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
