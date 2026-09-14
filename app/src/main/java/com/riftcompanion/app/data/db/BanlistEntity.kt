package com.riftcompanion.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "banlist")
data class BanlistEntity(
    @PrimaryKey val cardName: String,
    val cardType: String,  // "card" or "battlefield"
    val format: String,
    val effectiveDate: String?,
    val sourceUrl: String?,
)
