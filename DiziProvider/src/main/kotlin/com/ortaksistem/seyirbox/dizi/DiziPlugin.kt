package com.ortaksistem.seyirbox.dizi

import android.content.Context
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.ortaksistem.seyirbox.catalog.CatalogProvider
import com.ortaksistem.seyirbox.catalog.DiziBox

@CloudstreamPlugin
class DiziPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(CatalogProvider("DİZİ", TvType.TvSeries, listOf(DiziBox)))
    }
}
