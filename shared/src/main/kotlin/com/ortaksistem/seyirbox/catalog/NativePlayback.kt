package com.ortaksistem.seyirbox.catalog

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.util.Locale

internal data class PlayerPage(val url: String, val referer: String)
internal data class NativeSources(val media: List<Media>, val players: List<PlayerPage>)

internal suspend fun WebCatalog.resolveMedia(
    document: Document,
    pageLoader: suspend (WebCatalog, String) -> Document?,
    alternativeLoader: suspend (WebCatalog, String, String) -> Document?,
): NativeSources = coroutineScope {
    val original = document.location()
    val primaryPlayers = embeddedPlayers(document)
    val alternativeRequests = alternativePlayerUrls(document).distinct().take(6).map { url ->
        async { alternativeLoader(this@resolveMedia, url, original) }
    }
    val additionalPlayers = async { extraPlayerDocuments(document) }
    val sameSitePlayers = primaryPlayers.filter(::supports).filter { it != original }.take(2)
    val playerPages = sameSitePlayers.mapNotNull { pageLoader(this@resolveMedia, it) }
    val alternatives = alternativeRequests.awaitAll().filterNotNull() + additionalPlayers.await()
    val documents = listOf(document) + playerPages + alternatives
    val allPlayers = documents.flatMap { page ->
        val referer = if (page in alternatives) original else page.location()
        embeddedPlayers(page).map { PlayerPage(it, referer) }
    }.distinctBy(PlayerPage::url)
    val media = documents.flatMap { page ->
        (publicMedia(page) + structuredMedia(page) + embeddedMedia(page)).map {
            if (it.referer == null) it.copy(referer = page.location()) else it
        }
    } + allPlayers.mapNotNull { mediaAt(it.url, "")?.copy(referer = it.referer) }
    NativeSources(media, allPlayers)
}

internal suspend fun emitNativeLinks(
    provider: String,
    label: String,
    document: Document,
    media: List<Media>,
    embeds: List<PlayerPage>,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val seen = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    for (item in media.distinctBy(Media::url)) {
        val type = when (item.format) {
            MediaFormat.HLS -> ExtractorLinkType.M3U8
            MediaFormat.DASH -> ExtractorLinkType.DASH
            MediaFormat.VIDEO -> ExtractorLinkType.VIDEO
        }
        seen += item.url
        callback(newExtractorLink(provider, label, item.url, type) {
            referer = item.referer ?: document.location()
            item.resolution?.let { quality = it }
        })
    }
    for ((url, referer) in embeds.distinctBy(PlayerPage::url).take(10)) {
        if (url in seen) continue
        val player = httpsUri(url) ?: continue
        if (player.port !in setOf(-1, 443)) continue
        val host = player.host.lowercase(Locale.ROOT).removePrefix("www.")
        val matches = extractorApis.asReversed().filter {
            val api = httpsUri(it.mainUrl)
            api?.host?.lowercase(Locale.ROOT)?.removePrefix("www.") == host &&
                api.port in setOf(-1, 443)
        }
        if (matches.isEmpty()) continue
        for (extractor in matches.take(3)) {
            val oldCount = seen.size
            try {
                withTimeoutOrNull(8_000) {
                    extractor.getUrl(url, referer, subtitleCallback) { link ->
                        if (isNativeLink(link) && seen.add(link.url)) callback(link)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Another registered extractor can support this player.
            }
            if (seen.size > oldCount) break
        }
    }
    return seen.isNotEmpty()
}

private val nativeTypes = setOf(ExtractorLinkType.M3U8, ExtractorLinkType.DASH, ExtractorLinkType.VIDEO)

internal fun isNativeLink(link: ExtractorLink): Boolean = isHttpsUrl(link.url) && link.type in nativeTypes

internal fun iframePlayers(document: Element): List<String> =
    document.select("iframe[src], iframe[data-src], iframe[data-litespeed-src]").asSequence()
        .filterNot { frame ->
            listOf(frame.id(), frame.attr("title"), frame.className()).any {
                it.contains("trailer", true) || it.contains("fragman", true)
            }
        }
        .flatMap { frame -> sequenceOf(frame.absUrl("src"), frame.absUrl("data-src"), frame.absUrl("data-litespeed-src")) }
        .filter(::isHttpsUrl)
        .distinct()
        .take(6)
        .toList()

internal fun jsonValue(text: String): Any? = try {
    JSONTokener(text).nextValue()
} catch (_: JSONException) {
    null
}

internal fun jsonMedia(value: Any?, baseUrl: String, referer: String = baseUrl, depth: Int = 0): List<Media> {
    if (depth > 5) return emptyList()
    return when (value) {
        is JSONArray -> (0 until minOf(value.length(), 24)).flatMap { jsonMedia(value.opt(it), baseUrl, referer, depth + 1) }
        is JSONObject -> {
            if (sequenceOf("drm", "keySystem", "licenseUrl", "widevine", "playready", "fairplay", "contentProtection").any {
                value.has(it) && !value.isNull(it) && value.opt(it) != false && value.optString(it).isNotBlank()
            }) return emptyList()
            val mime = sequenceOf("type", "mimeType", "encodingFormat")
                .map(value::optString).firstOrNull(String::isNotBlank).orEmpty()
            val urls = sequenceOf("url", "file", "src", "contentUrl", "streamUrl", "hlsUrl", "dashUrl")
                .map(value::optString).filter(String::isNotBlank)
                .mapNotNull { resolveMedia(baseUrl, it, mime, referer) }.toList()
            urls + sequenceOf("data", "sources", "streams", "playlist", "video", "videos", "media")
                .flatMap { jsonMedia(value.opt(it), baseUrl, referer, depth + 1) }.toList()
        }
        is String -> listOfNotNull(resolveMedia(baseUrl, value, "", referer))
        else -> emptyList()
    }
}

private fun resolveMedia(baseUrl: String, value: String, mime: String, referer: String): Media? {
    val url = runCatching { URI(baseUrl).resolve(value).toString() }.getOrNull() ?: return null
    return mediaAt(url, mime)?.copy(referer = referer)
}
