package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.repository.MediaRepository
import com.example.player.AuraPlayerManager

class AuraApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: MediaRepository
        private set

    lateinit var playerManager: AuraPlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        repository = MediaRepository(database.mediaDao())
        playerManager = AuraPlayerManager(this)
    }
}
