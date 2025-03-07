package com.invenkode.cathedralcafeinventorylog

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val variant: String = "",
    val expirationDate: Long, // should be a Long
    val quantity: Int,
    val storageType: String,
    val notifiedApproaching: Boolean = false,    // Flags to track notifications
    val notifiedExpired: Boolean = false
)
