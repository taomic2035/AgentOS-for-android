package com.taomic.agent.ui

import android.os.Bundle
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.taomic.agent.AgentApp
import com.taomic.agent.llm.OpenAiCompatClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 独立设置页：LLM 配置 + 连接测试。
 *
 * V0.3 从引导页拆出，引导完成后主入口跳此页。
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as AgentApp
    val llmStore = remember { app.llmConfigStore() }

    var baseUrl by remember { mutableStateOf(llmStore.baseUrl) }
    var apiKey by remember { mutableStateOf(llmStore.apiKey) }
    var model by remember { mutableStateOf(llmStore.model) }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var pinging by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "AgentOS 设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        // LLM 配置卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("LLM 配置", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "默认使用火山引擎方舟 Doubao 1.5 Pro。可在方舟控制台开通后填入 api_key。",
                    color = Color(0xFF555555),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it; saved = false },
                    label = { Text("base_url") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    label = { Text("api_key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; saved = false },
                    label = { Text("model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        llmStore.baseUrl = baseUrl.trim()
                        llmStore.apiKey = apiKey.trim()
                        llmStore.model = model.trim()
                        app.rebuildRouter()
                        saved = true
                    }) { Text("保存") }
                    Spacer(Modifier.width(12.dp))
                    if (saved) {
                        Text("已保存", color = Color(0xFF1F8E3E), style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            pinging = true
                            pingResult = null
                            CoroutineScope(Dispatchers.Main).launch {
                                val result = try {
                                    val client = OpenAiCompatClient(
                                        baseUrl = baseUrl.trim(),
                                        apiKey = apiKey.trim(),
                                        model = model.trim(),
                                    )
                                    if (client.ping()) "连接成功" else "连接失败"
                                } catch (e: Exception) {
                                    "错误: ${e.message?.take(60)}"
                                }
                                pingResult = result
                                pinging = false
                            }
                        },
                        enabled = !pinging,
                    ) { Text(if (pinging) "测试中..." else "测试连接") }
                    if (pingResult != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            pingResult!!,
                            color = if (pingResult == "连接成功") Color(0xFF1F8E3E) else Color(0xFFB02020),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        // 关于
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F7)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("关于", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("AgentOS V0.3 — Android 智能助手", style = MaterialTheme.typography.bodySmall, color = Color(0xFF555555))
                Text("常驻系统、可随时呼出，理解你说的话，动手帮你做事。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF555555))
            }
        }
    }
}
