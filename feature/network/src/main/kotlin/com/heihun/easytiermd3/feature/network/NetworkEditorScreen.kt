package com.heihun.easytiermd3.feature.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkEditorScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: NetworkEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun saveAndExit() {
        viewModel.save {
            if (uiState.isEdit) {
                android.widget.Toast.makeText(
                    context,
                    "配置已更新，重启连接以生效",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = if (uiState.isEdit) "编辑网络" else "创建网络") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::toggleAdvanced) {
                        Text(text = if (uiState.advancedMode) "简单模式" else "高级模式")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            if (!uiState.advancedMode) {
                StepIndicator(currentStep = uiState.step)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (uiState.advancedMode) {
                    AdvancedEditor(
                        rawToml = uiState.rawToml,
                        validationError = uiState.validationError,
                        onRawTomlChange = viewModel::onRawTomlChange,
                        onRestoreDefault = viewModel::restoreDefaultToml,
                    )
                } else {
                    when (uiState.step) {
                        0 -> BasicInfoStep(uiState, viewModel)
                        1 -> SecretStep(uiState, viewModel)
                        2 -> StartNodesStep(uiState, viewModel)
                        3 -> VirtualNetworkStep(uiState, viewModel)
                        4 -> RoutingStep(uiState, viewModel)
                    }
                }

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.advancedMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = viewModel::restoreDefaultToml) {
                        Text(text = "恢复默认")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    SaveButton(
                        saving = uiState.saving,
                        onClick = { saveAndExit() },
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.step > 0) {
                        TextButton(onClick = viewModel::previousStep) {
                            Text(text = "上一步")
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (uiState.step < 4) {
                        Button(onClick = viewModel::nextStep) {
                            Text(text = "下一步")
                        }
                    } else {
                        SaveButton(
                            saving = uiState.saving,
onClick = { saveAndExit() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (step in 0..4) {
            LinearProgressIndicator(
                progress = { if (step <= currentStep) 1f else 0f },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
            )
        }
    }
}

@Composable
private fun SaveButton(saving: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !saving) {
        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "保存中...")
        } else {
            Text(text = "保存")
        }
    }
}

@Composable
private fun BasicInfoStep(
    uiState: NetworkEditorViewModel.EditorUiState,
    viewModel: NetworkEditorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "基本信息",
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = uiState.networkName,
            onValueChange = { value ->
                viewModel.updateField { it.copy(networkName = value) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("网络名称") },
            singleLine = true,
        )
        OutlinedTextField(
            value = uiState.hostname,
            onValueChange = { value ->
                viewModel.updateField { it.copy(hostname = value) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("节点名称") },
            supportingText = { Text("此设备在网络中显示的名称") },
            singleLine = true,
        )
    }
}

@Composable
private fun SecretStep(
    uiState: NetworkEditorViewModel.EditorUiState,
    viewModel: NetworkEditorViewModel,
) {
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "网络密码",
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "设置网络密码", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "关闭后任何人都可以加入此网络",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = uiState.useSecret,
                onCheckedChange = { checked ->
                    viewModel.updateField { it.copy(useSecret = checked) }
                },
            )
        }
        if (uiState.useSecret) {
            OutlinedTextField(
                value = uiState.networkSecret,
                onValueChange = { value ->
                    viewModel.updateField { it.copy(networkSecret = value) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (visible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (visible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun StartNodesStep(
    uiState: NetworkEditorViewModel.EditorUiState,
    viewModel: NetworkEditorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "启动节点",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "通过已存在的节点加入网络，例如 tcp://example.com:11010",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        uiState.startNodes.forEachIndexed { index, node ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = node,
                    onValueChange = { value -> viewModel.updateNode(index, value) },
                    modifier = Modifier.weight(1f),
                    label = { Text("节点 ${index + 1}") },
                    placeholder = { Text("tcp://1.2.3.4:11010") },
                    singleLine = true,
                )
                IconButton(onClick = { viewModel.removeNode(index) }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除节点",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        TextButton(onClick = viewModel::addNode) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "添加节点")
        }
    }
}

@Composable
private fun VirtualNetworkStep(
    uiState: NetworkEditorViewModel.EditorUiState,
    viewModel: NetworkEditorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "虚拟网络配置",
            style = MaterialTheme.typography.titleLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "启用 DHCP", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "自动分配虚拟 IP；关闭后需手动填写",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = uiState.autoIpv4,
                onCheckedChange = { checked ->
                    viewModel.updateField { it.copy(autoIpv4 = checked) }
                },
            )
        }
        if (!uiState.autoIpv4) {
            OutlinedTextField(
                value = uiState.ipv4,
                onValueChange = { value ->
                    viewModel.updateField { it.copy(ipv4 = value) }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("虚拟 IP") },
                placeholder = { Text("10.144.0.2/24") },
                supportingText = { Text("格式：IP/前缀，如 10.144.0.2/24") },
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = uiState.listenPort,
            onValueChange = { value ->
                viewModel.updateField { it.copy(listenPort = value) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("监听端口") },
            singleLine = true,
        )
    }
}

@Composable
private fun RoutingStep(
    uiState: NetworkEditorViewModel.EditorUiState,
    viewModel: NetworkEditorViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "路由与代理",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "发布本机可达的网段（Proxy CIDR），网络内其他节点可直接访问这些网段。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.proxyCidrs.isEmpty()) {
            Text(
                text = "未配置代理网段",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        uiState.proxyCidrs.forEachIndexed { index, item ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "路由 ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeProxy(index) }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "删除路由",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                OutlinedTextField(
                    value = item.cidr,
                    onValueChange = { value ->
                        viewModel.updateProxy(index) { it.copy(cidr = value) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("CIDR") },
                    placeholder = { Text("192.168.1.0/24") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = item.mappedCidr,
                    onValueChange = { value ->
                        viewModel.updateProxy(index) { it.copy(mappedCidr = value) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("映射 CIDR（可选）") },
                    placeholder = { Text("10.233.0.0/24") },
                    supportingText = { Text("向网络广播的映射网段") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = item.allow,
                    onValueChange = { value ->
                        viewModel.updateProxy(index) { it.copy(allow = value) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("放行主机（可选，逗号分隔）") },
                    placeholder = { Text("host1, host2") },
                    singleLine = true,
                )
            }
        }
        TextButton(onClick = viewModel::addProxy) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "添加路由")
        }
    }
}

@Composable
private fun AdvancedEditor(
    rawToml: String,
    validationError: String?,
    onRawTomlChange: (String) -> Unit,
    onRestoreDefault: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "高级配置",
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = rawToml,
            onValueChange = onRawTomlChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            placeholder = {
                Text(text = "[network]\nnetwork_name = \"My Network\"\n\n[instance]\nhostname = \"Android\"")
            },
        )
        if (validationError != null) {
            Text(
                text = validationError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = "保存前会进行配置校验，格式错误将无法保存。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}