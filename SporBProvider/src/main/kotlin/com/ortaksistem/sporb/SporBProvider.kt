package com.ortaksistem.sporb

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPage
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.ortaksistem.seyirbox.catalog.CatalogProvider
import com.ortaksistem.seyirbox.catalog.DiziBox
import com.ortaksistem.seyirbox.catalog.FilmCehennemi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer
import java.util.Locale

class SporBProvider : MainAPI() {
    override var name = "SEYİRBOX"
    override var mainUrl = "https://github.com/" + SporBCatalogue.repository
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)
    override val hasMainPage = true

    private val film = CatalogProvider(name, TvType.Movie, listOf(FilmCehennemi))
    private val dizi = CatalogProvider(name, TvType.TvSeries, listOf(DiziBox))
    private val webPages: List<Pair<MainPageData, CatalogProvider>> = listOf("FİLM" to film, "DİZİ" to dizi).flatMap { (section, provider) ->
        provider.mainPage.map { page ->
            mainPage(page.data, "$section · ${page.name}", page.horizontalImages) to provider
        }
    }
    private val webPageProviders = webPages.associate { (page, provider) -> page.data to provider }

    override val mainPage: List<MainPageData> = webPages.map { it.first } + listOf(
        mainPage("resmi", "SPOR · Yayıncı Kaynakları"),
        mainPage("Tvjustin", "SPOR · JUSTIN SPOR"),
        mainPage("Trgoals", "SPOR · TRGOALS"),
        mainPage("DoğuSpor", "SPOR · DOĞU SPOR"),
        mainPage("Vavoo Turkey", "SPOR · VAVOO SPOR"),
    )

    private fun webProvider(url: String): CatalogProvider? = when {
        FilmCehennemi.supports(url) -> film
        DiziBox.supports(url) -> dizi
        else -> null
    }

    private val displaySourceNames = mapOf(
        "Tvjustin" to "JUSTIN SPOR",
        "Trgoals" to "TRGOALS",
        "DoğuSpor" to "DOĞU SPOR",
        "Vavoo Turkey" to "VAVOO SPOR",
    )

    private fun sourceLabel(source: String): String = displaySourceNames[source] ?: source

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
    private data class Stream(val label: String, val url: String, val headers: Map<String, String>)

    private val allChannels = official + channels
    private val officialByName = official.associateBy { channelKey(it.name) }
    private val mirrorsByName = channels.groupBy { channelKey(it.name) }

    private fun channelKey(name: String): String {
        val plain = Normalizer.normalize(name.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFKD)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
            .trim()
            .replace(Regex("^4k\\s+tr:\\s*"), "")
            .replace(Regex("\\s+\\.[abcs]$"), "")
            .replace(Regex("\\s+\\((?:[0-9]{3,4}p|turkey)\\)$"), "")
            .replace(Regex("(?:\\s+(?:4k|uhd|fhd|hd|sd|hevc|[0-9]{3,4}p|[0-9]+ fps))+$"), "")
            .replace(Regex("[^a-z0-9+]+"), " ")
            .trim()
        return plain
            .replace(Regex("^beinsports\\b"), "bein sports")
            .replace(Regex("^bein sports ([12]) max$"), "bein sports max $1")
            .replace(Regex("^exxen (?:tv )?spor(?:ts)? (?=[0-9])"), "exxen sports ")
            .replace(Regex("^euro ?sport ?(?=[12]$)"), "eurosport ")
            .replace(Regex("^(?:tivibuspor|tivibu sports?)\\b"), "tivibu spor")
            .replace(Regex("^spor smart\\b"), "smart spor")
            .replace(Regex("^s sports? 1$"), "s sport")
            .replace(Regex("^s sports 2$"), "s sport 2")
    }

    private fun streamsFor(channel: SportsChannel): List<Stream> {
        val key = channelKey(channel.name)
        val broadcaster = officialByName[key]
        val alternateKey = if (channel.source == "Vavoo Turkey") {
            val baseName = key.replace(Regex(" (?:[0-9]{3,4}p ?feed|live during events only|match time)$"), "")
            baseName.takeIf { it != key && it.matches(Regex("bein sports [1-5]")) }
        } else {
            null
        }
        val matches = mirrorsByName[key].orEmpty() +
            (alternateKey?.let { mirrorsByName[it] }.orEmpty())
        val matchingChannels = if (broadcaster != null) {
            if (channel.id == broadcaster.id) listOf(broadcaster) else {
                listOf(broadcaster, channel) + matches
            }
        } else {
            listOf(channel) + matches
        }

        return matchingChannels.distinctBy { it.id }.flatMap { match ->
            listOfNotNull(
                Stream(sourceLabel(match.source), match.url, match.headers),
                officialBackups[match.id]?.let { Stream("${match.source} · Yedek", it, emptyMap()) },
            )
        }.distinctBy { it.url }
    }
    private val byId = allChannels.associateBy { it.id }

    private fun findChannel(value: String): SportsChannel? {
        val url = value.trim().substringBefore('?').substringBefore('#').trimEnd('/')
        val prefix = "$mainUrl/sporb/"
        if (!url.startsWith(prefix)) return null
        val id = url.removePrefix(prefix).toIntOrNull() ?: return null
        return byId[id]
    }
    private fun itemUrl(channel: SportsChannel) = "$mainUrl/sporb/${channel.id}"
    private fun card(channel: SportsChannel) =
        newLiveSearchResponse(channel.name, itemUrl(channel), TvType.Live, fix = false)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        webPageProviders[request.data]?.let { return it.getMainPage(page, request) }
        val items = if (page == 1) channelsForGroup(request.data).map(::card) else emptyList()
        return newHomePageResponse(request.name, items, hasNext = false)
    }

    private fun channelsForGroup(source: String): List<SportsChannel> =
        when (source) {
            "resmi" -> official
            else -> channels.filter { it.source == source }
        }

    override suspend fun search(query: String): List<SearchResponse> = coroutineScope {
        val term = query.trim().lowercase(locale)
        if (term.isEmpty()) return@coroutineScope emptyList()
        val filmResults = async { film.search(query) }
        val diziResults = async { dizi.search(query) }
        val sports = allChannels.filter { row ->
            listOf(row.name, row.group, row.source, sourceLabel(row.source)).any { it.lowercase(locale).contains(term) }
        }.map(::card)
        filmResults.await() + diziResults.await() + sports
    }

    override suspend fun load(url: String): LoadResponse? {
        webProvider(url)?.let { return it.load(url) }
        val row = findChannel(url) ?: return null
        return newLiveStreamLoadResponse(row.name, itemUrl(row), itemUrl(row)) {
            val count = streamsFor(row).size
            plot = "${row.group} · ${sourceLabel(row.source)}" +
                if (count > 1) "\n$count kaynak var. Yayın açılmazsa Kaynaklar menüsünden diğerini seç." else ""
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        webProvider(data)?.let { return it.loadLinks(data, isCasting, subtitleCallback, callback) }
        val row = findChannel(data) ?: return false
        val streams = streamsFor(row)
        streams.forEach { stream ->
            val path = stream.url.substringBefore('#')
            val type = when {
                path.contains(".m3u8", ignoreCase = true) -> ExtractorLinkType.M3U8
                path.substringBefore('?').endsWith(".mpd", ignoreCase = true) -> ExtractorLinkType.DASH
                else -> ExtractorLinkType.VIDEO
            }
            callback(newExtractorLink(name, stream.label, stream.url, type) {
                referer = stream.headers["Referer"].orEmpty()
                headers = stream.headers
            })
        }
        return true
    }
}
