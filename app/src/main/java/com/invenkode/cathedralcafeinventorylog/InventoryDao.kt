package com.invenkode.cathedralcafeinventorylog

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory")
    fun getAll(): LiveData<List<InventoryItem>>

    // For background worker use (suspend function to fetch list synchronously)
    @Query("SELECT * FROM inventory")
    suspend fun getAllItemsSync(): List<InventoryItem>

    @Insert
    suspend fun insert(item: InventoryItem)

    @Update
    suspend fun update(item: InventoryItem)

    @Delete
    suspend fun delete(item: InventoryItem)
    // Method to query for items whose name starts with the provided base.
    @Query("SELECT * FROM inventory WHERE name LIKE :namePrefix || '%'")
    suspend fun getItemsByName(namePrefix: String): List<InventoryItem>

    @Query("UPDATE inventory SET isRunningLow = :isLow WHERE id = :itemId")
    fun updateRunningLow(itemId: Int, isLow: Boolean)

}
