# RiftCompanion

An Android companion app for managing Riftbound card inventory, built to mirror the look and feel of the macOS RiftBuilder app.

## Features

- **Inventory View**: Searchable, filterable by location and domain, list/grid modes, shows card artwork, domains, tags, and quantities
- **Catalog View**: Same search/filter/display as Inventory, pulls from the CardNexus Catalog API
- **Card Details**: Full card information including artwork, rules text, flavor text, energy/might cost, domains, tags, printings, locations, and availability
- **Locations Management**: Create, edit, delete (empty only), and hide locations from inventory
- **Settings**: Theme customization (appearance, accent colors, gradients, transparency), CardNexus API key management (encrypted + biometric), sync
- **Biometric Security**: App lock and API credential access secured by biometric authentication
- **First-Time Setup**: Guided biometric enrollment and API key entry
- **Data Usage Warning**: Warns user before syncing on metered (cellular) connections, with permanent acknowledgment option
- **Persistent Artwork Cache**: Card images cached to disk (200 MB) for offline viewing and reduced data usage
- **Scoped to Riftbound**: All API calls use `game=riftbound`

## Architecture

- **UI**: Jetpack Compose with Material 3
- **DI**: Hilt
- **Local Storage**: Room database for card data, DataStore for settings, EncryptedSharedPreferences for credentials
- **Networking**: OkHttp with kotlinx.serialization (no Retrofit dependency)
- **Image Loading**: Coil 3 with persistent disk cache
- **Security**: AndroidX Biometric for app lock, EncryptedSharedPreferences for API key storage

## Resource Optimization

- No background services, no WorkManager, no periodic sync
- All sync is strictly user-initiated
- Bounded memory cache (15% of app memory) and disk cache (200 MB)
- Lifecycle-aware coroutines (WhileSubscribed(5000) stops collection when UI is not visible)
- Lazy loading in all lists/grids
- No unnecessary wakeups or battery drain

## Building

```bash
./gradlew assembleDebug
```

## CardNexus API

The app communicates with `https://public-api.cardnexus.com/v1/` using:
- `GET /inventory` (paginated) for inventory lines
- `GET /inventory/locations` for locations
- `GET /feeds/riftbound/catalog` for catalogue metadata
- `GET <feed_url>` for catalogue download (gzip-compressed NDJSON)
- `POST /inventory/locations` for location creation
- `PATCH /inventory/locations/{name}` for location updates
- `DELETE /inventory/locations/{name}` for location deletion
- `POST /inventory/bulk/update` for bulk inventory moves

All requests use `Bearer` token authentication with the user's API key.
