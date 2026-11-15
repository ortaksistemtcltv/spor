package com.ortaksistem.seyirbox.film

import android.content.Context
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.ortaksistem.seyirbox.catalog.CatalogProvider
import com.ortaksistem.seyirbox.catalog.FilmCehennemi

@CloudstreamPlugin
class FilmPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CatalogProvider("FİLM", TvType.Movie, listOf(FilmCehennemi)))
    }
}
