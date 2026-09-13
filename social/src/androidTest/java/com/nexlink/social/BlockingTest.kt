package com.nexlink.social

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexlink.social.core.rust.RustSocialSession
import com.nexlink.social.core.session.UserId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * §31.3.1 — blocking, against the live homeserver.
 *
 * The reason this is an instrumented test rather than a unit test: **the block
 * is enforced by the server**, not by the client. Matrix's ignore list lives in
 * account data, the homeserver applies it, and that is the property worth
 * having — it survives a reinstall and holds on every device. A test against a
 * fake would confirm only that a flow emitted.
 *
 *   ./gradlew :social:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.nexlink.social.BlockingTest \
 *     -Pandroid.testInstrumentationRunnerArguments.user=<localpart> \
 *     -Pandroid.testInstrumentationRunnerArguments.pass=<password>
 */
@RunWith(AndroidJUnit4::class)
class BlockingTest {

    private val args = InstrumentationRegistry.getArguments()
    private val user = args.getString("user") ?: error("pass -e user")
    private val pass = args.getString("pass") ?: error("pass -e pass")
    private val target = args.getString("target") ?: "@someone:nexlink.thvjq.com.au"
    private val hs = args.getString("hs") ?: "https://nexlink.thvjq.com.au"

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun signIn(tag: String): RustSocialSession {
        val dir = File(ctx.cacheDir, "blk-$tag").apply { deleteRecursively(); mkdirs() }
        val cache = File(ctx.cacheDir, "blk-$tag-c").apply { deleteRecursively(); mkdirs() }
        val s = RustSocialSession.login(hs, user, pass, dir.absolutePath, cache.absolutePath,
            "blocking-test-$tag", ByteArray(32) { (it + 7).toByte() })
        s.startSync()
        return s
    }

    /**
     * Block, confirm the server has it, unblock, confirm it is gone.
     *
     * The round trip matters as much as the block: §31.3.1 puts this under the
     * user's control, and a block that cannot be undone is a trap rather than a
     * control.
     */
    @Test
    fun blockingRoundTripsThroughTheServer() = runBlocking {
        val s = withContext(Dispatchers.IO) { signIn("a") }
        val who = UserId(target)
        try {
            s.blockUser(who).getOrThrow()

            val afterBlock = withTimeoutOrNull(45_000) {
                s.blockedUsers().first { who in it }
            }
            assertNotNull("the server never reported the block", afterBlock)
            assertTrue(who in afterBlock!!)

            s.unblockUser(who).getOrThrow()
            val afterUnblock = withTimeoutOrNull(45_000) {
                s.blockedUsers().first { who !in it }
            }
            assertNotNull("the block could not be undone — that is a trap, not a control",
                afterUnblock)
        } finally {
            withContext(Dispatchers.IO) {
                runCatching { s.unblockUser(who) }
                runCatching { s.stopSync() }
            }
        }
    }
}
