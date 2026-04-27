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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * V0.3 浮窗内容：圆按钮（collapsed）↔ 展开卡片（expanded）。
 *
 * - collapsed：56dp Cat 圆按钮；轻触展开；拖拽移动浮窗
 * - expanded：圆角卡片 + 输入框 + 发送按钮 + 快捷 chip + 关闭按钮
 *     输入框 + 发送 → onIntent(text)
 *     chip "打开网络" → onIntent("网络")
 *     chip "看三体"   → onIntent("三体")
 *     麦克风按钮     → onMic（V0.3 SpeechRecognizer）
 *     关闭按钮       → 退回 collapsed
 *
 * IME 适配：展开/收起输入框时通过 onFocusableChanged 切换 WindowManager flags。
 */
@Composable
fun BubbleContent(
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onIntent: (text: String) -> Unit,
    onFocusableChanged: (focusable: Boolean) -> Unit = {},
    onMic: () -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    onSaveRecordedSkill: (id: String, name: String, description: String) -> Unit = { _, _, _ -> },
    onDiscardRecording: () -> Unit = {},
    state: AgentState = AgentState.IDLE,
    recordedSteps: List<String> = emptyList(),
) {
    var expanded by remember { mutableStateOf(false) }

    if (!expanded) {
        onFocusableChanged(false)
        CollapsedBubble(
            onDragDelta = onDragDelta,
            onDragEnd = onDragEnd,
            onClick = { expanded = true },
            state = state,
        )
    } else {
        ExpandedCard(
            onClose = { expanded = false },
            onIntent = { text ->
                onIntent(text)
                expanded = false
            },
            onFocusableChanged = onFocusableChanged,
            onMic = onMic,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onCancelRecording = onCancelRecording,
            onSaveRecordedSkill = onSaveRecordedSkill,
            onDiscardRecording = onDiscardRecording,
            state = state,
            recordedSteps = recordedSteps,
        )
    }
}

@Composable
private fun CollapsedBubble(
    onDragDelta: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    state: AgentState,
) {
    val bgColor = when (state) {
        AgentState.THINKING, AgentState.EXECUTING -> Color(0xFFE8DEF8)
        AgentState.RECORDING -> Color(0xFFFFCDD2)
        AgentState.ERROR -> Color(0xFFFFCDD2)
        else -> AccentPurple
    }
    val label = when (state) {
        AgentState.THINKING -> "..."
        AgentState.EXECUTING -> "▶"
        AgentState.RECORDING -> "●"
        AgentState.ERROR -> "!"
        AgentState.DONE -> "✓"
        else -> "Cat"
    }

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
        color = bgColor,
        shadowElevation = 6.dp,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
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
    onFocusableChanged: (Boolean) -> Unit,
    onMic: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSaveRecordedSkill: (id: String, name: String, description: String) -> Unit,
    onDiscardRecording: () -> Unit,
    state: AgentState,
    recordedSteps: List<String>,
) {
    var inputText by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier
            .widthIn(min = 280.dp, max = 340.dp)
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

            // 状态提示
            if (state != AgentState.IDLE) {
                val statusText = when (state) {
                    AgentState.THINKING -> "正在思考..."
                    AgentState.EXECUTING -> "正在执行..."
                    AgentState.RECORDING -> "录制中..."
                    AgentState.DONE -> "完成"
                    AgentState.ERROR -> "出错了，请重试"
                    else -> ""
                }
                val statusColor = when (state) {
                    AgentState.THINKING, AgentState.EXECUTING -> Color(0xFF6750A4)
                    AgentState.RECORDING -> Color(0xFFB02020)
                    AgentState.DONE -> Color(0xFF1F8E3E)
                    AgentState.ERROR -> Color(0xFFB02020)
                    else -> Color.Gray
                }
                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                )
            }

            // 输入框 + 发送 + 麦克风
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("说点什么...", fontSize = 13.sp, color = Color(0xFFAAAAAA)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentPurple,
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                        cursorColor = AccentPurple,
                    ),
                )
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onIntent(inputText.trim())
                            inputText = ""
                        }
                    },
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                ) {
                    Text("发送", fontSize = 12.sp, color = Color.White)
                }
                // 麦克风按钮（V0.3 SpeechRecognizer 占位）
                Button(
                    onClick = onMic,
                    modifier = Modifier.size(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8DEF8)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text("🎤", fontSize = 16.sp)
                }
            }

            // 快捷 chip + 录制控制
            if (state == AgentState.RECORDING) {
                // 录制中：停止 / 取消
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onStopRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F8E3E)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    ) {
                        Text("停止录制", fontSize = 12.sp, color = Color.White)
                    }
                    TextButton(onClick = onCancelRecording) {
                        Text("取消", color = Color(0xFFB02020), fontSize = 12.sp)
                    }
                }
            } else {
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
                    AssistChip(
                        onClick = onStartRecording,
                        label = { Text("录制") },
                        colors = AssistChipDefaults.assistChipColors(labelColor = Color(0xFFB02020)),
                    )
                }
            }

            // 录制审阅：有录制结果时显示
            if (recordedSteps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "录制完成 — ${recordedSteps.size} 步",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFF1F8E3E),
                    )
                    recordedSteps.forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. $step",
                            fontSize = 11.sp,
                            color = Color(0xFF444444),
                            maxLines = 2,
                        )
                    }
                    var skillId by remember { mutableStateOf("") }
                    var skillName by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = skillId,
                        onValueChange = { skillId = it },
                        placeholder = { Text("Skill ID", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = skillName,
                        onValueChange = { skillName = it },
                        placeholder = { Text("Skill 名称", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (skillId.isNotBlank() && skillName.isNotBlank()) {
                                    onSaveRecordedSkill(skillId.trim(), skillName.trim(), "")
                                }
                            },
                            enabled = skillId.isNotBlank() && skillName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        ) {
                            Text("保存", fontSize = 12.sp, color = Color.White)
                        }
                        TextButton(onClick = onDiscardRecording) {
                            Text("丢弃", color = Color(0xFFB02020), fontSize = 12.sp)
                        }
                    }
                }
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

/** Agent 执行状态，供浮窗 UI 消费。 */
enum class AgentState {
    IDLE,           // 空闲，等待输入
    THINKING,       // LLM 思考中
    EXECUTING,      // Skill 执行中
    RECORDING,      // 录制模式
    DONE,           // 完成
    ERROR,          // 出错
}

private val AccentPurple = Color(0xFF6750A4)
