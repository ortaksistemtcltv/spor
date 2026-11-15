package com.ortaksistem.sporb

import android.content.Context
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SporBPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SporBProvider())
    }
}
