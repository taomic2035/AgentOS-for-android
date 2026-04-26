package com.taomic.agent.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.taomic.agent.AgentApp

/**
 * V0.1 引导壳：
 *  - 显示当前 OVERLAY 权限状态
 *  - "申请浮窗权限" 跳到系统设置；ON_RESUME 时重检
 *  - "显示浮窗 / 隐藏浮窗" 切换浮窗
 *
 * T-003 会替换为完整引导（含 A11y 启用引导 + LLM key 配置）。
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

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var bubbleShown by remember { mutableStateOf(app.isBubbleShown()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlay = Settings.canDrawOverlays(context)
                bubbleShown = app.isBubbleShown()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier.padding(24.dp).fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("AgentOS — V0.1", style = MaterialTheme.typography.headlineSmall)
        Text("浮窗权限：${if (hasOverlay) "已授予" else "未授予"}")

        Spacer(Modifier.height(4.dp))

        if (!hasOverlay) {
            Button(onClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
                context.startActivity(intent)
            }) { Text("申请浮窗权限") }
        } else {
            Button(onClick = {
                if (bubbleShown) app.hideBubble() else app.showBubble()
                bubbleShown = app.isBubbleShown()
            }) { Text(if (bubbleShown) "隐藏浮窗" else "显示浮窗") }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "提示：还需在系统设置中启用 AgentOS 的无障碍服务，浮窗触发的 skill 才能真正操作其他 App。",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "V0.1a：点击浮窗触发硬编码 settings_open_internet。V0.1b 将加输入卡片 + 关键词路由。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
