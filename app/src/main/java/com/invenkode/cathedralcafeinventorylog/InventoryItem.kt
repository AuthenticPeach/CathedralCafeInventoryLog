package com.invenkode.cathedralcafeinventorylog

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    // Store expiration date as milliseconds since epoch
    val expirationDate: Long,
    val quantity: Int = 6,  // default quantity is 6
    val storageType: String = "Fridge",  // default storage type is Fridge
    val notifiedApproaching: Boolean = false,    // Flags to track notifications
    val notifiedExpired: Boolean = false
)
