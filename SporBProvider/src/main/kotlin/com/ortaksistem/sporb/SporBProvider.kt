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
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.ortaksistem.seyirbox.catalog.BrowserPlayback
import com.ortaksistem.seyirbox.catalog.CatalogProvider
import com.ortaksistem.seyirbox.catalog.filmCatalogs
import com.ortaksistem.seyirbox.catalog.seriesCatalogs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer
import java.util.Locale

class SporBProvider internal constructor(
    private val openWebChannel: suspend (SportsWebChannel) -> Boolean,
) : MainAPI() {
    constructor() : this({ BrowserPlayback.open(it.name, it.page, it.waitSeconds) })

    override var name = "SEYİRBOX"
    override var mainUrl = "https://github.com/" + SporBCatalogue.repository
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)
    override val hasMainPage = true

    private val film = CatalogProvider(name, TvType.Movie, filmCatalogs)
    private val dizi = CatalogProvider(name, TvType.TvSeries, seriesCatalogs)
    private val sharedCatalogNames = film.mainPage.map { it.name }.intersect(dizi.mainPage.map { it.name }.toSet())
    private val webPages: List<Pair<MainPageData, CatalogProvider>> = listOf(film, dizi).flatMap { provider ->
        provider.mainPage.map { page ->
            val label = if (provider === dizi && page.name in sharedCatalogNames) "${page.name} Dizileri" else page.name
            mainPage(page.data, label, page.horizontalImages) to provider
        }
    }
    private val webPageProviders = webPages.associate { (page, provider) -> page.data to provider }

    private val browserGroups = SportsWebCatalogue.groups.associateBy { it.id }
    private val browserChannels = browserGroups.values.flatMap { it.channels }.associateBy { it.id }
    private val browserPrefix get() = "$mainUrl/spor-web/"

    override val mainPage: List<MainPageData> = webPages.map { it.first } + listOf(
        mainPage("resmi", "Yayıncı Kaynakları", horizontalImages = true),
        mainPage("Tvjustin", "JUSTIN SPOR", horizontalImages = true),
        mainPage("Trgoals", "TRGOALS", horizontalImages = true),
        mainPage("DoğuSpor", "DOĞU SPOR", horizontalImages = true),
        mainPage("Vavoo Turkey", "VAVOO SPOR", horizontalImages = true),
    ) + browserGroups.values.map { mainPage(it.id, it.name, horizontalImages = true) }

    private fun webProvider(url: String): CatalogProvider? = when {
        film.handles(url) -> film
        dizi.handles(url) -> dizi
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
        SportsChannel(1003, "A Spor", "Spor", "A Spor", "https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/aspor/aspor.m3u8", emptyMap()),
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
    private fun card(channel: SportsChannel, portrait: Boolean = false) =
        newLiveSearchResponse(channel.name, itemUrl(channel), TvType.Live, fix = false) {
            posterUrl = ChannelArtwork.posterUrl(channelKey(channel.name), portrait)
        }

    private fun browserChannel(url: String): SportsWebChannel? =
        url.takeIf { it.startsWith(browserPrefix) }?.removePrefix(browserPrefix)?.let(browserChannels::get)

    private fun browserCard(channel: SportsWebChannel, portrait: Boolean = false): SearchResponse =
        newLiveSearchResponse(channel.name, browserPrefix + channel.id, TvType.Live, fix = false) {
            posterUrl = ChannelArtwork.posterUrl(channelKey(channel.name), portrait)
        }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        webPageProviders[request.data]?.let { return it.getMainPage(page, request) }
        val items = if (page == 1) {
            browserGroups[request.data]?.channels?.map { browserCard(it) }
                ?: channelsForGroup(request.data).map { card(it) }
        } else emptyList()
        return newHomePageResponse(request, items, hasNext = false)
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
        }.map { card(it, portrait = true) }
        val browser = browserGroups.values.flatMap { group ->
            group.channels.filter { channel ->
                channel.name.lowercase(locale).contains(term) || group.name.lowercase(locale).contains(term)
            }.map { browserCard(it, portrait = true) }
        }
        filmResults.await() + diziResults.await() + sports + browser
    }

    override suspend fun load(url: String): LoadResponse? {
        webProvider(url)?.let { return it.load(url) }
        browserChannel(url)?.let { channel ->
            return newMovieLoadResponse(channel.name, channel.page, TvType.CustomMedia, url) {
                uniqueUrl = url
                posterUrl = ChannelArtwork.posterUrl(channelKey(channel.name))
                plot = if (channel.waitSeconds > 0) {
                    "Oynat’a basınca ${channel.waitSeconds} saniye sonra yayın sayfası tarayıcıda açılır."
                } else {
                    "Oynat’a basınca kaynak sayfası tarayıcıda açılır."
                }
            }
        }
        val row = findChannel(url) ?: return null
        return newLiveStreamLoadResponse(row.name, itemUrl(row), itemUrl(row)) {
            posterUrl = ChannelArtwork.posterUrl(channelKey(row.name))
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
        browserChannel(data)?.let { return !isCasting && openWebChannel(it) }
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
