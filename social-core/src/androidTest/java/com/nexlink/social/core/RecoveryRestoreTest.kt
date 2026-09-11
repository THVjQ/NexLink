package com.nexlink.social.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.matrix.rustcomponents.sdk.*
import uniffi.matrix_sdk_base.EncryptionState
import java.io.File
import java.util.Collections

/**
 * §11.7 step 6 — restore history on a NEW device from key backup.
 *
 * §11.7.2 calls steps 4-6 the gate: *"an SDK that makes steps 1-3 pleasant and
 * step 6 impossible is the wrong SDK, and only building it reveals that."*
 *
 * This is also the test that validates §7 as a **product design**, not just as
 * an SDK feature. The no-email recovery path (§7.1) promises exactly one thing:
 * that a recovery key, and nothing else, brings your history back on a new
 * device. If that promise does not hold, §7.3's warnings, §9.6.1's screen 3 and
 * §32.5's support script are all describing something that does not happen.
 *
 * The second device here is a second SDK session with its own store, which is a
 * genuinely separate Matrix device. §11.7 step 5 (QR verification between two
 * screens) is the part that still needs a person.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryRestoreTest {

    private val args = InstrumentationRegistry.getArguments()
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val homeserver = args.getString("hs") ?: "https://nexlink.thvjq.com.au"
    private val user = args.getString("user") ?: error("pass -e user")
    private val pass = args.getString("pass") ?: error("pass -e pass")

    private companion object { @Volatile var platformReady = false }

    private fun initOnce() {
        if (platformReady) return
        initPlatform(
            TracingConfiguration(
                logLevel = LogLevel.WARN, extraTargets = emptyList(),
                writeToStdoutOrSystem = true, writeToFiles = null,
                sentryConfig = null, traceLogPacks = emptyList()
            ), false
        )
        platformReady = true
    }

    private suspend fun device(tag: String): Client {
        initOnce()
        val d = File(ctx.filesDir, "dev-$tag").apply { deleteRecursively(); mkdirs() }
        val c = File(ctx.cacheDir, "dev-$tag").apply { deleteRecursively(); mkdirs() }
        val client = ClientBuilder()
            .homeserverUrl(homeserver)
            .sessionPaths(d.absolutePath, c.absolutePath)
            .slidingSyncVersionBuilder(SlidingSyncVersionBuilder.DISCOVER_NATIVE)
            .build()
        client.login(user, pass, "restore-test $tag", null)
        return client
    }

    /** Collect timeline items through the listener — there is no getItems(). */
    private class Collector : TimelineListener {
        val items: MutableList<TimelineItem> = Collections.synchronizedList(mutableListOf())
        override fun onUpdate(diff: List<TimelineDiff>) {
            diff.forEach { d ->
                when (d) {
                    is TimelineDiff.Append -> items.addAll(d.values)
                    is TimelineDiff.Reset -> { items.clear(); items.addAll(d.values) }
                    is TimelineDiff.PushBack -> items.add(d.value)
                    is TimelineDiff.PushFront -> items.add(0, d.value)
                    is TimelineDiff.Insert -> items.add(d.value)
                    is TimelineDiff.Set -> items.add(d.value)
                    else -> Unit
                }
            }
        }
    }

    private fun TimelineItem.bodyOrUtd(): String? {
        val ev = asEvent() ?: return null
        val content = ev.content
        if (content !is TimelineItemContent.MsgLike) return null
        return when (val kind = content.content.kind) {
            is MsgLikeKind.UnableToDecrypt -> "<<UTD>>"
            is MsgLikeKind.Message -> (kind.content.msgType as? MessageType.Text)?.content?.body
            else -> null
        }
    }

    @Test fun recoveryKeyRestoresHistoryOnANewDevice() = runBlocking {
        val secret = "recovery probe ${System.currentTimeMillis()}"

        // ── Device A: enable recovery, send an encrypted message ─────────────
        val a = device("A")
        val aSync = a.syncService().finish(); aSync.start(); Thread.sleep(4000)

        // enableRecovery throws BackupExistsOnServer if this account already has
        // one — which it will on any re-run. resetRecoveryKey() supersedes the
        // old key and is the same call §30.4.2 specifies for a disclosed key,
        // so exercising it here is useful rather than a workaround.
        // §11.7 step 4 is "set up cross-signing AND a recovery key", and the
        // AND matters. A fresh account has NO cross-signing identity, so
        // enableRecovery() alone puts the backup key into 4S but no
        // cross-signing secrets. A second device then restores the backup,
        // stays UNVERIFIED, and never decrypts history — recoveryState sticks
        // at INCOMPLETE. Observed exactly that before this was added (§7.4.4).
        println("RESTORE bootstrap_identity: verif=${a.encryption().verificationState()}")
        runCatching {
            val handle = a.encryption().resetIdentity()
            if (handle != null) {
                println("RESTORE identity_auth_type=${handle.authType()}")
                handle.reset(AuthData.Password(AuthDataPasswordDetails(user, pass)))
                println("RESTORE identity_reset=done verif=${a.encryption().verificationState()}")
            } else println("RESTORE identity_reset=handle_null")
        }.onFailure { println("RESTORE identity_reset_failed=${it::class.simpleName}: ${it.message}") }

        val recoveryKey = try {
            a.encryption().enableRecovery(
                true, null,
                object : EnableRecoveryProgressListener {
                    override fun onUpdate(status: EnableRecoveryProgress) {
                        println("RESTORE progressA=${status::class.simpleName}")
                    }
                }
            )
        } catch (e: RecoveryException.BackupExistsOnServer) {
            println("RESTORE backup_already_existed=true -> resetRecoveryKey (§30.4.2)")
            a.encryption().resetRecoveryKey()
                ?: error("resetRecoveryKey returned no key")
        }
        println("RESTORE recovery_key_len=${recoveryKey.length}")
        assertTrue("a recovery key must be issued", recoveryKey.isNotBlank())

        val roomId = a.createRoom(
            CreateRoomParameters(
                name = "restore ${System.currentTimeMillis()}", topic = null,
                isEncrypted = true, isDirect = false,
                visibility = RoomVisibility.Private, preset = RoomPreset.PRIVATE_CHAT,
                invite = null, avatar = null, powerLevelContentOverride = null,
                joinRuleOverride = null, historyVisibilityOverride = null, canonicalAlias = null
            )
        )
        val roomA = a.awaitRoomRemoteEcho(roomId)
        // §11.7.4 — encryptionState() reads SYNCED state, so give it a moment
        // rather than gating on it immediately.
        repeat(20) { if (roomA.encryptionState() == EncryptionState.ENCRYPTED) return@repeat; Thread.sleep(1000) }
        println("RESTORE roomA_encryption=${roomA.encryptionState()}")
        assertEquals(EncryptionState.ENCRYPTED, roomA.encryptionState())

        roomA.timeline().send(messageEventContentFromMarkdown(secret))
        println("RESTORE sent=$secret room=$roomId")

        // §11.7.4 — DO NOT sleep and hope. The megolm key for this message has
        // to reach the key backup before device B can possibly restore it, and
        // the upload runs on device A's sync loop. A first attempt slept 10s and
        // then stopped sync; device B saw <<UTD>> because the key had never been
        // uploaded. The SDK has a dedicated wait for exactly this.
        // §8.5.2 — and this is the subtle one. waitForBackupUploadSteadyState
        // returns Done when nothing is *currently* queued, which immediately
        // after send() means the new megolm session has not been queued YET.
        // A first attempt trusted a single Done and the backup was still empty
        // server-side (count=0) — so device B legitimately could not decrypt.
        //
        // The product consequence is larger than the test: a client that tells
        // the user "your history is backed up" on the strength of one steady
        // state is lying to them. §8.5.2's backup health must be based on the
        // backup actually containing keys, not on a transient Done.
        repeat(4) { round ->
            Thread.sleep(5000)
            a.encryption().waitForBackupUploadSteadyState(
                object : BackupSteadyStateListener {
                    override fun onUpdate(status: BackupUploadState) {
                        println("RESTORE upload[$round]=${status::class.simpleName}")
                    }
                }
            )
        }
        assertTrue("backup must exist before we test restoring from it",
            a.encryption().backupExistsOnServer())
        println("RESTORE backup_state=${a.encryption().backupState()}")
        aSync.stop()

        // ── Device B: a brand new device. Should NOT read history yet ────────
        val b = device("B")
        println("RESTORE deviceA=${a.deviceId()} deviceB=${b.deviceId()}")
        assertNotEquals("device B must be a genuinely different device",
            a.deviceId(), b.deviceId())

        val bSync = b.syncService().finish(); bSync.start(); Thread.sleep(6000)
        println("RESTORE recovery_state_before=${b.encryption().recoveryState()}")

        val roomB = b.awaitRoomRemoteEcho(roomId)
        val before = Collector()
        val h1 = roomB.timeline().addListener(before)
        Thread.sleep(6000)
        val beforeBodies = before.items.mapNotNull { it.bodyOrUtd() }
        println("RESTORE before_restore=$beforeBodies")

        // ── The actual test: the recovery key, and nothing else ──────────────
        val encB = b.encryption()
        println("RESTORE B_backup_before=${encB.backupState()} verif_before=${encB.verificationState()}")
        println("RESTORE B_backup_on_server=${runCatching { encB.backupExistsOnServer() }.getOrNull()}")

        val recoverResult = runCatching { encB.recover(recoveryKey) }
        println("RESTORE recover_threw=${recoverResult.exceptionOrNull()?.let { it::class.simpleName + ": " + it.message }}")
        println("RESTORE recovery_state_after=${encB.recoveryState()}")
        println("RESTORE B_backup_after=${encB.backupState()} verif_after=${encB.verificationState()}")

        runCatching { encB.waitForE2eeInitializationTasks() }

        // Poll rather than sleep once: key download and re-decryption are async,
        // and a fixed sleep tells you nothing about WHY it did not happen.
        repeat(12) { i ->
            Thread.sleep(5000)
            val c = Collector()
            val h = roomB.timeline().addListener(c)
            Thread.sleep(2500)
            val bodies = c.items.mapNotNull { it.bodyOrUtd() }
            println("RESTORE poll[$i] backup=${encB.backupState()} recovery=${encB.recoveryState()} bodies=$bodies")
            h.cancel()
            if (bodies.contains(secret)) return@repeat
        }

        val after = Collector()
        val h2 = roomB.timeline().addListener(after)
        Thread.sleep(8000)
        val afterBodies = after.items.mapNotNull { it.bodyOrUtd() }
        println("RESTORE after_restore=$afterBodies")

        h1.cancel(); h2.cancel(); bSync.stop()

        assertTrue(
            "§7.1's promise: the recovery key alone must bring history back. " +
            "Device B saw: $afterBodies",
            afterBodies.contains(secret)
        )
        println("RESTORE RESULT=PASS")
    }
}
