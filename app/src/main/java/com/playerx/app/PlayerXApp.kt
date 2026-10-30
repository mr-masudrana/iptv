package com.playerx.app

import android.app.Application

class PlayerXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayerManager.init(this)
    }
}
