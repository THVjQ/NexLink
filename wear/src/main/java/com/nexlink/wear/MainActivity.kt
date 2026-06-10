package com.nexlink.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.nexlink.shared.Conversation
import com.nexlink.wear.data.WearDataRepository
import com.nexlink.wear.data.WearMessageClient
import com.nexlink.wear.ui.conversations.ConversationsScreen
import com.nexlink.wear.ui.reply.ReplyScreen
import com.nexlink.wear.ui.thread.ThreadScreen
import com.nexlink.wear.ui.thread.ThreadViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ask phone for fresh conversations on app open
        WearMessageClient.requestConversations(applicationContext)

        setContent { NexLinkWearApp() }
    }
}

@Composable
fun NexLinkWearApp() {
    val navController   = rememberSwipeDismissableNavController()
    val conversations   by WearDataRepository.conversations.collectAsState()
    val cannedReplies   by WearDataRepository.cannedReplies.collectAsState()
    val threadViewModel: ThreadViewModel = viewModel()

    var current by remember { mutableStateOf<Conversation?>(null) }

    SwipeDismissableNavHost(
        navController  = navController,
        startDestination = "conversations"
    ) {
        composable("conversations") {
            ConversationsScreen(
                conversations = conversations,
                onConversationClick = { conv ->
                    current = conv
                    navController.navigate("thread")
                }
            )
        }

        composable("thread") {
            val conv = current ?: return@composable
            ThreadScreen(
                threadId    = conv.threadId,
                address     = conv.address,
                contactName = conv.contactName,
                viewModel   = threadViewModel,
                onReply     = { navController.navigate("reply") }
            )
        }

        composable("reply") {
            val conv = current ?: return@composable
            ReplyScreen(
                address      = conv.address,
                cannedReplies = cannedReplies,
                onSent       = { navController.popBackStack("conversations", inclusive = false) }
            )
        }
    }
}
