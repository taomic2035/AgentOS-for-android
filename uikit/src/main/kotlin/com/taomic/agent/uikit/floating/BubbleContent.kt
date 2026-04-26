package com.taomic.agent.uikit.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * V0.1b 浮窗内容：圆按钮（collapsed）↔ 展开卡片（expanded）。
 *
 * - collapsed：56dp Cat 圆按钮；轻触展开；拖拽移动浮窗
 * - expanded：圆角卡片 + 两个快捷 chip + 关闭按钮
 *     chip "打开网络" → onIntent("网络")
 *     chip "看三体"   → onIntent("三体")
 *     关闭按钮       → 退回 collapsed
 *
 * V0.1b 仅 chip。文字输入框 + IME 推到 V0.3 与 ASR 一起做（IME 在
 * TYPE_APPLICATION_OVERLAY 上需要切换 NOT_FOCUSABLE flag，是独立工作量）。
 *
 * 拖拽事件仅 collapsed 态生效；expanded 态不响应拖拽（避免误触）。
 */
@Composable
fun BubbleContent(
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onIntent: (text: String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    if (!expanded) {
        CollapsedBubble(
            onDragDelta = onDragDelta,
            onDragEnd = onDragEnd,
            onClick = { expanded = true },
        )
    } else {
        ExpandedCard(
            onClose = { expanded = false },
            onIntent = { text ->
                onIntent(text)
                expanded = false
            },
        )
    }
}

@Composable
private fun CollapsedBubble(
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        onDragDelta(drag.x, drag.y)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        color = AccentPurple,
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cat",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ExpandedCard(
    onClose: () -> Unit,
    onIntent: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 320.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AccentPurple),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "C",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "AgentOS",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color(0xFF1F1F1F),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "想干嘛？",
                    fontSize = 12.sp,
                    color = Color(0xFF8A8A8A),
                )
            }

            // 快捷 chip
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onIntent("网络") },
                    label = { Text("打开网络") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = AccentPurple),
                )
                AssistChip(
                    onClick = { onIntent("三体") },
                    label = { Text("看三体") },
                    colors = AssistChipDefaults.assistChipColors(labelColor = AccentPurple),
                )
            }

            // 底部关闭
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClose) {
                    Text("关闭", color = Color(0xFF6B6B6B))
                }
            }
        }
    }
}

private val AccentPurple = Color(0xFF6750A4)
