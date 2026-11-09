package com.omarchy.auto

import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.omarchy.auto.core.DashboardState
import com.omarchy.auto.core.LiveDataStore
import com.omarchy.auto.core.NavigationController

/**
 * Host search keyboard and result rows for Android Auto. The custom canvas
 * keyboard is not drawn on the navigation surface. The host blocks the
 * keyboard while driving but still shows results and speech input.
 */
class PlaceSearchScreen(carContext: CarContext) : Screen(carContext) {
    private var lastResultsStamp = ""
    private val listener: (DashboardState) -> Unit = {
        val stamp = resultsStamp()
        if (stamp != lastResultsStamp) {
            lastResultsStamp = stamp
            invalidate()
        }
    }
    private val searchHandler = Handler(Looper.getMainLooper())
    private val debouncedSearch = Runnable {
        val rawQuery = NavigationController.searchQuery
        if (rawQuery.trim().isEmpty()) {
            NavigationController.clearQuery()
        } else {
            NavigationController.searchAddress(rawQuery)
        }
        lastResultsStamp = resultsStamp()
        invalidate()
    }

    private val callback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            NavigationController.setQuery(searchText)
            searchHandler.removeCallbacks(debouncedSearch)
            searchHandler.postDelayed(debouncedSearch, SEARCH_DEBOUNCE_MS)
        }

        override fun onSearchSubmitted(searchText: String) {
            NavigationController.setQuery(searchText)
            searchHandler.removeCallbacks(debouncedSearch)
            if (searchText.isBlank()) {
                NavigationController.clearQuery()
            } else {
                NavigationController.searchAddress(searchText)
            }
            lastResultsStamp = resultsStamp()
            invalidate()
        }
    }

    private fun resultsStamp(): String {
        val nav = NavigationController
        return listOf(
            nav.searchResults.size.toString(),
            nav.searchResults.firstOrNull()?.title.orEmpty(),
            nav.searchResults.lastOrNull()?.title.orEmpty(),
            nav.searching.toString(),
            nav.statusMessage.orEmpty(),
        ).joinToString("|")
    }

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    lastResultsStamp = resultsStamp()
                    LiveDataStore.addListener(listener)
                }

                override fun onStop(owner: LifecycleOwner) {
                    searchHandler.removeCallbacks(debouncedSearch)
                    LiveDataStore.removeListener(listener)
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        val results = NavigationController.searchResults
        if (results.isEmpty()) {
            list.setNoItemsMessage(
                when {
                    NavigationController.searching -> "Searching…"
                    NavigationController.searchQuery.isBlank() -> "Enter a place or address"
                    else -> NavigationController.statusMessage ?: "No results"
                },
            )
        } else {
            results.take(6).forEachIndexed { index, candidate ->
                list.addItem(
                    Row.Builder()
                        .setTitle(candidate.title.ifBlank { "Place" })
                        .addText(candidate.subtitle.ifBlank { candidate.displayName })
                        .setOnClickListener {
                            NavigationController.selectDestination(candidate, autoStartNav = true)
                            finish()
                        }
                        .build(),
                )
                if (index >= 5) return@forEachIndexed
            }
        }
        val builder = SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("Place or address")
            .setInitialSearchText(NavigationController.searchQuery)
            .setShowKeyboardByDefault(true)
        if (NavigationController.searching) {
            builder.setLoading(true)
        } else {
            builder.setItemList(list.build())
        }
        return builder.build()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 400L
    }
}
