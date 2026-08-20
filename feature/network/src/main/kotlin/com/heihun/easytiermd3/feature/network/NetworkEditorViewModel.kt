package com.heihun.easytiermd3.feature.network

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heihun.easytiermd3.core.api.model.EasyTierConfig
import com.heihun.easytiermd3.core.api.model.ProxyNetworkConfig
import com.heihun.easytiermd3.domain.model.EasyTierConfigCodec
import com.heihun.easytiermd3.domain.model.toDisplayMessage
import com.heihun.easytiermd3.domain.usecase.NetworkConfigUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val networkConfigUseCases: NetworkConfigUseCases,
) : ViewModel() {

    private val networkId: String? = savedStateHandle.get<String>("networkId")

    data class EditorUiState(
        val isEdit: Boolean = false,
        val step: Int = 0,
        val saving: Boolean = false,
        val networkName: String = "",
        val hostname: String = "Android Phone",
        val useSecret: Boolean = false,
        val networkSecret: String = "",
        val startNodes: List<String> = listOf(""),
        val autoIpv4: Boolean = true,
        val ipv4: String = "",
        val listenPort: String = "11010",
        val proxyCidrs: List<ProxyCidrInput> = emptyList(),
        val errorMessage: String? = null,
        val advancedMode: Boolean = false,
        val rawToml: String = "",
        val validationError: String? = null,
    )

    data class ProxyCidrInput(
        val cidr: String = "",
        val mappedCidr: String = "",
        val allow: String = "",
    )

    private val _uiState = MutableStateFlow(EditorUiState(isEdit = networkId != null))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        if (networkId != null) {
            viewModelScope.launch {
                val network = networkConfigUseCases.getById(networkId) ?: return@launch
                val parsed = EasyTierConfigCodec.decode(network.configText).getOrNull()
                _uiState.update { state ->
                    state.copy(
                        networkName = parsed?.networkName ?: network.name,
                        hostname = parsed?.hostname ?: "Android Phone",
                        useSecret = !parsed?.networkSecret.isNullOrBlank(),
                        networkSecret = parsed?.networkSecret ?: "",
                        startNodes = parsed?.startNodes?.ifEmpty { listOf("") } ?: listOf(""),
                        autoIpv4 = parsed?.ipv4.isNullOrBlank(),
                        ipv4 = parsed?.ipv4?.let { ip ->
                            if (ip.contains('/')) {
                                ip
                            } else {
                                val prefix = parsed.cidr
                                    ?.substringAfter('/', "")
                                    ?.takeIf { it.isNotBlank() } ?: "24"
                                "$ip/$prefix"
                            }
                        } ?: "",
                        proxyCidrs = parsed?.proxyNetworks?.map {
                            ProxyCidrInput(
                                cidr = it.cidr,
                                mappedCidr = it.mappedCidr ?: "",
                                allow = it.allow.joinToString(", "),
                            )
                        } ?: emptyList(),
                        listenPort = parsed?.listenPort?.toString() ?: "11010",
                        rawToml = network.configText,
                    )
                }
            }
        }
    }

    fun setStep(step: Int) {
        _uiState.update { it.copy(step = step, errorMessage = null) }
    }

    fun nextStep() {
        if (validateStep(_uiState.value.step) == null) {
            _uiState.update { it.copy(step = it.step + 1, errorMessage = null) }
        }
    }

    fun previousStep() {
        _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    fun updateField(transform: (EditorUiState) -> EditorUiState) {
        _uiState.update(transform)
    }

    fun updateNode(index: Int, value: String) {
        _uiState.update { state ->
            val nodes = state.startNodes.toMutableList()
            if (index in nodes.indices) nodes[index] = value
            state.copy(startNodes = nodes)
        }
    }

    fun removeNode(index: Int) {
        _uiState.update { state ->
            if (state.startNodes.size <= 1) return@update state
            state.copy(startNodes = state.startNodes.filterIndexed { i, _ -> i != index })
        }
    }

    fun addNode() {
        _uiState.update { state -> state.copy(startNodes = state.startNodes + "") }
    }

    fun updateProxy(index: Int, transform: (ProxyCidrInput) -> ProxyCidrInput) {
        _uiState.update { state ->
            val items = state.proxyCidrs.toMutableList()
            if (index in items.indices) items[index] = transform(items[index])
            state.copy(proxyCidrs = items)
        }
    }

    fun removeProxy(index: Int) {
        _uiState.update { state ->
            if (state.proxyCidrs.size <= 1) return@update state
            state.copy(proxyCidrs = state.proxyCidrs.filterIndexed { i, _ -> i != index })
        }
    }

    fun addProxy() {
        _uiState.update { state -> state.copy(proxyCidrs = state.proxyCidrs + ProxyCidrInput()) }
    }

    fun toggleAdvanced() {
        _uiState.update { it.copy(advancedMode = !it.advancedMode, validationError = null) }
    }

    fun onRawTomlChange(text: String) {
        _uiState.update { it.copy(rawToml = text, validationError = null) }
    }

    fun restoreDefaultToml() {
        _uiState.update { it.copy(rawToml = EasyTierConfigCodec.encode(buildConfig(it))) }
    }

    fun save(onDone: () -> Unit) {
        val state = _uiState.value
        if (state.saving) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            try {
                if (state.advancedMode) {
                    val parsed = EasyTierConfigCodec.decode(state.rawToml).getOrThrow()
                    saveNetwork(parsed.networkName, EasyTierConfigCodec.encode(parsed))
                } else {
                    val error = validateSimple(state)
                    if (error != null) {
                        _uiState.update { it.copy(errorMessage = error, saving = false) }
                        return@launch
                    }
                    val config = buildConfig(state)
                    // merge 保留原 TOML 中 UI 不管理的高级字段（[flags] 等），
                    // 避免普通模式编辑丢失用户的高级配置。
                    saveNetwork(config.networkName, EasyTierConfigCodec.merge(state.rawToml, config))
                }
                onDone()
            } catch (e: Exception) {
                val message = (e as? EasyTierConfigCodec.ConfigParseException)
                    ?.toDisplayMessage()
                    ?: e.message
                    ?: "保存失败"
                _uiState.update { it.copy(validationError = message, saving = false) }
            }
        }
    }

    private fun validateStep(step: Int): String? = when (step) {
        0 -> if (_uiState.value.networkName.isBlank()) "请输入网络名称" else null
        3 -> validateSimple(_uiState.value)
        else -> null
    }

    private fun validateSimple(state: EditorUiState): String? {
        if (state.networkName.isBlank()) return "请输入网络名称"
        val port = state.listenPort.toIntOrNull()
        if (port == null || port !in 1..65535) return "监听端口无效"
        if (!state.autoIpv4) {
            val input = state.ipv4.trim()
            if (input.isBlank()) return "请输入虚拟 IP（如 10.144.0.2/24）"
            val addr = input.substringBefore('/')
            val prefix = input.substringAfter('/', "").ifBlank { "24" }
            if (!IPV4_PATTERN.matches(addr)) return "虚拟 IP 格式无效"
            val p = prefix.toIntOrNull()
            if (p == null || p !in 0..32) return "掩码前缀无效（0-32）"
        }
        validateProxies(state)?.let { return it }
        return null
    }

    /** 校验代理网段：格式、重复、与自身虚拟 IP 网段冲突。 */
    private fun validateProxies(state: EditorUiState): String? {
        val nonEmpty = state.proxyCidrs.filter { it.cidr.isNotBlank() }
        if (nonEmpty.isEmpty()) return null
        val seen = mutableSetOf<String>()
        for ((index, item) in nonEmpty.withIndex()) {
            val label = "第 ${index + 1} 条路由"
            val err = validateCidr(item.cidr.trim(), "CIDR")
            if (err != null) return "$label：$err"
            val cidr = item.cidr.trim()
            if (!seen.add(cidr)) return "$label：CIDR $cidr 重复"
            item.mappedCidr.trim().takeIf { it.isNotBlank() }?.let { mapped ->
                validateCidr(mapped, "映射 CIDR")?.let { return "$label：$it" }
                if (!seen.add(mapped)) return "$label：映射 CIDR $mapped 与已有网段重复"
            }
            if (!state.autoIpv4) {
                val selfInput = state.ipv4.trim()
                val selfAddr = selfInput.substringBefore('/')
                val selfPrefix = selfInput.substringAfter('/', "").ifBlank { "24" }
                if (networkEquals(selfAddr, selfPrefix, cidr)) {
                    return "$label：CIDR $cidr 与自身虚拟 IP 网段 $selfAddr/$selfPrefix 冲突"
                }
            }
        }
        return null
    }

    private fun validateCidr(value: String, label: String): String? {
        val addr = value.substringBefore('/')
        val prefixStr = value.substringAfter('/', "")
        if (addr.isBlank() || !value.contains('/')) return "$label 格式无效（应为 IP/前缀，如 192.168.1.0/24）"
        val ip = java.net.InetAddress.getByName(addr)
        val maxPrefix = if (ip is java.net.Inet6Address) 128 else 32
        val prefix = prefixStr.toIntOrNull()
        if (prefix == null || prefix !in 0..maxPrefix) return "$label 前缀无效（0-$maxPrefix）"
        return null
    }

    /** 判断 cidr 网段与 ip/prefix 网段是否相同（IPv4/IPv6 通用，基于字节掩码）。 */
    private fun networkEquals(ip: String, prefix: String, cidr: String): Boolean {
        return try {
            val aBytes = java.net.InetAddress.getByName(ip).address
            val cBytes = java.net.InetAddress.getByName(cidr.substringBefore('/')).address
            if (aBytes.size != cBytes.size) return false
            val p = prefix.toInt().coerceIn(0, aBytes.size * 8)
            val aNet = maskBytes(aBytes, p)
            val cNet = maskBytes(cBytes, p)
            aNet.contentEquals(cNet)
        } catch (_: Exception) {
            false
        }
    }

    private fun maskBytes(bytes: ByteArray, prefix: Int): ByteArray {
        return ByteArray(bytes.size) { i ->
            val bitPos = i * 8
            val mask = when {
                prefix <= bitPos -> 0
                prefix >= bitPos + 8 -> 0xFF
                else -> (0xFF shl (8 - (prefix - bitPos))) and 0xFF
            }
            (bytes[i].toInt() and mask).toByte()
        }
    }

    private suspend fun saveNetwork(name: String, configText: String) {
        val now = System.currentTimeMillis()
        val existing = networkId?.let { networkConfigUseCases.getById(it) }
        if (existing != null) {
            networkConfigUseCases.save(
                existing.copy(name = name, configText = configText, updatedAt = now)
            )
        } else {
            val draft = networkConfigUseCases.createDraft()
            networkConfigUseCases.save(
                draft.copy(
                    name = name,
                    configText = configText,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    private fun buildConfig(state: EditorUiState): EasyTierConfig {
        return EasyTierConfig(
            networkName = state.networkName.trim(),
            networkSecret = if (state.useSecret) {
                state.networkSecret.takeIf { it.isNotBlank() }
            } else {
                null
            },
            hostname = state.hostname.trim().ifBlank { null },
            startNodes = state.startNodes.filter { it.isNotBlank() },
            ipv4 = if (!state.autoIpv4) {
                state.ipv4.trim().substringBefore('/').ifBlank { null }
            } else {
                null
            },
            cidr = if (!state.autoIpv4) {
                val input = state.ipv4.trim()
                val prefix = input.substringAfter('/', "").ifBlank { "24" }
                if (input.isBlank()) null else "10.0.0.0/$prefix"
            } else {
                null
            },
            listenPort = state.listenPort.toIntOrNull() ?: 11010,
            proxyNetworks = state.proxyCidrs
                .mapIndexed { index, item -> index to item }
                .filter { it.second.cidr.isNotBlank() }
                .map { (_, item) ->
                    ProxyNetworkConfig(
                        cidr = item.cidr.trim(),
                        mappedCidr = item.mappedCidr.trim().takeIf { it.isNotBlank() },
                        allow = item.allow.split(",")
                            .map { it.trim() }
                            .filter { it.isNotBlank() },
                    )
                },
        )
    }

    companion object {
        private val IPV4_PATTERN = Regex(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
        )
    }
}