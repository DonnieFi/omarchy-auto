package com.omarchy.auto.core

import android.content.Context
import android.content.SharedPreferences

object ThemeStore {
    private const val PREFS = "omarchy_auto"
    private const val KEY = "theme_id"
    private const val LEGACY_KEY = "theme_index"

    private var prefs: SharedPreferences? = null

    @Volatile
    var index: Int = 0
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedId = prefs?.getString(KEY, null)
        val byId = savedId?.let { id ->
            Themes.all.indexOfFirst { it.id == id }.takeIf { it >= 0 }
        }
        index = byId ?: Math.floorMod(prefs?.getInt(LEGACY_KEY, 0) ?: 0, Themes.all.size)
    }

    fun next(): Int {
        select(index + 1)
        return index
    }

    fun select(value: Int) {
        index = Math.floorMod(value, Themes.all.size)
        prefs?.edit()
            ?.putString(KEY, current().id)
            ?.putInt(LEGACY_KEY, index)
            ?.apply()
    }

    fun current(): OmarchyTheme = Themes.at(index)
}
