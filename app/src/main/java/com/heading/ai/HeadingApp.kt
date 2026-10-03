package com.heading.ai

import android.app.Application
import com.heading.ai.data.preferences.AppPreferences
import com.heading.ai.data.repository.ChatRepository

class HeadingApp : Application() {

    lateinit var preferences: AppPreferences
        private set

    lateinit var chatRepository: ChatRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = AppPreferences(this)
        chatRepository = ChatRepository(preferences)
    }

    companion object {
        lateinit var instance: HeadingApp
            private set
    }
}
