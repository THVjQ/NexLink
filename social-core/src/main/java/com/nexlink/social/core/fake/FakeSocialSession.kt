package com.nexlink.social.core.fake

import com.nexlink.social.core.session.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * An in-memory [SocialSession] — §11.6, §34.3.
 *
 * This exists so `:social-ui` can be built and tested before §11's SDK decision
 * is made in phase 2 (§33.3), and so the UI stays testable afterwards with no
 * network and no homeserver.
 *
 * It is a **test and development double**, shipped in `main` rather than in a
 * test source set because the phase-0 `:social` build runs against it (§33.1).
 * When the real implementation lands, [FakeSocialSession] stays for tests and
 * `:social` stops selecting it — see `SessionProvider` in `:social`.
 */
class FakeSocialSession(
    initialState: SessionState = SessionState.SignedOut
) : SocialSession {

    private val _state = MutableStateFlow(initialState)
    override val state: Flow<SessionState> = _state.asStateFlow()

    private val _rooms = MutableStateFlow<List<RoomSummary>>(emptyList())

    private val timelines = mutableMapOf<String, FakeTimeline>()

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())

    override fun rooms(): Flow<List<RoomSummary>> = _rooms.asStateFlow()

    override suspend fun timeline(roomId: RoomId): Timeline =
        timelines.getOrPut(roomId.value) { FakeTimeline() }

    override suspend fun send(roomId: RoomId, body: MessageBody): Result<EventId> {
        val id = EventId("\$fake${eventCounter++}")
        val text = when (body) {
            is MessageBody.Text -> body.text
            is MessageBody.Image -> body.caption ?: "Photo"
            is MessageBody.Video -> body.caption ?: "Video"
            is MessageBody.Audio -> "Audio"
            is MessageBody.File -> body.displayName
        }
        (timeline(roomId) as FakeTimeline).append(
            TimelineItem(
                eventId = id,
                sender = UserId("@me:example"),
                senderDisplayName = "You",
                timestamp = System.currentTimeMillis(),
                content = TimelineContent.Text(text),
                state = MessageState.SENT
            )
        )
        _rooms.update { list ->
            list.map {
                if (it.id == roomId) it.copy(lastMessagePreview = text, lastMessageAt = System.currentTimeMillis())
                else it
            }
        }
        return Result.success(id)
    }

    override suspend fun react(eventId: EventId, emoji: String): Result<Unit> = Result.success(Unit)

    override fun devices(): Flow<List<DeviceInfo>> = _devices.asStateFlow()

    override suspend fun verifyDevice(deviceId: DeviceId): VerificationFlow = FakeVerificationFlow()

    // ── test/dev controls ────────────────────────────────────────────────────

    fun setState(state: SessionState) { _state.value = state }

    fun setRooms(rooms: List<RoomSummary>) { _rooms.value = rooms }

    fun setDevices(devices: List<DeviceInfo>) { _devices.value = devices }

    private var eventCounter = 0

    companion object {
        /** A small, deterministic set for driving the UI during development. */
        fun withSampleData(): FakeSocialSession = FakeSocialSession(
            SessionState.SignedIn(
                userId = UserId("@me:example"),
                deviceId = DeviceId("DEVICE01"),
                keyBackupHealthy = true,
                unverifiedDeviceCount = 0
            )
        ).apply {
            val now = System.currentTimeMillis()
            setRooms(
                listOf(
                    RoomSummary(RoomId("!a:example"), "Sam", null, "See you at six", now - 60_000, 2, false, false),
                    RoomSummary(RoomId("!b:example"), "Climbing", null, "Who's in?", now - 3_600_000, 0, true, false),
                    // §14.2.3 — the timeline must render this, not hide it.
                    RoomSummary(RoomId("!c:example"), "Priya", null, null, now - 86_400_000, 1, false, false,
                        lastMessageUndecryptable = true)
                )
            )
        }
    }
}

private class FakeTimeline : Timeline {
    private val _items = MutableStateFlow<List<TimelineItem>>(emptyList())
    override val items: Flow<List<TimelineItem>> = _items.asStateFlow()
    override suspend fun paginateBack(count: Int): Boolean = false
    override suspend fun markRead(upTo: EventId) = Unit
    override fun close() = Unit
    fun append(item: TimelineItem) { _items.update { it + item } }
}

private class FakeVerificationFlow : VerificationFlow {
    private val _steps = MutableStateFlow<VerificationStep>(VerificationStep.Requested)
    override val steps: Flow<VerificationStep> = _steps.asStateFlow()
    override suspend fun confirmMatch() { _steps.value = VerificationStep.Verified }
    override suspend fun declineMatch() { _steps.value = VerificationStep.Cancelled("declined") }
    override suspend fun cancel() { _steps.value = VerificationStep.Cancelled("cancelled") }
}
