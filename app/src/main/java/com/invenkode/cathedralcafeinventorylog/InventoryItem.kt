package com.invenkode.cathedralcafeinventorylog

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val variant: String = "",
    val expirationDate: Long,
    val quantity: Int,
    val storageType: String,       // "Fridge", "Display", or "Stock"
    val stockSubCategory: String = "",     // e.g. "Cups and Lids"
    val stockAlertType: String = "ideal",  // "ideal" or "runningLow"
    val idealThreshold: Int? = null,
    val isRunningLow: Boolean = false,
    val notifiedApproaching: Boolean = false,
    val notifiedExpired: Boolean = false
)
