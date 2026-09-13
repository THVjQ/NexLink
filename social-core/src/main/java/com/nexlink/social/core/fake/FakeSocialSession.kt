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

    // ---- §31.3 safety ------------------------------------------------------

    private val _blocked = MutableStateFlow<List<UserId>>(emptyList())
    /** Reports the UI has sent, so a test can assert consent was honoured. */
    val reports = mutableListOf<Triple<UserId, String, Boolean>>()

    override suspend fun blockUser(userId: UserId): Result<Unit> {
        _blocked.update { if (userId in it) it else it + userId }
        return Result.success(Unit)
    }

    override suspend fun unblockUser(userId: UserId): Result<Unit> {
        _blocked.update { it - userId }
        return Result.success(Unit)
    }

    override fun blockedUsers(): Flow<List<UserId>> = _blocked.asStateFlow()

    override suspend fun reportUser(
        userId: UserId,
        reason: String,
        includeContent: Boolean,
        roomId: RoomId?,
        eventId: EventId?
    ): Result<Unit> {
        reports.add(Triple(userId, reason, includeContent))
        return Result.success(Unit)
    }

    // ---- §13.3 push --------------------------------------------------------

    /** Records the last registration so a test can assert it happened. */
    var registeredPush: Triple<String, String, String>? = null
        private set

    override suspend fun registerPush(
        pushToken: String,
        appId: String,
        gatewayUrl: String
    ): Result<Unit> {
        registeredPush = Triple(pushToken, appId, gatewayUrl)
        return Result.success(Unit)
    }

    override suspend fun unregisterPush(pushToken: String, appId: String): Result<Unit> {
        registeredPush = null
        return Result.success(Unit)
    }

    // ---- §12.5 storage -----------------------------------------------------
    //
    // Plausible numbers rather than zeroes: a storage screen that shows 0 B for
    // everything looks broken and, worse, cannot be reviewed — the formatting,
    // the ordering and the "clearing this frees X" copy are all untestable
    // against an empty store.

    private var media = 412L * 1024 * 1024
    private var eventCache = 63L * 1024 * 1024
    var retention: MediaRetention = MediaRetention.DEFAULT
        private set

    override suspend fun storeSizes(): Result<StoreUsage> = Result.success(
        StoreUsage(
            stateBytes = 18L * 1024 * 1024,
            eventCacheBytes = eventCache,
            mediaBytes = media,
            cryptoBytes = 3L * 1024 * 1024
        )
    )

    override suspend fun applyMediaRetention(policy: MediaRetention): Result<Unit> {
        retention = policy
        return Result.success(Unit)
    }

    /** Clears exactly what the real one clears — crypto and state survive. */
    override suspend fun clearCaches(): Result<Unit> {
        media = 0
        eventCache = 0
        return Result.success(Unit)
    }

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

    override suspend fun sendImage(roomId: RoomId, localUri: String): Result<Unit> = Result.success(Unit)

    override suspend fun sendFile(roomId: RoomId, localUri: String): Result<Unit> = Result.success(Unit)

    override suspend fun createGroup(name: String, invite: List<UserId>): Result<RoomId> =
        Result.success(RoomId("!fakegroup:example"))

    override suspend fun inviteToRoom(roomId: RoomId, userId: UserId): Result<Unit> = Result.success(Unit)

    override suspend fun members(roomId: RoomId): Result<List<RoomMemberSummary>> = Result.success(emptyList())

    override suspend fun acceptInvite(roomId: RoomId): Result<Unit> = Result.success(Unit)

    override suspend fun leaveRoom(roomId: RoomId): Result<Unit> = Result.success(Unit)

    override suspend fun markRead(roomId: RoomId): Result<Unit> = Result.success(Unit)

    override suspend fun setTyping(roomId: RoomId, typing: Boolean): Result<Unit> = Result.success(Unit)

    override fun typingUsers(roomId: RoomId): Flow<List<String>> =
        MutableStateFlow(emptyList<String>()).asStateFlow()

    override fun searchMessages(query: String): Flow<List<MessageSearchHit>> =
        MutableStateFlow(emptyList<MessageSearchHit>()).asStateFlow()

    override suspend fun loadMedia(mediaId: String): Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun edit(roomId: RoomId, eventId: EventId, newText: String): Result<Unit> = Result.success(Unit)

    override suspend fun delete(roomId: RoomId, eventId: EventId, reason: String?): Result<Unit> = Result.success(Unit)

    override suspend fun react(roomId: RoomId, eventId: EventId, emoji: String): Result<Unit> = Result.success(Unit)

    override fun devices(): Flow<List<DeviceInfo>> = _devices.asStateFlow()

    override suspend fun verifyDevice(deviceId: DeviceId): VerificationFlow = FakeVerificationFlow()

    override suspend fun findUsers(query: String): Result<List<UserSummary>> =
        Result.success(emptyList())

    override suspend fun startDirectMessage(userId: UserId): Result<RoomId> =
        Result.success(RoomId("!fake:example"))

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

/**
 * Public so tests can drive failure states directly — see [FakeTimeline.fail].
 * There is no other way to model an unsendable message without a network.
 */
class FakeTimeline : Timeline {
    private val _items = MutableStateFlow<List<TimelineItem>>(emptyList())
    override val items: Flow<List<TimelineItem>> = _items.asStateFlow()
    override suspend fun paginateBack(count: Int): Boolean = false
    override suspend fun markRead(upTo: EventId) = Unit

    /**
     * §13.5.2 — the fake actually moves the message, so a UI test can drive the
     * whole failed → retrying → sent path without a network. A retry that
     * returned success while leaving the bubble red would let the exact bug
     * this models slip through the test that exists to catch it.
     */
    override suspend fun retrySend(id: EventId): Result<Unit> = runCatching {
        _items.update { list ->
            list.map {
                if (it.eventId == id) it.copy(state = MessageState.SENT, sendFailure = null) else it
            }
        }
    }

    override suspend fun cancelSend(id: EventId): Result<Boolean> = runCatching {
        val present = _items.value.any { it.eventId == id }
        _items.update { list -> list.filterNot { it.eventId == id } }
        present
    }

    override fun close() = Unit
    fun append(item: TimelineItem) { _items.update { it + item } }
    /** Force a message into a failed state — for tests of the §13.5 surface. */
    fun fail(id: EventId, reason: com.nexlink.social.core.session.SendFailure) {
        _items.update { list ->
            list.map {
                if (it.eventId == id) it.copy(
                    state = if (reason == com.nexlink.social.core.session.SendFailure.OFFLINE)
                        MessageState.QUEUED_OFFLINE else MessageState.FAILED,
                    sendFailure = reason
                ) else it
            }
        }
    }
}

private class FakeVerificationFlow : VerificationFlow {
    private val _steps = MutableStateFlow<VerificationStep>(VerificationStep.Requested)
    override val steps: Flow<VerificationStep> = _steps.asStateFlow()
    override suspend fun confirmMatch() { _steps.value = VerificationStep.Verified }
    override suspend fun declineMatch() { _steps.value = VerificationStep.Cancelled("declined") }
    override suspend fun cancel() { _steps.value = VerificationStep.Cancelled("cancelled") }
}
