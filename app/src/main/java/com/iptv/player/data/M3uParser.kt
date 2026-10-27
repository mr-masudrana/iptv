package com.iptv.player.data

object M3uParser {
    private val attrRegex = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var name: String? = null
        var logo: String? = null
        var group = ""

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF") -> {
                    val attrs = attrRegex.findAll(line)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val lastQuote = line.lastIndexOf('"')
                    val commaIdx = line.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
                    var n = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else ""
                    if (n.isEmpty()) n = attrs["tvg-name"].orEmpty()
                    name = n.ifEmpty { null }
                    logo = attrs["tvg-logo"]?.ifBlank { null }
                    group = attrs["group-title"] ?: group
                }
                line.startsWith("#EXTGRP:") -> group = line.removePrefix("#EXTGRP:").trim()
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> {
                    result.add(
                        Channel(
                            name = name ?: line,
                            url = line,
                            logo = logo,
                            group = group.ifBlank { "Others" }
                        )
                    )
                    name = null
                    logo = null
                    group = ""
                }
            }
        }
        return result
    }
}
