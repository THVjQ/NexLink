package com.nexlink.wear.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.nexlink.shared.AvatarColors
import com.nexlink.shared.Conversation
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationsScreen(
    conversations: List<Conversation>,
    onConversationClick: (Conversation) -> Unit
) {
    val listState = rememberScalingLazyListState()

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
                ListHeader { Text("Messages", fontSize = 13.sp) }
            }

            if (conversations.isEmpty()) {
                item {
                    Text(
                        text = "No messages",
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(conversations) { conv ->
                    ConversationChip(conv = conv, onClick = { onConversationClick(conv) })
                }
            }
        }
    }
}

@Composable
private fun ConversationChip(conv: Conversation, onClick: () -> Unit) {
    val initials = conv.contactName.split(" ").take(2)
        .joinToString("") { it.take(1).uppercase() }
        .ifBlank { conv.address.take(2).uppercase() }

    val avatarColor = Color(AvatarColors.colorFor(initials))

    Chip(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = ChipDefaults.chipColors(
            backgroundColor = MaterialTheme.colors.surface
        ),
        label = {
            Text(
                text = conv.contactName.ifBlank { conv.address },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        },
        secondaryLabel = {
            Text(
                text = conv.lastMessage,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
        },
        icon = {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(avatarColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials.take(2),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (conv.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    )
                }
            }
        }
    )
}

private fun formatTimestamp(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000          -> "now"
        diff < 3_600_000       -> "${diff / 60_000}m"
        diff < 86_400_000      -> "${diff / 3_600_000}h"
        diff < 7 * 86_400_000L -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
        else                   -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
    }
}
