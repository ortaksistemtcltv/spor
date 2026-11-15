package com.ortaksistem.sporb

internal object ChannelArtwork {
    private val families = mapOf(
        "bein sports" to "bein-sports",
        "bein spots1" to "bein-sports-1",
        "exxen sports" to "exxen-spor",
        "s sport" to "s-sport",
        "s sport plus" to "s-sport-plus",
        "tivibu spor" to "tivibu-spor",
        "smart spor" to "spor-smart",
        "trt spor" to "trt-spor",
        "trt spor yildiz" to "trt-spor-yildiz",
        "trt spor 2" to "trt-spor-yildiz",
        "trt 1" to "trt-1",
        "trt 3" to "trt-3",
        "a spor" to "a-spor",
        "nba tv" to "nba-tv",
        "eurosport" to "eurosport",
        "fb tv" to "fb-tv",
        "gs tv" to "gs-tv",
        "tabii spor" to "tabii-spor",
        "sports tv" to "sports-tv",
        "sportstv" to "sports-tv",
        "t a y tv" to "tay-tv",
        "tay tv" to "tay-tv",
        "tay" to "tay-tv",
    )

    private val feedSuffix = Regex(""" (?:live during events only|match time|mac saati)$""")
    private val numberedChannel = Regex("""(bein sports(?: max)?|exxen sports|s sports?|tivibu spor|smart spor|eurosport|tabii spor) *([1-6])""")
    private val sportPlus = Regex("""s sports?\s*\+\s*([1-5])""")

    internal fun artworkId(channelKey: String): String {
        val key = channelKey.replace(feedSuffix, "").trim()
            .replace(Regex("^tabi spor"), "tabii spor")
            .replace(Regex("^exxen ([1-4])$"), "exxen sports $1")
            .replace(Regex("^s sport plus ([1-5])$"), "s sport+$1")
        if (key in setOf("exxen", "exxen tv", "exxen sports")) return "exxen-spor-1"
        if (key == "smart spor 1") return "spor-smart"
        if (key.startsWith("bein sports 4k feed")) return "bein-sports-4k"
        sportPlus.matchEntire(key)?.let { return "s-sport-plus-${it.groupValues[1]}" }
        numberedChannel.matchEntire(key)?.let { match ->
            val (family, number) = match.destructured
            val slug = when (family) {
                "bein sports max" -> "bein-sports-max"
                "s sports" -> "s-sport"
                else -> families.getValue(family)
            }
            val maxNumber = when (slug) {
                "bein-sports" -> 5
                "exxen-spor", "tivibu-spor" -> 4
                "tabii-spor" -> 6
                else -> 2
            }
            return when {
                number.toInt() > maxNumber -> "canli-spor"
                slug == "s-sport" && number == "1" -> slug
                else -> "$slug-$number"
            }
        }
        return families[key] ?: "canli-spor"
    }

    fun posterUrl(channelKey: String, portrait: Boolean = false): String {
        val variant = if (portrait) "-portre" else ""
        return "https://raw.githubusercontent.com/${SporBCatalogue.repository}/main/assets/channel-logos/v14/" +
            artworkId(channelKey) + variant + ".webp"
    }
}
