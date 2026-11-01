package com.omarchy.auto.core

import android.content.Context
import android.content.SharedPreferences

object ThemeStore {
    private const val PREFS = "omarchy_auto"
    private const val KEY = "theme_index"

    private var prefs: SharedPreferences? = null

    @Volatile
    var index: Int = 0
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        index = Math.floorMod(prefs?.getInt(KEY, 0) ?: 0, Themes.all.size)
    }

    fun next(): Int {
        select(index + 1)
        return index
    }

    fun select(value: Int) {
        index = Math.floorMod(value, Themes.all.size)
        prefs?.edit()?.putInt(KEY, index)?.apply()
    }

    fun current(): OmarchyTheme = Themes.at(index)
}
