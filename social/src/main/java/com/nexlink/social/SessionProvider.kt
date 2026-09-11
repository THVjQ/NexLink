package com.nexlink.social

import android.content.Context
import com.nexlink.social.core.SocialSessionManager

/**
 * The one place that hands out the live session — §11.6.
 *
 * Phase 0 returned a fake here. Phase 2 resolved §11 and phase 3 replaced it
 * with the real one, and **this file was the only change required outside
 * `:social-core`.** That is the seam working exactly as §11.6 intended.
 */
object SessionProvider {
    fun manager(context: Context): SocialSessionManager =
        (context.applicationContext as SocialApplication).sessions
}
