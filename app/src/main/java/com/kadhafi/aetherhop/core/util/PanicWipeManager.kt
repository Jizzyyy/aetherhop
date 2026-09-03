package com.kadhafi.aetherhop.core.util

import android.content.Context
import com.kadhafi.aetherhop.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PanicWipeManager(private val context: Context) {
    private val appContext = context.applicationContext

    suspend fun wipeAllDataAndResetNode() = withContext(Dispatchers.IO) {
        try {
            // 1. Clear Room database tables
            val db = AppDatabase.getDatabase(appContext)
            db.clearAllTables()

            // 2. Clear SharedPreferences (Device identity & settings)
            val prefs = appContext.getSharedPreferences("aetherhop_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().commit()

            // 3. Clear temp cache files
            appContext.cacheDir?.deleteRecursively()
            appContext.getExternalFilesDir(null)?.deleteRecursively()

            true
        } catch (e: Exception) {
            android.util.Log.e("PanicWipeManager", "Error during panic wipe", e)
            false
        }
    }
}
