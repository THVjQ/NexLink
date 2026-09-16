package com.nexlink.social.core

import java.io.File

/**
 * Removing the SDK's store so that nothing can find it afterwards — §12.4.7.
 *
 * Separated from [SocialSessionManager] because the rule it implements is worth
 * testing and has nothing to do with Android: *after this returns, the live
 * path must not resolve to the old store*, whether or not the bytes are gone.
 *
 * The distinction is the whole point. `deleteRecursively()` walks a tree and
 * returns `false` if any single file resists; wrapped in a `runCatching` that
 * ignores the result — as it was — a **partial** delete reports success and
 * leaves a crypto store behind at exactly the path the next sign-in will open.
 * The SDK then refuses it:
 *
 * ```
 * the account in the store doesn't match the account in the constructor:
 * expected …:JRWDBPLIJJ, got …:DIDOUYBDVB
 * ```
 *
 * and the app can no longer sign in at all, with no way out from inside it.
 *
 * A rename cannot half-happen. Do that first, delete second, and a failed
 * delete costs disk rather than costing the user their account.
 */
object StoreWipe {

    const val DISCARD_SUFFIX = ".discarded-"

    /**
     * @return true when every directory is gone from its live path. A false
     *   here means something is very wrong (an unwritable parent), not merely
     *   that a file survived — and the caller can say so rather than guess.
     */
    fun wipe(dirs: List<File>, nowNanos: Long = System.nanoTime()): Boolean {
        var clean = true
        for (dir in dirs) {
            if (!dir.exists()) continue
            val aside = File(dir.parentFile, "${dir.name}$DISCARD_SUFFIX$nowNanos")
            val moved = runCatching { dir.renameTo(aside) }.getOrDefault(false)
            runCatching { (if (moved) aside else dir).deleteRecursively() }
            if (dir.exists()) clean = false
        }
        return clean
    }

    /**
     * Anything an earlier [wipe] could not finish. Cheap, and without it a
     * failing delete accumulates one copy of the crypto store per sign-out.
     */
    fun sweep(parents: List<File>) {
        for (parent in parents) {
            runCatching {
                parent.listFiles { f -> f.name.contains(DISCARD_SUFFIX) }
                    ?.forEach { it.deleteRecursively() }
            }
        }
    }
}
