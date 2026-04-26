package com.taomic.agent.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.taomic.agent.AgentApp
import com.taomic.agent.service.AgentForegroundService

/**
 * V0.1 完整引导页：
 *
 *  Step 1  浮窗权限 (SYSTEM_ALERT_WINDOW)        [✓] 或 [去授权]
 *  Step 2  无障碍服务 (AccessibilityService)     [✓] 或 [去启用]
 *  Step 3  LLM 配置 (base_url / api_key / model) [✓] 或 [配置]   * 可跳过；V0.2 后才真用
 *
 *  全部就绪后底部显示主按钮：「启动助手」/「停止助手」（驱动 AgentForegroundService）
 *
 * 状态在 ON_RESUME 时刷新（用户从系统设置回来时立即重检）。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BootScreen()
                }
            }
        }
    }
}

@Composable
private fun BootScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as AgentApp
    val llmStore = remember { LlmConfigStore(context) }

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasA11y by remember { mutableStateOf(isA11yEnabled(context)) }
    var llmConfigured by remember { mutableStateOf(llmStore.isConfigured) }
    var serviceRunning by remember { mutableStateOf(app.isBubbleShown()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = Settings.canDrawOverlays(context)
                hasA11y = isA11yEnabled(context)
                llmConfigured = llmStore.isConfigured
                serviceRunning = app.isBubbleShown()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allRequiredReady = hasOverlay && hasA11y // LLM 在 V0.1 不强制（V0.2 接入时才必填）

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "AgentOS — V0.1",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "三步设置后即可呼出 AgentOS 助手；LLM 配置可在 V0.2 接入前先跳过。",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6B6B6B),
        )

        StepCard(
            index = "1",
            title = "浮窗权限",
            ready = hasOverlay,
            actionLabel = "去系统设置授权",
            description = "允许 AgentOS 在其他 App 上方显示浮窗。",
            onAction = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
        )

        StepCard(
            index = "2",
            title = "无障碍服务",
            ready = hasA11y,
            actionLabel = "去无障碍设置启用",
            description = "在「设置 → 无障碍 → 已下载的应用」里打开 AgentOS Accessibility，授权 AgentOS 代你点击其他 App。",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        )

        LlmCard(
            ready = llmConfigured,
            store = llmStore,
            onSaved = { llmConfigured = llmStore.isConfigured },
        )

        Spacer(Modifier.height(8.dp))

        if (allRequiredReady) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (serviceRunning) {
                        AgentForegroundService.stop(context)
                    } else {
                        AgentForegroundService.start(context)
                    }
                    serviceRunning = !serviceRunning
                },
            ) {
                Text(if (serviceRunning) "停止助手 (隐藏浮窗)" else "启动助手 (显示浮窗)")
            }
        } else {
            Text(
                "▲ 完成 Step 1 + 2 后才能启动助手。",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB02020),
            )
        }
    }
}

@Composable
private fun StepCard(
    index: String,
    title: String,
    ready: Boolean,
    actionLabel: String,
    description: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) Color(0xFFE8F4EA) else Color(0xFFF7F7F7),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (ready) "✓" else index,
                    color = if (ready) Color(0xFF1F8E3E) else Color(0xFF6750A4),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (ready) "已就绪" else "未就绪",
                    color = if (ready) Color(0xFF1F8E3E) else Color(0xFF8A8A8A),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = description,
                color = Color(0xFF555555),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!ready) {
                OutlinedButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun LlmCard(
    ready: Boolean,
    store: LlmConfigStore,
    onSaved: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(store.baseUrl) }
    var apiKey by remember { mutableStateOf(store.apiKey) }
    var model by remember { mutableStateOf(store.model) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) Color(0xFFE8F4EA) else Color(0xFFFFF8E1),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (ready) "✓" else "3",
                    color = if (ready) Color(0xFF1F8E3E) else Color(0xFF6750A4),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "LLM 配置",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        ready -> "已配置"
                        else -> "可跳过 (V0.2 才需要)"
                    },
                    color = if (ready) Color(0xFF1F8E3E) else Color(0xFFB07A00),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "出厂默认走火山引擎方舟的 Doubao 1.5 Pro。需要在方舟控制台开通后填入 api_key。",
                color = Color(0xFF555555),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起配置" else if (ready) "查看 / 修改配置" else "展开填写配置")
            }
            if (expanded) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("base_url") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("api_key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = {
                    store.baseUrl = baseUrl.trim()
                    store.apiKey = apiKey.trim()
                    store.model = model.trim()
                    onSaved()
                    expanded = false
                }) { Text("保存") }
            }
        }
    }
}

private fun isA11yEnabled(context: android.content.Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    return enabled.contains("com.taomic.agent/com.taomic.agent.a11y.AgentAccessibilityService")
}
