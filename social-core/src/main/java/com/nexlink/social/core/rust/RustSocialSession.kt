package com.nexlink.social.core.rust

import com.nexlink.social.core.DeviceManager
import com.nexlink.social.core.ImagePrep
import com.nexlink.social.core.session.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.matrix.rustcomponents.sdk.AuthData
import org.matrix.rustcomponents.sdk.VerificationState
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.RoomPreset
import org.matrix.rustcomponents.sdk.RoomVisibility
import org.matrix.rustcomponents.sdk.AuthDataPasswordDetails
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.LatestEventValue
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.ReceiptType
import org.matrix.rustcomponents.sdk.Membership
import org.matrix.rustcomponents.sdk.Room as SdkRoom
import org.matrix.rustcomponents.sdk.RoomListEntriesListener
import org.matrix.rustcomponents.sdk.RoomListEntriesUpdate
import org.matrix.rustcomponents.sdk.RoomListEntriesWithDynamicAdaptersResult
import org.matrix.rustcomponents.sdk.RoomListService
import org.matrix.rustcomponents.sdk.SqliteStoreBuilder
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import com.nexlink.social.core.session.MediaRetention
import com.nexlink.social.core.session.StoreUsage
import org.matrix.rustcomponents.sdk.HttpPusherData
import org.matrix.rustcomponents.sdk.IgnoredUsersListener
import org.matrix.rustcomponents.sdk.PushFormat
import org.matrix.rustcomponents.sdk.PusherIdentifiers
import org.matrix.rustcomponents.sdk.PusherKind
import org.matrix.rustcomponents.sdk.SyncService
import uniffi.matrix_sdk_base.MediaRetentionPolicy
import java.time.Duration

/**
 * [SocialSession] backed by matrix-rust-sdk — §11.6.
 *
 * **This is the only file in the project permitted to import
 * `org.matrix.rustcomponents.sdk.*`.** `tools/check-invariants.sh` fails the
 * build on an SDK import anywhere in `:social`, `:social-ui` or
 * `:social-contract`. The seam is the insurance policy against §11's decision
 * being wrong: if the SDK is swapped, this file and its neighbours are the
 * rewrite, not the application.
 *
 * Phase 2 status (§33.3): the bake-off is in progress. Steps 1–3 of §11.7 —
 * login, list rooms, send an encrypted message — are implemented here. Steps 4–6
 * (cross-signing, second-device verification, history restore) are the decision
 * gate and need two devices; see [RustRecovery] for the recovery half.
 */
