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
        "resmi" to "Yayıncı Kaynakları",
        "Tvjustin" to "Tvjustin",
        "Trgoals" to "Trgoals",
        "DoğuSpor" to "DoğuSpor",
        "Vavoo Turkey" to "Vavoo Turkey",
    )

    private val locale = Locale.forLanguageTag("tr-TR")
    private val channels = SporBCatalogue.channels
    private val official = listOf(
        SportsChannel(1001, "TRT Spor", "Spor", "TRT", "https://tv-trtspor1.medya.trt.com.tr/master.m3u8", emptyMap()),
        SportsChannel(1002, "TRT Spor Yıldız", "Spor", "TRT", "https://tv-trtspor2.medya.trt.com.tr/master.m3u8", emptyMap()),
        SportsChannel(1003, "A Spor", "Spor", "A Spor", "https://trkvz-live.daioncdn.net/aspor/aspor.m3u8", emptyMap()),
    )
    private val officialBackups = mapOf(
        1001 to "https://trt.daioncdn.net/trtspor/master.m3u8?app=web&platform=trtspor",
        1002 to "https://trt.daioncdn.net/trtspor-yildiz/master.m3u8?app=web&platform=trtspor",
    )
    private val allChannels = official + channels
    private val byId = allChannels.associateBy { it.id }

    private fun findChannel(value: String): SportsChannel? {
        val key = value.trim().substringBefore('?').substringBefore('#').trimEnd('/')
            .substringAfterLast('/').toIntOrNull() ?: return null
        return byId[key]
    }
    private fun itemUrl(channel: SportsChannel) = "$mainUrl/sporb/${channel.id}"
    private fun card(channel: SportsChannel) =
        newLiveSearchResponse(channel.name, itemUrl(channel), TvType.Live, fix = false)

    override suspend fun getMainPage(page: Int, request: MainPageRequest) =
        newHomePageResponse(
            request.name,
            (if (request.data == "resmi") official else channels.filter { it.source == request.data }).map(::card),
            hasNext = false,
        )

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim().lowercase(locale)
        return allChannels.filter { row ->
            listOf(row.name, row.group, row.source).any { it.lowercase(locale).contains(term) }
        }.map(::card)
    }

    override suspend fun load(url: String) = findChannel(url)?.let { row ->
        newLiveStreamLoadResponse(row.name, itemUrl(row), itemUrl(row)) {
            plot = "${row.group} · ${row.source}"
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val row = findChannel(data) ?: return false
        val streams = listOfNotNull(row.url, officialBackups[row.id])
        streams.forEachIndexed { index, stream ->
            val path = stream.substringBefore('#')
            val type = when {
                path.contains(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                path.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
            val label = if (index == 0) row.source else "${row.source} · Yedek"
            callback(newExtractorLink(name, label, stream, type) {
                referer = row.headers["Referer"].orEmpty()
                headers = row.headers
            })
        }
        return true
    }
}
