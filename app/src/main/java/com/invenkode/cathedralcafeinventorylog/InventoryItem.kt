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
    // New field for inventory count
    val quantity: Int = 1,
    // Flags to track notifications
    val notifiedApproaching: Boolean = false,
    val notifiedExpired: Boolean = false
)