class RustSocialSession private constructor(
    private val client: Client,
    private val syncService: SyncService,
    private val roomListService: RoomListService
) : SocialSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Every call into the SDK goes through here.
     *
     * The bindings are FFI: most calls block the calling thread, including ones
     * declared `suspend` in Kotlin, because `suspend` describes the Kotlin side
     * and not what the Rust side does with the thread. Calling them from a
     * `lifecycleScope` coroutine — which defaults to the **main** dispatcher —
     * froze the app on opening a conversation, twice, with an ANR and nothing in
     * the crash log.
     *
     * **The rule: this class never assumes its caller is off the main thread.**
     * §11.6's seam is called from UI code by design, so the hop belongs here
     * rather than at every call site, where it would eventually be forgotten.
     */
    private suspend fun <T> io(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { block() }

    private suspend fun <T> ioCatching(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }

    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    override val state: Flow<SessionState> = _state.asStateFlow()

    private val _rooms = MutableStateFlow<List<RoomSummary>>(emptyList())
    override fun rooms(): Flow<List<RoomSummary>> = _rooms.asStateFlow()

    /** §11.7 step 1 — the session is live; publish who we are. */
    suspend fun publishSignedInState() {
        val enc = client.encryption()
        _state.value = SessionState.SignedIn(
            userId = UserId(client.userId()),
            deviceId = DeviceId(client.deviceId()),
            // §8.5.2 — backup health. False means a new device gets no history.
            keyBackupHealthy = runCatching { enc.backupExistsOnServer() }.getOrDefault(false),
            // §8.6 — NOT available from the SDK (§11.7.3). The device list needs
            // raw Client-Server API calls, so this stays 0 until that is built.
            unverifiedDeviceCount = 0
        )
    }

    private var roomListHandle: RoomListEntriesWithDynamicAdaptersResult? = null

    /**
     * Room handles as the room list reports them — §14.8.
     *
     * `client.rooms()` returns only **joined** rooms, so an invitation is
     * invisible to it. The room list's entries include invites, which is the
     * whole reason the list is built from here rather than from `rooms()`.
     * Building it the easy way silently loses every invitation, and nothing
     * reports an error.
     */
    private val entries = java.util.Collections.synchronizedList(mutableListOf<SdkRoom>())

    suspend fun startSync() {
        syncService.start()
        observeRoomList()
        // §8.4 — install the verification delegate HERE, not when the verify
        // screen opens.
        //
        // A verification request arrives as a to-device event and is handled by
        // the crypto machine the moment sync delivers it. If no delegate is
        // attached yet, it is consumed with nobody listening and the UI never
        // learns of it — which is what happened on the first run of this, and
        // it fails silently in both directions: the asking device waits
        // forever, the answering device shows nothing.
        //
        // Worse, the request stays *ongoing*. Asking again then produces
        //
        //   matrix_sdk_crypto::verification::machine: Received a new
        //   verification request whilst another request with the same user is
        //   ongoing. Cancelling both requests.
        //
        // so the retry cancels the original as well, and the user sees the
        // second attempt fail for no visible reason.
        //
        // §8.3.2 makes this a security requirement rather than a nicety: an
        // unexpected verification request is the signal that someone is trying
        // to add a device to the account, and a request nobody can see is a
        // warning that was never given.
        runCatching { startVerification() }
    }

    /**
     * §13.2 — observe the room list instead of polling it.
     *
     * The first cut polled every two seconds, which is a real battery cost
     * (§13.7) for a list that changes rarely. `entriesWithDynamicAdapters`
     * pushes a diff when something actually changes.
     *
     * The diff carries room handles, not summaries, and building a summary
     * requires suspending calls (`roomInfo()`, `latestEvent()`). So the listener
     * signals and a coroutine rebuilds — still event-driven, just not
     * synchronous inside the callback.
     */
    private suspend fun observeRoomList() {
        val all = roomListService.allRooms()
        roomListHandle = all.entriesWithDynamicAdapters(
            pageSize = 100u,
            listener = object : RoomListEntriesListener {
                override fun onUpdate(roomEntriesUpdate: List<RoomListEntriesUpdate>) {
                    if (roomEntriesUpdate.isEmpty()) return
                    synchronized(entries) {
                        roomEntriesUpdate.forEach { u ->
                            when (u) {
                                is RoomListEntriesUpdate.Append -> entries.addAll(u.values)
                                is RoomListEntriesUpdate.Reset -> { entries.clear(); entries.addAll(u.values) }
                                is RoomListEntriesUpdate.PushBack -> entries.add(u.value)
                                is RoomListEntriesUpdate.PushFront -> entries.add(0, u.value)
                                is RoomListEntriesUpdate.Insert ->
                                    entries.add(u.index.toInt().coerceIn(0, entries.size), u.value)
                                is RoomListEntriesUpdate.Set -> {
                                    val i = u.index.toInt()
                                    if (i in entries.indices) entries[i] = u.value else entries.add(u.value)
                                }
                                is RoomListEntriesUpdate.Remove -> {
                                    val i = u.index.toInt(); if (i in entries.indices) entries.removeAt(i)
                                }
                                is RoomListEntriesUpdate.PopBack ->
                                    if (entries.isNotEmpty()) entries.removeAt(entries.size - 1)
                                is RoomListEntriesUpdate.PopFront ->
                                    if (entries.isNotEmpty()) entries.removeAt(0)
                                else -> entries.clear()
                            }
                        }
                    }
                    scope.launch { runCatching { refreshRooms() } }
                }
            }
        )
        // Seed once — the listener only fires on change, and an empty inbox on
        // first launch would look like an empty account.
        runCatching { refreshRooms() }
    }
    suspend fun stopSync() {
        scope.coroutineContext.cancelChildren()
        synchronized(typingHandles) {
            typingHandles.values.forEach { runCatching { it.cancel() } }
            typingHandles.clear()
        }
        runCatching { roomListHandle?.controller()?.destroy() }
        roomListHandle = null
        syncService.stop()
    }

    /** The last published state, for callers that need it synchronously. */
    fun currentState(): SessionState = _state.value

    /** §12.4 — persist the credentials, encrypted at rest. */
    fun persistTo(store: com.nexlink.social.core.SessionStore) {
        store.save(client.session())
    }

    private val timelines = mutableMapOf<String, RustTimeline>()

    override suspend fun timeline(roomId: RoomId): Timeline = io {
        synchronized(timelines) { timelines[roomId.value] }?.let { return@io it }
        val room = roomListService.room(roomId.value)
        val t = RustTimeline(room.timeline())
        t.start()
        synchronized(timelines) { timelines[roomId.value] = t }
        t
    }

    /** §11.7 step 3 — send an encrypted text message. */
    override suspend fun send(roomId: RoomId, body: MessageBody): Result<EventId> = io {
        (timeline(roomId) as RustTimeline).send(body)
    }

    /** Set by [SocialSessionManager] so image prep can reach a Context. */
    var appContext: android.content.Context? = null

    override suspend fun sendImage(roomId: RoomId, localUri: String): Result<Unit> = io {
        val ctx = appContext ?: return@io Result.failure(IllegalStateException("no context"))
        // §14.5.1 — downscale and strip EXIF BEFORE upload. Once it is encrypted
        // and on the server it is too late to remove the GPS coordinates.
        val prepared = ImagePrep.prepare(ctx, android.net.Uri.parse(localUri))
            .getOrElse { return@io Result.failure(it) }
        (timeline(roomId) as RustTimeline)
            .sendImage(prepared.file, prepared.width, prepared.height, prepared.mimeType)
            .also { runCatching { prepared.file.delete() } }
    }

    override suspend fun createGroup(name: String, invite: List<UserId>): Result<RoomId> = ioCatching {
        val id = client.createRoom(
            CreateRoomParameters(
                name = name,
                topic = null,
                isEncrypted = true,          // §2.8 #1 — never optional
                isDirect = false,
                visibility = RoomVisibility.Private,
                // §2.5 — invite-only. A group is not discoverable and cannot be
                // joined by knowing its id.
                preset = RoomPreset.PRIVATE_CHAT,
                invite = invite.map { it.value },
                avatar = null,
                powerLevelContentOverride = null,
                joinRuleOverride = null,
                historyVisibilityOverride = null,
                canonicalAlias = null
            )
        )
        refreshRooms()
        RoomId(id)
    }

    override suspend fun inviteToRoom(roomId: RoomId, userId: UserId): Result<Unit> = ioCatching {
        roomListService.room(roomId.value).inviteUserById(userId.value)
    }

    override suspend fun members(roomId: RoomId): Result<List<RoomMemberSummary>> = ioCatching {
        val me = client.userId()
        roomListService.room(roomId.value).members().use { it ->
            buildList {
                var m = it.nextChunk(50u)
                while (m != null && m.isNotEmpty()) {
                    m.forEach { member ->
                        add(RoomMemberSummary(
                            id = UserId(member.userId),
                            displayName = member.displayName,
                            membership = when (member.membership) {
                                is org.matrix.rustcomponents.sdk.MembershipState.Join -> "joined"
                                is org.matrix.rustcomponents.sdk.MembershipState.Invite -> "invited"
                                is org.matrix.rustcomponents.sdk.MembershipState.Leave -> "left"
                                is org.matrix.rustcomponents.sdk.MembershipState.Ban -> "banned"
                                is org.matrix.rustcomponents.sdk.MembershipState.Knock -> "knocked"
                                else -> "unknown"
                            },
                            isSelf = member.userId == me
                        ))
                    }
                    m = it.nextChunk(50u)
                }
            }
        }
    }

    override suspend fun acceptInvite(roomId: RoomId): Result<Unit> = ioCatching {
        roomListService.room(roomId.value).join()
        // The room-list listener does eventually report the membership change,
        // but "eventually" here is seconds and the user has just tapped Accept.
        // Refresh immediately so the invitation stops being offered.
        refreshRooms()
    }

    override suspend fun leaveRoom(roomId: RoomId): Result<Unit> = ioCatching {
        roomListService.room(roomId.value).leave()
        refreshRooms()
    }

    override suspend fun markRead(roomId: RoomId): Result<Unit> = ioCatching {
        // READ_PRIVATE clears the unread count without telling the other
        // participants when you opened it — see the interface doc.
        roomListService.room(roomId.value).markAsRead(ReceiptType.READ_PRIVATE)
        refreshRooms()
    }

    override suspend fun setTyping(roomId: RoomId, typing: Boolean): Result<Unit> = ioCatching {
        roomListService.room(roomId.value).typingNotice(typing)
    }

    // ---- §31.3 safety ------------------------------------------------------

    private val _blocked = MutableStateFlow<List<UserId>>(emptyList())
    @Volatile private var blockedHandle: org.matrix.rustcomponents.sdk.TaskHandle? = null

    override suspend fun blockUser(userId: UserId): Result<Unit> = ioCatching {
        client.ignoreUser(userId.value)
        refreshBlocked()
    }

    override suspend fun unblockUser(userId: UserId): Result<Unit> = ioCatching {
        client.unignoreUser(userId.value)
        refreshBlocked()
    }

    override fun blockedUsers(): Flow<List<UserId>> {
        if (blockedHandle == null) {
            // Subscribe once. The list also changes from the user's other
            // devices — §31.3.1 puts blocking under the user's control, and a
            // block made on the phone that does not show on the tablet is not
            // under their control.
            blockedHandle = client.subscribeToIgnoredUsers(
                object : IgnoredUsersListener {
                    override fun call(ignoredUserIds: List<String>) {
                        _blocked.value = ignoredUserIds.map { UserId(it) }
                    }
                }
            )
        }
        return _blocked.asStateFlow()
    }

    private suspend fun refreshBlocked() {
        runCatching { _blocked.value = client.ignoredUsers().map { UserId(it) } }
    }

    /**
     * §31.3.2 — the report.
     *
     * With consent the specific event is reported, so the operator sees what
     * the reporter chose to show and nothing else. Without it, only the room
     * and the reporter's description travel.
     *
     * Nothing here gives the operator a way to read a room. §31.3.2: *"there is
     * no operator key that can read the room, only one that can read what a
     * user chose to send."*
     */
    override suspend fun reportUser(
        userId: UserId,
        reason: String,
        includeContent: Boolean,
        roomId: RoomId?,
        eventId: EventId?
    ): Result<Unit> = ioCatching {
        val room = roomId?.let { runCatching { roomListService.room(it.value) }.getOrNull() }
        when {
            // Consented: report the specific event, so the operator sees what
            // the reporter chose to show and nothing beyond it.
            includeContent && room != null && eventId != null ->
                room.reportContent(eventId.value, reason)

            // §31.3.2 — "Reports without content are still actionable."
            // Several independent reports against one account is a signal on
            // its own, so a report with no consent still has to go somewhere.
            room != null -> room.reportRoom(reason)

            // No room context at all — a profile-level report. There is no
            // Matrix API for this, so it is refused loudly rather than
            // silently dropped: a report the user believes was sent and was
            // not is worse than an error.
            else -> error(
                "a report needs a room; profile-only reporting is not supported " +
                "by the homeserver API (§31.3.2)")
        }
    }

    // ---- §13.3 push --------------------------------------------------------

    override suspend fun registerPush(
        pushToken: String,
        appId: String,
        gatewayUrl: String
    ): Result<Unit> = ioCatching {
        client.setPusher(
            identifiers = PusherIdentifiers(pushkey = pushToken, appId = appId),
            kind = PusherKind.Http(
                HttpPusherData(
                    url = gatewayUrl,
                    // EventIdOnly is the point, not an optimisation. §13.3.1:
                    // "The homeserver never sends content to the gateway for an
                    // encrypted room. The push carries an event ID and a room
                    // ID at most." Choosing the richer format would hand the
                    // gateway and FCM exactly what §2.8 #1 exists to withhold.
                    format = PushFormat.EVENT_ID_ONLY,
                    defaultPayload = null
                )
            ),
            appDisplayName = "NexLink Social",
            deviceDisplayName = android.os.Build.MODEL ?: "Android",
            profileTag = "",
            lang = "en",
            append = false
        )
    }

    override suspend fun unregisterPush(pushToken: String, appId: String): Result<Unit> =
        ioCatching {
            client.deletePusher(PusherIdentifiers(pushkey = pushToken, appId = appId))
        }

    // ---- §12.5 storage -----------------------------------------------------

    override suspend fun storeSizes(): Result<StoreUsage> = ioCatching {
        val s = client.getStoreSizes()
        // The SDK reports ULong. Every one of these is a byte count on a phone
        // and cannot plausibly exceed Long.MAX_VALUE, but coerce rather than
        // convert so a garbage value shows as a huge number instead of a
        // negative one — a negative size renders as "-2.1 GB" and looks like a
        // bug in the app rather than in the store.
        StoreUsage(
            // Each field is nullable: the SDK reports null for a store it has
            // not opened, which is not the same as "empty" but renders the same
            // and is the only honest thing to show before first sync.
            stateBytes = s.stateStore.bytes(),
            eventCacheBytes = s.eventCacheStore.bytes(),
            mediaBytes = s.mediaStore.bytes(),
            cryptoBytes = s.cryptoStore.bytes()
        )
    }

    override suspend fun applyMediaRetention(policy: MediaRetention): Result<Unit> = ioCatching {
        client.setMediaRetentionPolicy(
            MediaRetentionPolicy(
                // null = unlimited. The SDK models that as an absent cap, not
                // as a very large number.
                maxCacheSize = policy.maxCacheBytes?.toULong(),
                maxFileSize = policy.maxFileBytes.toULong(),
                lastAccessExpiry = Duration.ofDays(policy.lastAccessExpiryDays),
                // Hourly. Cleanup walks the media store, so it is not free, and
                // running it on every access would make scrolling a gallery
                // pay for housekeeping.
                cleanupFrequency = Duration.ofHours(1)
            )
        )
    }

    /**
     * §12.5.2 — note what this does **not** do.
     *
     * `clearCaches` drops the state and event caches and cached media. It does
     * not touch the crypto store: that is the SDK's behaviour and it is also
     * the requirement (§12.5.1). Megolm keys are not regenerable and their loss
     * is permanent, so "free up space" must never be able to reach them.
     *
     * Messages are not lost either — the event cache is a local copy and the
     * homeserver still holds the events, so the timeline re-populates on the
     * next sync. It does cost a re-download, which the settings copy says.
     */
    override suspend fun clearCaches(): Result<Unit> = ioCatching {
        client.clearCaches(syncService)
    }

    private val typing = mutableMapOf<String, MutableStateFlow<List<String>>>()
    private val typingHandles = mutableMapOf<String, org.matrix.rustcomponents.sdk.TaskHandle>()

    /**
     * §14.7 — who else is typing.
     *
     * **The subscription happens off the main thread**, deliberately. Both
     * `roomListService.room()` and `subscribeToTypingNotifications()` are
     * blocking FFI calls; doing them inline in this non-suspend function meant
     * they ran on whatever thread the collector was on — the main one — and the
     * app ANR'd on opening a conversation.
     *
     * The general rule this is an instance of: **nothing in this class may
     * assume its caller is on a background thread.** The seam is called from UI
     * code, and an FFI hop is not free.
     */
    override fun typingUsers(roomId: RoomId): Flow<List<String>> {
        val flow = synchronized(typing) {
            typing.getOrPut(roomId.value) { MutableStateFlow(emptyList()) }
        }
        val alreadySubscribed = synchronized(typingHandles) { typingHandles.containsKey(roomId.value) }
        if (!alreadySubscribed) {
            scope.launch {
                runCatching {
                    val handle = roomListService.room(roomId.value).subscribeToTypingNotifications(
                        object : org.matrix.rustcomponents.sdk.TypingNotificationsListener {
                            override fun call(typingUserIds: List<String>) {
                                // §6.7 — the MXID is what the SDK reports, and it
                                // is what should be shown anyway.
                                flow.value = typingUserIds
                            }
                        }
                    )
                    synchronized(typingHandles) { typingHandles[roomId.value] = handle }
                }
            }
        }
        return flow.asStateFlow()
    }

    private var searchService: org.matrix.rustcomponents.sdk.SearchService? = null
    private var searchHandle: org.matrix.rustcomponents.sdk.TaskHandle? = null
    private val searchResults = MutableStateFlow<List<MessageSearchHit>>(emptyList())

    override fun searchMessages(query: String): Flow<List<MessageSearchHit>> {
        // The FFI blocks (§11.7.5), so never on the caller's thread.
        scope.launch {
            runCatching {
                if (searchService == null) {
                    val svc = client.searchService()
                    searchHandle = svc.subscribeToResults(
                        object : org.matrix.rustcomponents.sdk.SearchServiceResultsListener {
                            override fun onUpdate(
                                results: List<org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate>
                            ) {
                                synchronized(searchAccum) {
                                    results.forEach { applySearchUpdate(it) }
                                    searchResults.value = searchAccum.mapNotNull { it.toHit() }
                                }
                            }
                        }
                    )
                    searchService = svc
                }
                if (query.isBlank()) {
                    synchronized(searchAccum) { searchAccum.clear() }
                    searchResults.value = emptyList()
                } else {
                    searchService?.setQuery(query)
                    searchService?.paginate()
                }
            }
        }
        return searchResults.asStateFlow()
    }

    private val searchAccum =
        mutableListOf<org.matrix.rustcomponents.sdk.SearchServiceResult>()

    private fun applySearchUpdate(u: org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate) {
        when (u) {
            is org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate.Append ->
                searchAccum.addAll(u.values)
            is org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate.Reset -> {
                searchAccum.clear(); searchAccum.addAll(u.values)
            }
            is org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate.PushBack ->
                searchAccum.add(u.value)
            is org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate.PushFront ->
                searchAccum.add(0, u.value)
            is org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate.Insert ->
                searchAccum.add(u.index.toInt().coerceIn(0, searchAccum.size), u.value)
            is org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate.Set -> {
                val i = u.index.toInt()
                if (i in searchAccum.indices) searchAccum[i] = u.value else searchAccum.add(u.value)
            }
            is org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate.Remove -> {
                val i = u.index.toInt(); if (i in searchAccum.indices) searchAccum.removeAt(i)
            }
            is org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate.PopBack ->
                if (searchAccum.isNotEmpty()) searchAccum.removeAt(searchAccum.size - 1)
            is org.matrix.rustcomponents.sdk.SearchServiceResultsUpdate.PopFront ->
                if (searchAccum.isNotEmpty()) searchAccum.removeAt(0)
            else -> searchAccum.clear()
        }
    }

    private fun org.matrix.rustcomponents.sdk.SearchServiceResult.toHit(): MessageSearchHit? {
        val msg = this as? org.matrix.rustcomponents.sdk.SearchServiceResult.Message ?: return null
        val m = msg.result
        val body = (m.content.toAppContent() as? com.nexlink.social.core.session.TimelineContent.Text)
            ?.body ?: return null
        return MessageSearchHit(
            eventId = EventId(m.eventId),
            roomId = RoomId(msg.roomId),
            sender = UserId(m.sender),
            senderDisplayName = (m.senderProfile as? org.matrix.rustcomponents.sdk.ProfileDetails.Ready)
                ?.displayName,
            body = body,
            timestamp = m.timestamp.toLong()
        )
    }

    override suspend fun loadMedia(mediaId: String): Result<ByteArray> = ioCatching {
        client.getMediaContent(MediaSource.fromJson(mediaId))
    }

    override suspend fun edit(roomId: RoomId, eventId: EventId, newText: String): Result<Unit> = io {
        (timeline(roomId) as RustTimeline).edit(eventId, newText)
    }

    override suspend fun delete(roomId: RoomId, eventId: EventId, reason: String?): Result<Unit> = io {
        (timeline(roomId) as RustTimeline).redact(eventId, reason)
    }

    override suspend fun sendFile(roomId: RoomId, localUri: String): Result<Unit> = io {
        val ctx = appContext ?: return@io Result.failure(IllegalStateException("no context"))
        val uri = android.net.Uri.parse(localUri)
        val resolver = ctx.contentResolver

        var name = "file"
        var size = 0L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0) size = c.getLong(si)
                }
            }
        }

        // §25.2 — the cap is Cloudflare's 100 MB request limit, not a
        // preference. Fail here with a clear message rather than letting the
        // user wait through an upload that the edge will reject.
        if (size > MAX_UPLOAD_BYTES) {
            return@io Result.failure(
                IllegalArgumentException(
                    "That file is ${size / (1024 * 1024)} MB. The limit is " +
                        "${MAX_UPLOAD_BYTES / (1024 * 1024)} MB."
                )
            )
        }

        // Copy to a private temp file: the SDK needs a real path, and a
        // content:// URI may not have one.
        //
        // The file keeps its ORIGINAL name inside a unique directory, rather
        // than getting a unique name. The SDK sends the file's basename, so
        // prefixing it made the recipient see "upload-1789170276946-report.pdf"
        // — an internal detail leaking into someone else's conversation.
        val dir = java.io.File(ctx.cacheDir, "upload-${System.currentTimeMillis()}").apply { mkdirs() }
        val tmp = java.io.File(dir, name)
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            }
        }.getOrElse { return@io Result.failure(it) }

        val mime = resolver.getType(uri) ?: "application/octet-stream"
        (timeline(roomId) as RustTimeline)
            .sendFile(tmp, name, mime)
            .also { runCatching { tmp.delete(); dir.delete() } }
    }

    override suspend fun react(roomId: RoomId, eventId: EventId, emoji: String): Result<Unit> = io {
        (timeline(roomId) as RustTimeline).toggleReaction(eventId, emoji)
    }

    override suspend fun findUsers(query: String): Result<List<UserSummary>> = ioCatching {
        // §6.6 — the search is server-side over this homeserver's directory
        // only. Federation is off (§2.6), so there is nowhere else to look.
        client.searchUsers(query, 20uL).results.map {
            UserSummary(UserId(it.userId), it.displayName, it.avatarUrl)
        }
    }

    override suspend fun startDirectMessage(userId: UserId): Result<RoomId> = ioCatching {
        // Reuse an existing DM rather than creating a second one. Two rooms with
        // the same person is confusing and splits history for no reason.
        client.getDmRoom(userId.value)?.let { return@ioCatching RoomId(it.id()) }

        val id = client.createRoom(
            CreateRoomParameters(
                name = null,
                topic = null,
                isEncrypted = true,
                isDirect = true,
                visibility = RoomVisibility.Private,
                preset = RoomPreset.TRUSTED_PRIVATE_CHAT,
                invite = listOf(userId.value),
                avatar = null,
                powerLevelContentOverride = null,
                joinRuleOverride = null,
                historyVisibilityOverride = null,
                canonicalAlias = null
            )
        )
        RoomId(id)
    }

    /**
     * Build the room list — §14.1.
     *
     * Each room contributes its own latest event, so a row can show what was
     * actually said rather than "No messages yet" next to a busy conversation.
     */
    suspend fun refreshRooms() = io {
        val snapshot = synchronized(entries) { entries.toList() }
        val source = if (snapshot.isNotEmpty()) snapshot else client.rooms()
        val summaries = source.mapNotNull { handle ->
            runCatching {
                // Re-resolve through the room list service: a handle captured in
                // an earlier diff can report stale membership after a join.
                val room = runCatching { roomListService.room(handle.id()) }.getOrDefault(handle)
                val info = room.roomInfo()
                val invited = info.membership == Membership.INVITED
                // An invite has no timeline yet, and asking for one throws.
                val latest = if (invited) null else runCatching { room.latestEvent() }.getOrNull()
                val remote = latest as? LatestEventValue.Remote
                val preview = remote?.content?.toRoomPreview()
                RoomSummary(
                    id = RoomId(room.id()),
                    title = info.displayName ?: room.id(),
                    avatarUrl = info.avatarUrl,
                    lastMessagePreview = preview,
                    lastMessageAt = (latest as? LatestEventValue.Remote)?.timestamp?.toLong() ?: 0L,
                    unreadCount = info.notificationCount.toInt(),
                    isGroup = !info.isDirect,
                    isMuted = false,
                    lastMessageUndecryptable =
                        (latest as? LatestEventValue.Remote)?.content?.toAppContent()
                            is com.nexlink.social.core.session.TimelineContent.Undecryptable,
                    isInvite = invited,
                    invitedBy = if (invited) runCatching { room.inviter()?.userId }.getOrNull() else null
                )
            }.getOrNull()
        }
        // Newest first — an inbox ordered any other way is not an inbox.
        _rooms.value = summaries.sortedByDescending { it.lastMessageAt }
    }

    /**
     * §8.6 — deliberately empty, and this is a finding rather than a stub.
     *
     * §11.7.3: the Rust bindings expose no device-listing API. What looks like
     * one (`AccountManagementAction.DevicesList`) is a deep link into the
     * homeserver's own web UI. Implementing this means raw
     * `GET /_matrix/client/v3/devices` against the session's access token, and
     * deletion additionally needs User-Interactive Auth.
     */
    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    override fun devices(): Flow<List<DeviceInfo>> = _devices.asStateFlow()

    /** §8.6 — the SDK has no device list, so this goes to the raw C-S API. */
    fun deviceManager(): DeviceManager =
        DeviceManager(client.session().homeserverUrl, client.session().accessToken)

    /** Returns the failure so the UI can show it — §14.2.2, never swallow. */
    suspend fun refreshDevices(): Result<Unit> = ioCatching {
        deviceManager().list(client.deviceId()).onFailure { throw it }.onSuccess { list ->
            // §8.3.2 — the server's view of which devices exist is useful; its
            // view of which are TRUSTED is precisely what must not be believed.
            // Verification comes from the local crypto store.
            val verified = runCatching {
                client.encryption().verificationState() == VerificationState.VERIFIED
            }.getOrDefault(false)
            _devices.value = list.map {
                if (it.isCurrent) it.copy(isVerified = verified) else it
            }
        }
        Unit
    }

    /**
     * §8.4 — kept for the seam, and it explains itself rather than lying.
     *
     * The protocol has no "verify that device from here": see the note on
     * [RustVerification]. [deviceId] is accepted so the interface is unchanged
     * for callers that already have one in hand, and ignored, because the
     * request goes to all of this user's other sessions.
     */
    override suspend fun verifyDevice(deviceId: DeviceId): VerificationFlow = startVerification()

    /**
     * §8.4 — the verification controller for this session.
     *
     * One per client, created lazily and kept: the SDK has a single delegate
     * slot, so constructing a second one would silently unhook the first and
     * incoming requests would stop arriving at whoever was listening.
     */
    suspend fun startVerification(): RustVerification =
        verification ?: io { RustVerification(client.getSessionVerificationController()) }
            .also { verification = it }

    @Volatile private var verification: RustVerification? = null

    /** Recovery and key backup (§7.4), which the bindings do serve well. */
    fun recovery(): RustRecovery = RustRecovery(client)

    /**
     * §7.4.4 — create the cross-signing identity. Needs User-Interactive Auth,
     * so the user's password is required; that is the platform's rule, not a
     * design choice, and §8.6 hits the same requirement for device deletion.
     */
    suspend fun bootstrapCrossSigning(username: String, password: String): Boolean = io {
        // A null handle means the SDK sees no reset to perform — usually because
        // an identity already exists. That is a legitimate outcome and it is
        // returned rather than swallowed.
        //
        // The previous version was `?: return@io`, which made "an identity was
        // created", "one already existed" and "this silently did nothing"
        // indistinguishable to the caller. §7.4.4 exists because a missing
        // cross-signing identity fails LATER and invisibly — a new device reads
        // nothing, forever, with no error — so this is precisely the place not
        // to discard the answer.
        val handle = client.encryption().resetIdentity() ?: return@io false
        // `username` must be the full MXID. A localpart is rejected with
        // MissingLeadingSigil.
        handle.reset(AuthData.Password(AuthDataPasswordDetails(username, password)))
        true
    }

    companion object {
        /** §25.2 — Cloudflare's request cap, with headroom for the envelope. */
        const val MAX_UPLOAD_BYTES = 95L * 1024 * 1024

        /**
         * §11.7 step 1 — log in with username and password.
         *
         * @param sessionPath where the SDK's own store lives. §12.2: the SDK
         *   owns the crypto and state stores, not the application. Encryption at
         *   rest (§12.4) is applied to this directory.
         */
        suspend fun login(
            homeserverUrl: String,
            username: String,
            password: String,
            sessionPath: String,
            cachePath: String,
            deviceDisplayName: String,
            storeKey: ByteArray
        ): RustSocialSession {
            val client = ClientBuilder()
                .homeserverUrl(homeserverUrl)
                // §12.4 — the SDK's SQLite store is encrypted at rest with a
                // key held in the Keystore. sessionPaths() alone leaves it in
                // the clear inside the app sandbox.
                .sqliteStore(SqliteStoreBuilder(sessionPath, cachePath).key(storeKey))
                // §13.2.3 — mandatory. Without it the room list fails with
                // "Sliding sync version is missing" (found in phase 2).
                .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
                .build()

            client.login(username, password, deviceDisplayName, null)

            val sync = client.syncService()
                // §14.1 — without a timeline limit, sliding sync returns no
                // recent events per room and latestEvent() is always None, so
                // every inbox row reads "No messages yet" next to a conversation
                // that plainly has messages.
                .withRoomListTimelineLimit(10u)
                .finish()
            return RustSocialSession(client, sync, sync.roomListService())
        }

        /** Re-open a stored session without asking for the password again. */
        suspend fun restore(
            session: org.matrix.rustcomponents.sdk.Session,
            sessionPath: String,
            cachePath: String,
            storeKey: ByteArray
        ): RustSocialSession {
            val client = ClientBuilder()
                .homeserverUrl(session.homeserverUrl)
                .sqliteStore(SqliteStoreBuilder(sessionPath, cachePath).key(storeKey))
                .withSearchIndexStore(
                    java.io.File(sessionPath, "search").apply { mkdirs() }.absolutePath,
                    java.io.File(cachePath, "search").apply { mkdirs() }.absolutePath
                )
                .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
                .build()
            client.restoreSession(session)
            val sync = client.syncService().withRoomListTimelineLimit(10u).finish()
            return RustSocialSession(client, sync, sync.roomListService())
        }
    }
}


/**
 * ULong bytes to Long, defensively.
 *
 * Coerced rather than converted so a garbage value shows as a huge number
 * instead of a negative one: a store size rendering as "-2.1 GB" reads as a bug
 * in the app rather than in the store, and sends the user to the wrong place.
 */
private fun ULong?.bytes(): Long = (this ?: 0uL).toLong().coerceAtLeast(0)
