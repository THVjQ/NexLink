package com.nexlink.social.core.rust

import com.nexlink.social.core.session.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.matrix.rustcomponents.sdk.AuthData
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.RoomPreset
import org.matrix.rustcomponents.sdk.RoomVisibility
import org.matrix.rustcomponents.sdk.AuthDataPasswordDetails
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.RoomListService
import org.matrix.rustcomponents.sdk.SlidingSyncVersionBuilder
import org.matrix.rustcomponents.sdk.SyncService

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

    suspend fun startSync() {
        syncService.start()
        // §13.2 — sliding sync delivers the room list asynchronously. Polling is
        // a first cut: RoomListService exposes a listener, and moving to it is a
        // phase-3 refinement rather than a rewrite (§14.9).
        scope.launch {
            while (isActive) {
                runCatching { refreshRooms() }
                delay(2000)
            }
        }
    }
    suspend fun stopSync() {
        scope.coroutineContext.cancelChildren()
        syncService.stop()
    }

    /** The last published state, for callers that need it synchronously. */
    fun currentState(): SessionState = _state.value

    /** §12.4 — persist the credentials, encrypted at rest. */
    fun persistTo(store: com.nexlink.social.core.SessionStore) {
        store.save(client.session())
    }

    private val timelines = mutableMapOf<String, RustTimeline>()

    override suspend fun timeline(roomId: RoomId): Timeline {
        synchronized(timelines) { timelines[roomId.value] }?.let { return it }
        val room = roomListService.room(roomId.value)
        val t = RustTimeline(room.timeline())
        t.start()
        synchronized(timelines) { timelines[roomId.value] = t }
        return t
    }

    /** §11.7 step 3 — send an encrypted text message. */
    override suspend fun send(roomId: RoomId, body: MessageBody): Result<EventId> =
        (timeline(roomId) as RustTimeline).send(body)

    override suspend fun react(eventId: EventId, emoji: String): Result<Unit> =
        Result.failure(NotImplementedError("§14.4 — needs the room, not just the event id"))

    override suspend fun findUsers(query: String): Result<List<UserSummary>> = runCatching {
        // §6.6 — the search is server-side over this homeserver's directory
        // only. Federation is off (§2.6), so there is nowhere else to look.
        client.searchUsers(query, 20uL).results.map {
            UserSummary(UserId(it.userId), it.displayName, it.avatarUrl)
        }
    }

    override suspend fun startDirectMessage(userId: UserId): Result<RoomId> = runCatching {
        // Reuse an existing DM rather than creating a second one. Two rooms with
        // the same person is confusing and splits history for no reason.
        client.getDmRoom(userId.value)?.let { return@runCatching RoomId(it.id()) }

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

    /** Poll the room list into the flow. §13.2.3's sliding sync drives the data. */
    suspend fun refreshRooms() {
        val summaries = client.rooms().mapNotNull { room ->
            runCatching {
                val info = room.roomInfo()
                RoomSummary(
                    id = RoomId(room.id()),
                    title = info.displayName ?: room.id(),
                    avatarUrl = info.avatarUrl,
                    lastMessagePreview = null,
                    lastMessageAt = 0L,
                    unreadCount = info.numUnreadMessages.toInt(),
                    isGroup = !info.isDirect,
                    isMuted = false
                )
            }.getOrNull()
        }
        _rooms.value = summaries
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
    override fun devices(): Flow<List<DeviceInfo>> = MutableStateFlow(emptyList<DeviceInfo>()).asStateFlow()

    override suspend fun verifyDevice(deviceId: DeviceId): VerificationFlow =
        throw NotImplementedError("phase 2 step 5 — §8.4, needs a second device")

    /** Recovery and key backup (§7.4), which the bindings do serve well. */
    fun recovery(): RustRecovery = RustRecovery(client)

    /**
     * §7.4.4 — create the cross-signing identity. Needs User-Interactive Auth,
     * so the user's password is required; that is the platform's rule, not a
     * design choice, and §8.6 hits the same requirement for device deletion.
     */
    suspend fun bootstrapCrossSigning(username: String, password: String) {
        val handle = client.encryption().resetIdentity() ?: return
        handle.reset(AuthData.Password(AuthDataPasswordDetails(username, password)))
    }

    companion object {
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
            deviceDisplayName: String
        ): RustSocialSession {
            val client = ClientBuilder()
                .homeserverUrl(homeserverUrl)
                .sessionPaths(sessionPath, cachePath)
                // §13.2.3 — mandatory. Without it the room list fails with
                // "Sliding sync version is missing" (found in phase 2).
                .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
                .build()

            client.login(username, password, deviceDisplayName, null)

            val sync = client.syncService().finish()
            return RustSocialSession(client, sync, sync.roomListService())
        }

        /** Re-open a stored session without asking for the password again. */
        suspend fun restore(
            session: org.matrix.rustcomponents.sdk.Session,
            sessionPath: String,
            cachePath: String
        ): RustSocialSession {
            val client = ClientBuilder()
                .homeserverUrl(session.homeserverUrl)
                .sessionPaths(sessionPath, cachePath)
                .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
                .build()
            client.restoreSession(session)
            val sync = client.syncService().finish()
            return RustSocialSession(client, sync, sync.roomListService())
        }
    }
}
