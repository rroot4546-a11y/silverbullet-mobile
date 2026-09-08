package com.silverbullet.mobile

import android.content.Context
import android.content.SharedPreferences

object AppContextHolder {
    lateinit var context: Context
}

object Prefs {
    private const val NAME = "silverbullet_prefs"
    const val KEY_SERVER = "server_url"
    const val KEY_TOKEN = "bearer_token"
    const val KEY_SERVER_VERSION = "server_version"

    private fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val appContext: Context get() = AppContextHolder.context

    var serverUrl: String
        get() = sp(appContext).getString(KEY_SERVER, "") ?: ""
        set(value) = sp(appContext).edit().putString(KEY_SERVER, value.trim().trimEnd('/')).apply()

    var bearerToken: String
        get() = sp(appContext).getString(KEY_TOKEN, "") ?: ""
        set(value) = sp(appContext).edit().putString(KEY_TOKEN, value.trim()).apply()

    var serverVersion: String
        get() = sp(appContext).getString(KEY_SERVER_VERSION, "") ?: ""
        set(value) = sp(appContext).edit().putString(KEY_SERVER_VERSION, value).apply()
}