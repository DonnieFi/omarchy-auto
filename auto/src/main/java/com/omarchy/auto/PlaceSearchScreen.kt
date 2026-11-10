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
 * keyboard while driving but still shows results, previous results, and
 * speech input.
 *
 * Voice transcripts are protected in both directions: blank host updates are
 * ignored, searches are started only after the transcript settles, the
 * template is not rebuilt while a search is in flight, and the last real
 * transcript is re-sent with result refreshes. Without those guards some
 * hosts cleared the search box before the driver could tap a result.
 */
class PlaceSearchScreen(carContext: CarContext) : Screen(carContext) {
    private var firstTemplate = true
    private var lastResultsStamp = ""

    private val listener: (DashboardState) -> Unit = {
        // Do not rebuild the template while a search is in flight. Some hosts
        // reset the search box on every update, which loses a voice transcript.
        if (!NavigationController.searching) {
            val stamp = resultsStamp()
            if (stamp != lastResultsStamp) {
                lastResultsStamp = stamp
                invalidate()
            }
        }
    }

    private val searchHandler = Handler(Looper.getMainLooper())
    private val debouncedSearch = Runnable {
        val rawQuery = NavigationController.searchQuery
        if (rawQuery.isNotBlank()) {
            // Force the completion update through even if the new results have
            // the same titles, so the transcript is restored in the host box.
            lastResultsStamp = ""
            NavigationController.searchAddress(rawQuery)
        }
    }

    private val callback = object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) {
            // Hosts clear or rewrite the box around a voice session. Ignoring
            // blanks keeps the last real transcript and, crucially, leaves the
            // pending search timer alone. Removing it on a blank update made
            // the transcript vanish with no results to tap.
            if (searchText.isBlank()) return
            // Keep the raw text, including a trailing space. Trimming here
            // deleted the space a driver had just spoken while typing.
            NavigationController.setQuery(searchText)
            searchHandler.removeCallbacks(debouncedSearch)
            searchHandler.postDelayed(debouncedSearch, SEARCH_DEBOUNCE_MS)
        }

        override fun onSearchSubmitted(searchText: String) {
            // A blank submit often accompanies the host clearing the box after
            // voice input; do not cancel the pending transcript search.
            if (searchText.isBlank()) return
            searchHandler.removeCallbacks(debouncedSearch)
            NavigationController.setQuery(searchText)
            lastResultsStamp = ""
            NavigationController.searchAddress(searchText)
        }
    }

    private fun resultsStamp(): String {
        val nav = NavigationController
        val titles = nav.searchResults.take(6).joinToString("~") { it.title }
        return "$titles|${nav.statusMessage.orEmpty()}"
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
            results.take(6).forEach { candidate ->
                list.addItem(
                    Row.Builder()
                        .setTitle(candidate.title.ifBlank { "Place" })
                        .addText(candidate.subtitle.ifBlank { candidate.displayName })
                        .setOnClickListener {
                            NavigationController.selectDestination(candidate, autoStartNav = true)
                            // Reveal the live map whether search was opened
                            // from the map, the hub or settings. The map is the
                            // root screen, so this collapses every screen above
                            // it instead of returning to a menu while routing.
                            try {
                                screenManager.popToRoot()
                            } catch (_: Exception) {
                                finish()
                            }
                        }
                        .build(),
                )
            }
        }
        val builder = SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("Place or address")
            .setItemList(list.build())
        // Re-send the last real transcript on every update. Some hosts clear
        // the search box after a voice submit; without this the field stays
        // empty even though results are ready to tap.
        val query = NavigationController.searchQuery
        if (query.isNotBlank()) {
            builder.setInitialSearchText(query)
        }
        if (firstTemplate) {
            builder.setShowKeyboardByDefault(query.isBlank())
            firstTemplate = false
        }
        return builder.build()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 700L
    }
}
