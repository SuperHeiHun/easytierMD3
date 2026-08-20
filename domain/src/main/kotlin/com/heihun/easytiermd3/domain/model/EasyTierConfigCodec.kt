package com.heihun.easytiermd3.domain.model

import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.ProxyNetworkConfig
import com.heihun.easytiermd3.domain.model.EasyTierConfigCodec.ConfigParseException

/**
 * EasyTierConfig <-> TOML 配置文本的编解码。
 * 用于简单模式表单与高级模式原始配置之间的互转。
 *
 * 编码采用 easytier-core 当前版本的扁平 TOML schema：
 * 顶层 instance_name/hostname/ipv4|dhcp/listeners + [network_identity] + [[peer]] + [[proxy_network]] + [flags]。
 * 解码兼容旧版 [network]/[instance] 分段结构（best-effort 行级解析）。
 *
 * [merge] 在保留原始 TOML 中未知字段（如 [flags]、[[proxy_network]] 之外的高级段）的前提下，
 * 用更新后的 [EasyTierConfig] 替换已知字段，避免普通模式编辑丢失高级配置。
 */
object EasyTierConfigCodec {

    private val sectionRegex = Regex("^\\[(\\w+)]$")
    private val peerSectionRegex = Regex("^\\[\\[(\\w+)]]$")
    private val keyValueRegex = Regex("^([\\w-]+)\\s*=\\s*(.*)$")
    private val stringValueRegex = Regex("^\"((?:[^\"\\\\]|\\\\.)*)\"$")
    private val urlRegex = Regex("^\\w+://[^:]+:(\\d+)$")
    private val ipv4WithPrefixRegex = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){3})/(\\d{1,2})$")

    /** 顶层已知字段：merge 时被替换或删除（instance_name 由 Core 启动时生成，原样保留）。 */
    private val knownTopLevelKeys = setOf(
        "hostname", "ipv4", "dhcp", "listeners",
    )
    private val knownIdentityKeys = setOf("network_name", "network_secret")
    private val knownPeerKeys = setOf("uri")

    fun encode(config: EasyTierConfig): String = buildString {
        config.hostname?.takeIf { it.isNotBlank() }?.let {
            appendLine("hostname = ${quote(it)}")
        }
        config.ipv4?.takeIf { it.isNotBlank() }?.let { ipv4 ->
            val prefix = config.cidr?.substringAfter('/', "24")?.takeIf { it.isNotBlank() } ?: "24"
            val addr = ipv4.substringBefore('/')
            appendLine("ipv4 = ${quote("$addr/$prefix")}")
        } ?: appendLine("dhcp = true")
        if (config.listenPort != 0) {
            val port = config.listenPort
            appendLine(
                "listeners = [${quote("tcp://0.0.0.0:$port")}, ${quote("udp://0.0.0.0:$port")}]"
            )
        }
        appendLine()
        appendLine("[network_identity]")
        appendLine("network_name = ${quote(config.networkName)}")
        config.networkSecret?.takeIf { it.isNotBlank() }?.let {
            appendLine("network_secret = ${quote(it)}")
        }
        config.proxyNetworks.forEach { proxy ->
            appendLine()
            appendLine("[[proxy_network]]")
            appendLine("cidr = ${quote(proxy.cidr)}")
            proxy.mappedCidr?.takeIf { it.isNotBlank() }?.let {
                appendLine("mapped_cidr = ${quote(it)}")
            }
            if (proxy.allow.isNotEmpty()) {
                appendLine("allow = [${proxy.allow.joinToString(", ") { quote(it) }}]")
            }
        }
        config.startNodes.filter { it.isNotBlank() }.forEach { uri ->
            appendLine()
            appendLine("[[peer]]")
            appendLine("uri = ${quote(uri)}")
        }
    }

    /**
     * 在原始 TOML 基础上用 [config] 替换已知字段，并保留所有未知内容
     * （未知顶层键、[flags] 等未知 section、[[peer]] 内的 peer_public_key 等）。
     * [original] 为空时等价于 [encode]。
     */
    fun merge(original: String, config: EasyTierConfig): String {
        if (original.isBlank()) return encode(config)
        val parts = splitOriginal(original)
        return buildString {
            config.hostname?.takeIf { it.isNotBlank() }?.let {
                appendLine("hostname = ${quote(it)}")
            }
            config.ipv4?.takeIf { it.isNotBlank() }?.let { ipv4 ->
                val prefix = config.cidr?.substringAfter('/', "24")?.takeIf { it.isNotBlank() } ?: "24"
                val addr = ipv4.substringBefore('/')
                appendLine("ipv4 = ${quote("$addr/$prefix")}")
            } ?: appendLine("dhcp = true")
            if (config.listenPort != 0) {
                val port = config.listenPort
                appendLine(
                    "listeners = [${quote("tcp://0.0.0.0:$port")}, ${quote("udp://0.0.0.0:$port")}]"
                )
            }
            parts.topUnknown.forEach { appendLine(it) }
            parts.unknownSections.forEach { block ->
                appendLine()
                appendLine(block.header)
                block.body.forEach { appendLine(it) }
            }
            appendLine()
            appendLine("[network_identity]")
            appendLine("network_name = ${quote(config.networkName)}")
            config.networkSecret?.takeIf { it.isNotBlank() }?.let {
                appendLine("network_secret = ${quote(it)}")
            }
            parts.identityExtras.forEach { appendLine(it) }
            config.proxyNetworks.forEach { proxy ->
                appendLine()
                appendLine("[[proxy_network]]")
                appendLine("cidr = ${quote(proxy.cidr)}")
                proxy.mappedCidr?.takeIf { it.isNotBlank() }?.let {
                    appendLine("mapped_cidr = ${quote(it)}")
                }
                if (proxy.allow.isNotEmpty()) {
                    appendLine("allow = [${proxy.allow.joinToString(", ") { quote(it) }}]")
                }
            }
            config.startNodes.filter { it.isNotBlank() }.forEachIndexed { index, uri ->
                appendLine()
                appendLine("[[peer]]")
                appendLine("uri = ${quote(uri)}")
                parts.peerExtras.getOrNull(index)?.forEach { appendLine(it) }
            }
        }
    }

    fun decode(text: String): Result<EasyTierConfig> = runCatching {
        var section = ""
        var networkName: String? = null
        var networkSecret: String? = null
        var cidr: String? = null
        var hostname: String? = null
        var ipv4: String? = null
        var ipv4Prefix = 24
        var listenPort = 11010
        val startNodes = mutableListOf<String>()
        val listeners = mutableListOf<String>()
        val proxyNetworks = mutableListOf<ProxyNetworkConfig>()
        var pendingProxy: ProxyNetworkConfig? = null

        fun flushProxy() {
            pendingProxy?.let { proxyNetworks += it }
            pendingProxy = null
        }

        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            peerSectionRegex.find(line)?.let {
                flushProxy()
                section = it.groupValues[1]
                return@forEachIndexed
            }
            sectionRegex.find(line)?.let {
                flushProxy()
                section = it.groupValues[1]
                return@forEachIndexed
            }
            val kv = keyValueRegex.find(line)
                ?: throw ConfigParseException(lineNumber = index + 1, message = "无法解析的行: $line")
            val key = kv.groupValues[1]
            val rawValue = kv.groupValues[2].trim()
            val strValue = stringValueRegex.find(rawValue)?.groupValues?.get(1)
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?: rawValue.trim('"')

            when (section) {
                "network_identity" -> when (key) {
                    "network_name" -> networkName = strValue
                    "network_secret" -> networkSecret = strValue
                }
                // 兼容旧版分段结构
                "network" -> when (key) {
                    "network_name" -> networkName = strValue
                    "network_secret" -> networkSecret = strValue
                    "cidr" -> cidr = strValue
                }
                "instance" -> when (key) {
                    "hostname" -> hostname = strValue
                    "ipv4" -> ipv4 = strValue
                    "listen_port" -> listenPort = strValue.toIntOrNull()
                        ?: throw ConfigParseException(index + 1, "listen_port 不是合法整数: $rawValue")
                }
                "peer" -> if (key == "uri") startNodes += strValue
                "proxy_network" -> when (key) {
                    "cidr" -> pendingProxy = ProxyNetworkConfig(cidr = strValue)
                    "mapped_cidr" -> pendingProxy = pendingProxy?.copy(mappedCidr = strValue)
                    "allow" -> pendingProxy = pendingProxy?.copy(
                        allow = parseStringArray(rawValue, index)
                    )
                }
                // 顶层与未知小节：忽略（instance_name / flags 等）
                "" -> when (key) {
                    "hostname" -> hostname = strValue
                    "ipv4" -> ipv4 = strValue
                    "listeners" -> parseListeners(rawValue, listeners, index)
                }
            }
        }
        flushProxy()

        // 扁平 schema: 从 ipv4="addr/prefix" 拆分 ipv4 与 cidr
        if (ipv4 != null) {
            ipv4WithPrefixRegex.find(ipv4)?.let {
                ipv4 = it.groupValues[1]
                ipv4Prefix = it.groupValues[2].toInt()
            }
        }
        if (cidr == null) {
            cidr = networkAddressOf(ipv4, ipv4Prefix)?.let { "$it/$ipv4Prefix" }
        }
        if (listenPort == 11010 && listeners.isNotEmpty()) {
            urlRegex.find(listeners.first())?.let {
                listenPort = it.groupValues[1].toIntOrNull() ?: 11010
            }
        }

        val name = networkName?.takeIf { it.isNotBlank() }
            ?: throw ConfigParseException(0, "缺少 [network_identity] network_name")
        EasyTierConfig(
            networkName = name,
            networkSecret = networkSecret?.takeIf { it.isNotBlank() },
            hostname = hostname?.takeIf { it.isNotBlank() },
            startNodes = startNodes,
            ipv4 = ipv4?.takeIf { it.isNotBlank() },
            cidr = cidr?.takeIf { it.isNotBlank() },
            listenPort = listenPort,
            proxyNetworks = proxyNetworks,
        )
    }

    private fun parseListeners(rawValue: String, out: MutableList<String>, line: Int) {
        val inner = rawValue.trim().removePrefix("[").removeSuffix("]")
        val items = inner.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (items.isEmpty()) {
            throw ConfigParseException(line, "listeners 不是合法数组: $rawValue")
        }
        items.forEach { url ->
            val unquoted = stringValueRegex.find(url)?.groupValues?.get(1) ?: url.trim('"')
            out += unquoted
        }
    }

    private fun parseStringArray(rawValue: String, line: Int): List<String> {
        val inner = rawValue.trim().removePrefix("[").removeSuffix("]")
        return inner.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map {
            stringValueRegex.find(it)?.groupValues?.get(1) ?: it.trim('"')
        }.also {
            if (it.isEmpty()) throw ConfigParseException(line, "不是合法数组: $rawValue")
        }
    }

    private enum class Mode { TOP, PEER, IDENTITY, PROXY_SKIP, UNKNOWN }

    private class SectionBlock(val header: String, val body: MutableList<String>)

    private class OriginalParts {
        val topUnknown = mutableListOf<String>()
        val unknownSections = mutableListOf<SectionBlock>()
        val peerExtras = mutableListOf<List<String>>()
        val identityExtras = mutableListOf<String>()
    }

    /**
     * 把原始 TOML 拆成已知字段之外的保留内容：
     * - 顶层未知键（如 instance_name）-> [OriginalParts.topUnknown]
     * - 未知 section（如 [flags]）整段 -> [OriginalParts.unknownSections]
     * - [[peer]] 段内非 uri 行（如 peer_public_key）按顺序 -> [OriginalParts.peerExtras]
     * - [network_identity] 段内未知键 -> [OriginalParts.identityExtras]
     * - [[proxy_network]] 段属于已知字段，整体丢弃（由 encode/merge 重建）
     */
    private fun splitOriginal(text: String): OriginalParts {
        val parts = OriginalParts()
        var mode = Mode.TOP
        var unknownBlock: MutableList<String>? = null
        var peerBlock: MutableList<String>? = null
        var identityBlock: MutableList<String>? = null

        fun closeSection() {
            if (unknownBlock != null) {
                parts.unknownSections += SectionBlock(unknownBlock!!.first(), unknownBlock!!.drop(1).toMutableList())
                unknownBlock = null
            }
            if (peerBlock != null) {
                parts.peerExtras += peerBlock!!
                peerBlock = null
            }
            if (identityBlock != null) {
                parts.identityExtras += identityBlock!!
                identityBlock = null
            }
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            peerSectionRegex.find(line)?.let { m ->
                when (m.groupValues[1]) {
                    "peer" -> {
                        closeSection()
                        mode = Mode.PEER
                        peerBlock = mutableListOf()
                    }
                    "proxy_network" -> {
                        closeSection()
                        mode = Mode.PROXY_SKIP
                    }
                    else -> {
                        closeSection()
                        mode = Mode.UNKNOWN
                        unknownBlock = mutableListOf(line)
                    }
                }
                return@forEach
            }
            sectionRegex.find(line)?.let { m ->
                when (m.groupValues[1]) {
                    "network_identity" -> {
                        closeSection()
                        mode = Mode.IDENTITY
                        identityBlock = mutableListOf()
                    }
                    // 旧版分段结构：整段保留（core 会忽略未知 section）
                    else -> {
                        closeSection()
                        mode = Mode.UNKNOWN
                        unknownBlock = mutableListOf(line)
                    }
                }
                return@forEach
            }

            when (mode) {
                Mode.TOP -> {
                    val kv = keyValueRegex.find(line)
                    if (kv == null || kv.groupValues[1] !in knownTopLevelKeys) {
                        parts.topUnknown += line
                    }
                }
                Mode.PEER -> {
                    val kv = keyValueRegex.find(line)
                    if (kv == null || kv.groupValues[1] !in knownPeerKeys) {
                        peerBlock?.add(line)
                    }
                }
                Mode.IDENTITY -> {
                    val kv = keyValueRegex.find(line)
                    if (kv == null || kv.groupValues[1] !in knownIdentityKeys) {
                        identityBlock?.add(line)
                    }
                }
                Mode.PROXY_SKIP, Mode.UNKNOWN -> {
                    unknownBlock?.add(line)
                }
            }
        }
        closeSection()
        return parts
    }

    /** 将 ipv4 地址按前缀掩码后的网络地址，如 10.144.0.2/16 -> 10.144.0.0。 */
    private fun networkAddressOf(ipv4: String?, prefix: Int): String? {
        if (ipv4 == null || prefix !in 0..32) return null
        val octets = ipv4.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) return null
        val mask = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFFFFFFL
        val value = octets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
        val masked = value and mask
        return listOf(
            (masked shr 24) and 0xFF,
            (masked shr 16) and 0xFF,
            (masked shr 8) and 0xFF,
            masked and 0xFF,
        ).joinToString(".")
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    class ConfigParseException(
        val lineNumber: Int,
        message: String,
    ) : Exception(message)
}

fun ConfigParseException.toDisplayMessage(): String =
    if (lineNumber > 0) {
        "第 $lineNumber 行配置错误: ${message ?: "未知错误"}"
    } else {
        "配置错误: ${message ?: "未知错误"}"
    }