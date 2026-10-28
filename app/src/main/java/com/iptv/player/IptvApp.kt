package com.iptv.player

import android.app.Application

class IptvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PlayerManager.init(this)
    }
}
