package com.riftcompanion.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BanlistDao {

    @Query("SELECT * FROM banlist")
    fun getAll(): Flow<List<BanlistEntity>>

    @Query("SELECT * FROM banlist")
    suspend fun getAllOnce(): List<BanlistEntity>

    @Query("SELECT * FROM banlist WHERE cardType = 'card'")
    suspend fun getBannedCards(): List<BanlistEntity>

    @Query("SELECT * FROM banlist WHERE cardType = 'battlefield'")
    suspend fun getBannedBattlefields(): List<BanlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<BanlistEntity>)

    @Query("DELETE FROM banlist")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(entities: List<BanlistEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
