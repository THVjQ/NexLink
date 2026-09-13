package com.nexlink.social

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexlink.social.core.rust.RustSocialSession
import com.nexlink.social.core.rust.RustVerification
import com.nexlink.social.core.session.VerificationStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * §11.7 step 5 / §8.4 — second-device verification, end to end.
 *
 * ## Why this is two SDK clients and not two apps
 *
 * The first attempt used the debug and release builds of this app side by side
 * on one phone. It got as far as `m.key.verification.ready` and `.start` on the
 * wire and then stalled every time, because **only one app can be in the
 * foreground**: whichever side was backgrounded stopped syncing, and SAS needs
 * several round trips with both ends responsive. §33.3.2 records that in full.
 *
 * Two clients inside one instrumentation process do not have that problem —
 * both sync for the whole test. That is a better rig, not a weaker one: it
 * removes the only variable that was failing while testing exactly the code
 * that ships, `RustVerification` over the SDK's `SessionVerificationController`.
 *
 * What it does NOT cover is `VerifyActivity`'s rendering, which was checked by
 * hand.
 *
 * ## Running it
 *
 * Needs a live account on the homeserver:
 * ```
 * ./gradlew :social:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.nexlink.social.DeviceVerificationTest \
 *   -Pandroid.testInstrumentationRunnerArguments.user=<localpart> \
 *   -Pandroid.testInstrumentationRunnerArguments.pass=<password>
 * ```
 */
@RunWith(AndroidJUnit4::class)
class DeviceVerificationTest {

    private val args = InstrumentationRegistry.getArguments()
    private val user = args.getString("user") ?: error("pass -e user <localpart>")
    private val pass = args.getString("pass") ?: error("pass -e pass <password>")
    private val hs = args.getString("hs") ?: "https://nexlink.thvjq.com.au"

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun signIn(tag: String): RustSocialSession {
        val dir = File(ctx.cacheDir, "verify-$tag").apply { deleteRecursively(); mkdirs() }
        val cache = File(ctx.cacheDir, "verify-$tag-cache").apply { deleteRecursively(); mkdirs() }
        val session = RustSocialSession.login(
            homeserverUrl = hs,
            username = user,
            password = pass,
            sessionPath = dir.absolutePath,
            cachePath = cache.absolutePath,
            deviceDisplayName = "verify-test-$tag",
            // Not the Keystore key: these stores are created and destroyed by
            // the test and must not collide with the real app's.
            storeKey = ByteArray(32) { (it + tag.hashCode()).toByte() }
        )
        session.startSync()
        return session
    }

    /**
     * The whole flow: B asks, A accepts, both compare, both approve.
     *
     * The emoji equality assertion is the real content of this test. SAS's
     * entire security property is that the two devices independently derive the
     * **same** short string from a key agreement an attacker cannot influence;
     * if the two sides showed different emoji, a user comparing them would
     * reject a legitimate device, and if they showed a *constant* the check
     * would be theatre.
     */
    @Test
    fun twoDevicesVerifyEachOtherWithMatchingEmoji() = runBlocking {
        val a = withContext(Dispatchers.IO) { signIn("a") }
        val b = withContext(Dispatchers.IO) { signIn("b") }
        try {
            // **Cross-signing must exist before anything can be verified.**
            //
            // Without it `requestDeviceVerification()` fails with
            // `ClientException$Generic: Failed retrieving user identity`, which
            // names neither cross-signing nor the fix. §7.4.4 already records
            // that recovery setup must bootstrap the cross-signing identity and
            // not only the key backup; this is the same requirement arriving
            // from the other direction — an account that never set up recovery
            // cannot verify a device at all.
            //
            // Accounts made with `register_new_matrix_user` have no identity,
            // so the test creates one rather than assuming the fixture has it.
            withContext(Dispatchers.IO) {
                runCatching { a.bootstrapCrossSigning("@$user:nexlink.thvjq.com.au", pass) }
                    .onFailure { error("could not bootstrap cross-signing: ${it.message}") }
            }

            val va = a.startVerification()
            val vb = b.startVerification()

            // B is the new device asking to be verified — but not until it has
            // SYNCED the identity A just created.
            //
            // `requestDeviceVerification()` fails with "Failed retrieving user
            // identity" until the requesting client has seen the cross-signing
            // keys, and it fails the same way whether the identity does not
            // exist or merely has not arrived yet. Those are very different
            // problems with one message, so this retries rather than treating
            // the first failure as fatal. Confirmed on the server that the keys
            // existed while the client was still reporting them missing.
            val requested = withTimeoutOrNull(120_000) {
                var ok = false
                while (!ok) {
                    ok = runCatching { vb.request() }.isSuccess
                    if (!ok) kotlinx.coroutines.delay(3_000)
                }
                true
            }
            assertNotNull(
                "device B could never start verification — the cross-signing " +
                    "identity never reached it",
                requested
            )

            // A sees the request. This is the step that silently did nothing
            // before the delegate was moved to startSync() — §33.3.2.
            val incoming = withTimeoutOrNull(60_000) {
                va.incoming.first { it != null }
            }
            assertNotNull("device A never saw the verification request", incoming)

            va.accept()

            val emojiA = withTimeoutOrNull(90_000) {
                va.steps.first { it is VerificationStep.ShowEmoji } as VerificationStep.ShowEmoji
            }
            val emojiB = withTimeoutOrNull(90_000) {
                vb.steps.first { it is VerificationStep.ShowEmoji } as VerificationStep.ShowEmoji
            }
            assertNotNull("device A never received SAS emoji", emojiA)
            assertNotNull("device B never received SAS emoji", emojiB)

            assertEquals("SAS emoji count", 7, emojiA!!.emoji.size)
            assertEquals(
                "THE TWO DEVICES SHOWED DIFFERENT EMOJI — a user comparing them " +
                    "would reject a legitimate device",
                emojiA.emoji.map { it.first },
                emojiB!!.emoji.map { it.first }
            )

            // Both sides must approve. A one-sided confirm leaves the other
            // waiting, which is the point of a mutual check.
            va.confirmMatch()
            vb.confirmMatch()

            val doneA = withTimeoutOrNull(90_000) {
                va.steps.first { it is VerificationStep.Verified || it is VerificationStep.Cancelled }
            }
            val doneB = withTimeoutOrNull(90_000) {
                vb.steps.first { it is VerificationStep.Verified || it is VerificationStep.Cancelled }
            }
            assertTrue("device A did not finish: $doneA", doneA is VerificationStep.Verified)
            assertTrue("device B did not finish: $doneB", doneB is VerificationStep.Verified)
        } finally {
            withContext(Dispatchers.IO) {
                // stopSync, not signOut: signing out would invalidate the
                // account's other sessions' view of these devices, and the
                // point of the test is that they verified each other.
                runCatching { a.stopSync() }
                runCatching { b.stopSync() }
            }
        }
    }
}
