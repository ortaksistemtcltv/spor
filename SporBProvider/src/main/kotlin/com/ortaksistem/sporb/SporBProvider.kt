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
import java.text.Normalizer
import java.util.Locale

class SporBProvider : MainAPI() {
    override var name = "SPOR KANALI"
    override var mainUrl = "https://github.com/" + SporBCatalogue.repository
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Live)
    override val hasMainPage = true
    override val mainPage = mainPageOf(
        "resmi" to "Yayıncı Kaynakları",
        "Tvjustin" to "SPOR 1",
        "Trgoals" to "SPOR 2",
        "DoğuSpor" to "SPOR 3",
        "Vavoo Turkey" to "SPOR 4",
        "spor5" to "SPOR 5",
        "spor6" to "SPOR 6",
    )

    private val displaySourceNames = mapOf(
        "Tvjustin" to "SPOR 1",
        "Trgoals" to "SPOR 2",
        "DoğuSpor" to "SPOR 3",
        "Vavoo Turkey" to "SPOR 4",
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
    private val importedGroups = SporBCatalogue.groupNames.mapValues { (_, names) ->
        names.distinctBy(::channelKey).mapNotNull { channelName ->
            val key = channelKey(channelName)
            officialByName[key] ?: mirrorsByName[key]?.firstOrNull()
        }.distinctBy { it.id }
    }

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
        val key = value.trim().substringBefore('?').substringBefore('#').trimEnd('/')
            .substringAfterLast('/').toIntOrNull() ?: return null
        return byId[key]
    }
    private fun itemUrl(channel: SportsChannel) = "$mainUrl/sporb/${channel.id}"
    private fun card(channel: SportsChannel) =
        newLiveSearchResponse(channel.name, itemUrl(channel), TvType.Live, fix = false)

    override suspend fun getMainPage(page: Int, request: MainPageRequest) =
        newHomePageResponse(request.name, channelsForGroup(request.data).map(::card), hasNext = false)

    private fun channelsForGroup(source: String): List<SportsChannel> =
        when (source) {
            "resmi" -> official
            "spor5", "spor6" -> importedGroups[source].orEmpty()
            else -> channels.filter { it.source == source }
        }

    override suspend fun search(query: String): List<SearchResponse> {
        val term = query.trim().lowercase(locale)
        return allChannels.filter { row ->
            listOf(row.name, row.group, row.source, sourceLabel(row.source)).any { it.lowercase(locale).contains(term) }
        }.map(::card)
    }

    override suspend fun load(url: String) = findChannel(url)?.let { row ->
        newLiveStreamLoadResponse(row.name, itemUrl(row), itemUrl(row)) {
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
