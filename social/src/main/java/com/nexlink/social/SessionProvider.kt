package com.nexlink.social

import com.nexlink.social.core.fake.FakeSocialSession
import com.nexlink.social.core.session.SocialSession

/**
 * The one place that decides which [SocialSession] implementation the app runs
 * against — §11.6.
 *
 * In phase 0 it is always the fake: §11 is unresolved and §33.3 resolves it.
 * When a real implementation lands in `:social-core`, **this file is the only
 * one that changes.** That is the seam working; if selecting the real session
 * turns out to require edits elsewhere, an SDK type has leaked across the
 * module boundary and §11.6 has been violated.
 */
object SessionProvider {

    private val fake by lazy { FakeSocialSession.withSampleData() }

    fun session(): SocialSession = fake
}
