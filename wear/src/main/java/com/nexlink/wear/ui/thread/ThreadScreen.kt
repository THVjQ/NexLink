package com.nexlink.wear.ui.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.nexlink.shared.SmsMessage
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ThreadScreen(
    threadId: Long,
    address: String,
    contactName: String,
    viewModel: ThreadViewModel,
    onReply: () -> Unit
) {
    val ctx       = LocalContext.current
    val messages  by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(threadId) {
        viewModel.loadThread(threadId, address)
        viewModel.markRead(ctx, threadId)
    }

    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = maxOf(0, messages.size - 1)
    )

    Scaffold(
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                ListHeader {
                    Text(contactName.ifBlank { address }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (isLoading && messages.isEmpty()) {
                item {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            items(messages) { msg ->
                MessageBubble(msg = msg)
            }

            item {
                Spacer(Modifier.height(4.dp))
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    onClick = onReply
                ) {
                    Text("Reply", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: SmsMessage) {
    val isIncoming = msg.isIncoming
    val bubbleColor = if (isIncoming) Color(0xFF1E2A3A) else Color(0xFF1565C0)
    val textColor   = Color.White

    val text = when {
        msg.isVoice                          -> "🎤 Voice message"
        msg.isMms && msg.mediaUri != null    -> if (msg.mimeType?.startsWith("image/") == true) "🖼 Image" else "📎 Media"
        else                                 -> msg.body
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isIncoming) Arrangement.Start else Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 140.dp)
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Column {
                val senderName = msg.senderName
                if (isIncoming && senderName != null) {
                    Text(
                        text  = senderName,
                        color = Color(0xFF90CAF9),
                        fontSize = 9.sp
                    )
                }
                Text(text = text, color = textColor, fontSize = 11.sp)
                Text(
                    text  = formatTime(msg.timestamp),
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
