package com.invenkode.cathedralcafeinventorylog

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking

class ExpirationWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val inventoryDao = InventoryDatabase.getDatabase(context).inventoryDao()

    override fun doWork(): Result {
        val currentTime = System.currentTimeMillis()
        // Define your target email address (or load from SharedPreferences/config)
        val email = "yourEmail@example.com"

        runBlocking {
            val items = inventoryDao.getAllItemsSync()
            for (item in items) {
                // Example: If the item is expiring within the next 2 days
                if (item.expirationDate - currentTime in 0..(2 * 24 * 60 * 60 * 1000)) {
                    EmailUtil.sendEmailNotification(
                        email,
                        "Expiration Warning",
                        "The item '${item.name}' is expiring soon."
                    )
                    Log.d("ExpirationWorker", "Approaching expiration for: ${item.name}")
                }
                // Check if the expiration date has been reached or passed
                if (currentTime >= item.expirationDate) {
                    EmailUtil.sendEmailNotification(
                        email,
                        "Expiration Alert",
                        "The item '${item.name}' has expired. Please dispose of it."
                    )
                    Log.d("ExpirationWorker", "Expired: ${item.name}")
                }
            }
        }
        return Result.success()
    }
}
