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

    // Add a delete function:
    @Delete
    suspend fun delete(item: InventoryItem)
}
