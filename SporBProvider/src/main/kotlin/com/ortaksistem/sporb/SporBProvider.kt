package com.ortaksistem.sporb

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.Locale

class SporBProvider : MainAPI() {
    override var name = "OSPlayer SporB"
    override var mainUrl = "https://github.com/" + SporBCatalogue.repository
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "Tvjustin" to "Tvjustin",
        "Trgoals" to "Trgoals",
        "DoğuSpor" to "DoğuSpor",
        "Vavoo Turkey" to "Vavoo Turkey",
    )

    private val locale = Locale.forLanguageTag("tr-TR")
    private val channels = SporBCatalogue.channels
    private val byId = channels.associateBy { it.id }

    private fun findChannel(url: String) = byId[url.substringAfterLast('/').toIntOrNull()]
    private fun itemUrl(channel: SportsChannel) = "$mainUrl/sporb/${channel.id}"
    private fun card(channel: SportsChannel) =
        newLiveSearchResponse(channel.name, itemUrl(channel), TvType.Live, fix = false)

    override suspend fun getMainPage(page: Int, request: MainPageRequest) =
        newHomePageResponse(
            request.name,
            channels.filter { it.source == request.data }.map(::card),
            hasNext = false,
        )

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim().lowercase(locale)
        return channels.filter { row ->
            listOf(row.name, row.group, row.source).any { it.lowercase(locale).contains(term) }
        }.map(::card)
    }

    override suspend fun load(url: String) = findChannel(url)?.let { row ->
        newLiveStreamLoadResponse(row.name, itemUrl(row), row.id.toString()) {
            plot = "${row.group} · ${row.source}"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val row = byId[data.toIntOrNull()] ?: return false
        val type = if (row.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            ExtractorLinkType.M3U8
        } else {
            // Several playlist entries use URLs without filenames. Let the player infer their type.
            null
        }
        callback(newExtractorLink(name, row.source, row.url, type) {
            referer = row.headers["Referer"].orEmpty()
            headers = row.headers.filterKeys { it != "Referer" }
        })
        return true
    }
}
