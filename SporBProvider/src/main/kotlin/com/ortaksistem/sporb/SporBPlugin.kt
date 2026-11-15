package com.ortaksistem.sporb

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class SporBPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(SporBProvider())
    }
}
