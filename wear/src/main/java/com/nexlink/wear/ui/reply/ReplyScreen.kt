package com.nexlink.wear.ui.reply

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.nexlink.wear.data.WearMessageClient

@Composable
fun ReplyScreen(
    address: String,
    cannedReplies: List<String>,
    onSent: () -> Unit
) {
    val ctx       = LocalContext.current
    val listState = rememberScalingLazyListState()

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrBlank()) {
                WearMessageClient.sendSms(ctx, address, text)
                onSent()
            }
        }
    }

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
                ListHeader { Text("Reply", fontSize = 13.sp) }
            }

            // Voice input button
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your reply")
                        }
                        voiceLauncher.launch(intent)
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    label = { Text("🎤  Voice reply", fontSize = 13.sp) }
                )
            }

            // Canned replies
            items(cannedReplies) { reply ->
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        WearMessageClient.sendSms(ctx, address, reply)
                        onSent()
                    },
                    label = {
                        Text(
                            text = reply,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp
                        )
                    }
                )
            }
        }
    }
}
