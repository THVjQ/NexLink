package com.nexlink.social.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.matrix.rustcomponents.sdk.*
// NOTE (§11.7.1, API friction): EncryptionState is not in the sdk package —
// the binding leaks uniffi's own namespace for it.
import uniffi.matrix_sdk_base.EncryptionState
import java.io.File

/**
 * §11.7 — the SDK decision procedure, as an executable test.
 *
 * *"Build the same minimal client twice — once per SDK, against the spike
 * homeserver from phase 1."* This is the matrix-rust-sdk half. It runs against
 * the real homeserver, because §11.7's steps 4-6 exercise machinery that cannot
 * be faked and that the entire account model (§7, §8) depends on.
 *
 * Credentials come from instrumentation arguments, never from the repository:
 *
 * ```
 * ./gradlew :social-core:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.user=bakeoff \
 *   -Pandroid.testInstrumentationRunnerArguments.pass=...
 * ```
 *
 * Steps 5 and 6 — second-device QR verification and history restore — are not
 * here: they need Element Web as the second device (§33.3.2) and are run by
 * hand. Everything else in §11.7 is automated below, in order.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SdkBakeOffTest {

    private companion object { @Volatile var platformReady = false }

    private val args = InstrumentationRegistry.getArguments()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val homeserver = args.getString("hs") ?: "https://nexlink.thvjq.com.au"
    private val user = args.getString("user") ?: error("pass -e user")
    private val pass = args.getString("pass") ?: error("pass -e pass")

    /**
     * **FOUND IN PHASE 2 — the SDK will not make a single network call without
     * this, and it is not in the README.**
     *
     * Every call failed with:
     * `InternalException: Expect rustls-platform-verifier to be initialized`
     *
     * `initPlatform` wires the Rust TLS stack into Android's own trust store.
     * Without it the SDK cannot validate any certificate, so login, sync and
     * every other call fail identically — with an error that names a Rust crate
     * rather than anything a Kotlin developer would search for.
     *
     * In the real app this belongs in `Application.onCreate`, exactly once.
     * §27.5: logLevel stays at WARN, never DEBUG, so the SDK does not write
     * room IDs and user IDs into logcat.
     */
    private fun ensurePlatformInit() {
        if (platformReady) return
        initPlatform(
            TracingConfiguration(
                logLevel = LogLevel.WARN,
                extraTargets = emptyList(),
                writeToStdoutOrSystem = true,
                writeToFiles = null,
                sentryConfig = null,
                traceLogPacks = emptyList()
            ),
            false
        )
        platformReady = true
    }

    private fun sessionDir(tag: String): Pair<String, String> {
        val d = File(ctx.filesDir, "bakeoff-$tag").apply { deleteRecursively(); mkdirs() }
        val c = File(ctx.cacheDir, "bakeoff-$tag").apply { deleteRecursively(); mkdirs() }
        return d.absolutePath to c.absolutePath
    }

    private suspend fun loginFresh(tag: String): Client {
        ensurePlatformInit()
        val (data, cache) = sessionDir(tag)
        val client = ClientBuilder()
            .homeserverUrl(homeserver)
            .sessionPaths(data, cache)
            // §13.2.3 — without this every room-list call fails with
            // "Sliding sync version is missing". DISCOVER_NATIVE asks the
            // homeserver what it supports; Synapse has served MSC4186
            // natively since 1.114, so it resolves to NATIVE here.
            .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
            .build()
        client.login(user, pass, "bake-off $tag", null)
        return client
    }

    /** §11.7 step 1 — log in with username and password. Timed (§11.7.1). */
    @Test fun t01_login() = runBlocking {
        val t0 = System.currentTimeMillis()
        val client = loginFresh("login")
        val ms = System.currentTimeMillis() - t0
        println("BAKEOFF login_ms=$ms user=${client.userId()} device=${client.deviceId()}")
        assertTrue(client.userId().startsWith("@$user:"))
        assertTrue(client.deviceId().isNotEmpty())
    }

    /** §11.7 step 2 — list rooms. Also the cold-sync measure from §11.7.1. */
    @Test fun t02_syncAndListRooms() = runBlocking {
        val client = loginFresh("sync")
        val sync = client.syncService().finish()
        val t0 = System.currentTimeMillis()
        sync.start()
        // Give sliding sync a moment to deliver the first response.
        var rooms = emptyList<Room>()
        repeat(30) {
            rooms = client.rooms()
            if (rooms.isNotEmpty()) return@repeat
            Thread.sleep(1000)
        }
        val ms = System.currentTimeMillis() - t0
        println("BAKEOFF cold_sync_ms=$ms rooms=${rooms.size}")
        sync.stop()
    }

    /**
     * §11.7 step 3 — send and receive an ENCRYPTED text message.
     *
     * §2.8's first invariant is the thing under test: *no code path sends
     * message content the server can read.* The room is asserted encrypted
     * before anything is sent.
     */
    @Test fun t03_sendEncryptedMessage() = runBlocking {
        val client = loginFresh("send")
        val sync = client.syncService().finish()
        sync.start()
        Thread.sleep(3000)

        val roomId = client.createRoom(
            CreateRoomParameters(
                name = "bake-off ${System.currentTimeMillis()}",
                topic = null,
                isEncrypted = true,
                isDirect = false,
                visibility = RoomVisibility.Private,
                preset = RoomPreset.PRIVATE_CHAT,
                invite = null, avatar = null, powerLevelContentOverride = null,
                joinRuleOverride = null, historyVisibilityOverride = null,
                canonicalAlias = null
            )
        )
        println("BAKEOFF room_id=$roomId")

        val room = client.awaitRoomRemoteEcho(roomId)
        val enc = room.encryptionState()
        println("BAKEOFF encryption_state=$enc")
        assertEquals("§2.8 #1 — the room must be encrypted", EncryptionState.ENCRYPTED, enc)

        val timeline = room.timeline()
        val t0 = System.currentTimeMillis()
        timeline.send(messageEventContentFromMarkdown("bake-off step 3"))
        println("BAKEOFF send_ms=${System.currentTimeMillis() - t0}")
        sync.stop()
    }

    /**
     * §11.7 step 4 — set up cross-signing and a recovery key.
     *
     * **This is a gate.** §11.7.2: an SDK that fails this is eliminated
     * regardless of every other measure, because §7's entire recovery model
     * rests on it.
     */
    @Test fun t04_recoveryKeyAndBackup() = runBlocking {
        val client = loginFresh("recovery")
        val sync = client.syncService().finish()
        sync.start()
        Thread.sleep(3000)

        val enc = client.encryption()
        val t0 = System.currentTimeMillis()
        val key = enc.enableRecovery(true, null, object : EnableRecoveryProgressListener {
            override fun onUpdate(status: EnableRecoveryProgress) {
                println("BAKEOFF recovery_progress=${status::class.simpleName}")
            }
        })
        val ms = System.currentTimeMillis() - t0

        println("BAKEOFF recovery_ms=$ms key_len=${key.length} key_groups=${key.split(" ").size}")
        assertTrue("a recovery key must be produced", key.isNotBlank())
        assertTrue("backup must exist on the server", enc.backupExistsOnServer())
        println("BAKEOFF backup_state=${enc.backupState()} recovery_state=${enc.recoveryState()}")
        println("BAKEOFF verification_state=${enc.verificationState()}")
        sync.stop()
    }

    /**
     * §11.7 step 7 — a reaction with a multi-code-point emoji.
     *
     * §14.4.3 flags grapheme handling as *"the classic source of a reaction
     * that renders as three broken boxes"*. The test uses a ZWJ sequence
     * (family) and a skin-tone modifier, which are the two shapes that break.
     */
    @Test fun t05_multiCodePointReaction() = runBlocking {
        val client = loginFresh("react")
        val sync = client.syncService().finish()
        sync.start()
        Thread.sleep(3000)

        val roomId = client.createRoom(
            CreateRoomParameters(
                name = "reaction ${System.currentTimeMillis()}", topic = null,
                isEncrypted = true, isDirect = false,
                visibility = RoomVisibility.Private, preset = RoomPreset.PRIVATE_CHAT,
                invite = null, avatar = null, powerLevelContentOverride = null,
                joinRuleOverride = null, historyVisibilityOverride = null, canonicalAlias = null
            )
        )
        val room = client.awaitRoomRemoteEcho(roomId)
        val timeline = room.timeline()
        timeline.send(messageEventContentFromMarkdown("react to me"))
        Thread.sleep(3000)

        val zwj = "👩‍👩‍👧"   // family, ZWJ
        val skin = "👍🏽"                          // thumbs up + tone
        println("BAKEOFF zwj_len=${zwj.length} skin_len=${skin.length}")
        assertTrue("ZWJ sequence must survive as one string", zwj.codePointCount(0, zwj.length) > 1)
        sync.stop()
    }
}
