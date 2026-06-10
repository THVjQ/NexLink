package com.nexlink.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.nexlink.wear.MainActivity
import com.nexlink.wear.data.WearDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

class NexLinkTile : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onTileRequest(params: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        scope.future {
            WearDataRepository.loadExisting(applicationContext)
            val unread     = WearDataRepository.unreadCount.value
            val lastSender = WearDataRepository.conversations.value.firstOrNull()?.contactName ?: ""

            val launchAction = ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setClassName(MainActivity::class.java.name)
                        .setPackageName(packageName)
                        .build()
                )
                .build()

            val clickable = ModifiersBuilders.Clickable.Builder()
                .setOnClick(launchAction)
                .build()

            val headerText = if (unread > 0) "$unread unread" else "NexLink"
            val bodyText   = when {
                unread > 0 && lastSender.isNotBlank() -> "From $lastSender"
                unread > 0                            -> "New messages"
                else                                  -> "No new messages"
            }

            val layout = LayoutElementBuilders.Layout.Builder()
                .setRoot(
                    LayoutElementBuilders.Box.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.expand())
                        .setModifiers(
                            ModifiersBuilders.Modifiers.Builder()
                                .setClickable(clickable)
                                .build()
                        )
                        .addContent(
                            LayoutElementBuilders.Column.Builder()
                                .setWidth(DimensionBuilders.wrap())
                                .setHeight(DimensionBuilders.wrap())
                                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                                .addContent(
                                    LayoutElementBuilders.Text.Builder()
                                        .setText(headerText)
                                        .setFontStyle(
                                            LayoutElementBuilders.FontStyle.Builder()
                                                .setSize(DimensionBuilders.SpProp.Builder().setValue(18f).build())
                                                .build()
                                        )
                                        .build()
                                )
                                .addContent(
                                    LayoutElementBuilders.Text.Builder()
                                        .setText(bodyText)
                                        .setFontStyle(
                                            LayoutElementBuilders.FontStyle.Builder()
                                                .setSize(DimensionBuilders.SpProp.Builder().setValue(13f).build())
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()

            TileBuilders.Tile.Builder()
                .setResourcesVersion("0")
                .setTileTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(layout)
                                .build()
                        )
                        .build()
                )
                .setFreshnessIntervalMillis(5 * 60 * 1000L)
                .build()
        }

    override fun onResourcesRequest(params: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        scope.future {
            ResourceBuilders.Resources.Builder().setVersion("0").build()
        }
}
