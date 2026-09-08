package com.silverbullet.mobile

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = applicationContext
    }
}