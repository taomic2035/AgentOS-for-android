package com.taomic.agent.uikit.floating

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * V0.1a 浮窗内容：56dp 圆形按钮。
 *
 * 手势处理走 Compose pointerInput，**不**用 View 层 setOnTouchListener
 * （ComposeView 会消费 touch 在 Compose runtime 内部，不向外冒泡）。
 * detectDragGestures 在 touch slop 内的轻触会让位给 detectTapGestures —
 * 由 Compose 内部协调，恰好分得开 click 与 drag。
 */
@Composable
fun BubbleContent(
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
        color = Color(0xFF6750A4),
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
