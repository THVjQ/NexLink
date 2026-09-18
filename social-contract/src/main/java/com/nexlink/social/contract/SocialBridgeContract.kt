package com.nexlink.social.contract

/**
 * Constants both apps must agree on — §16.3.1, §16.6.
 *
 * Kept in one place because every one of these is a string that, typo'd on one
 * side, produces a binding that silently never connects.
 */
object SocialBridgeContract {

    /** The signature-level permission, declared in this module's manifest (§16.3.1). */
    const val PERMISSION = "com.thvjq.nexlink.permission.SOCIAL_BRIDGE"

    /** Action NexLink binds to. */
    const val ACTION_BIND = "com.thvjq.nexlink.social.action.BIND_BRIDGE"

    /** The Social application ID (§10.5). NexLink targets the bind Intent at this package. */
    const val SOCIAL_PACKAGE = "com.thvjq.nexlink.social"

    /** The NexLink application ID. Social verifies the caller is this package (§16.3.2). */
    const val NEXLINK_PACKAGE = "com.thvjq.nexlink"

    /**
     * §16.2.2 — "this conversation has been read in Social".
     *
     * NexLink's unified inbox **cancels Social's notification and posts its own
     * copy**, which is what makes one inbox out of two apps. The cost is that
     * Social can then no longer clear it: by the time the user opens the
     * conversation, Social's notification is long gone — cancelled by NexLink —
     * so `cancel()` is a no-op and NexLink's copy stays in the shade. Reported
     * as "notifications are not disappearing when the chat is opened", and it
     * is not a bug in either app alone. It is the seam between them.
     *
     * So Social says so explicitly. A broadcast rather than the AIDL bridge:
     * this is one-way and fire-and-forget, and has to work whether or not
     * anything is bound. Sent to [NEXLINK_PACKAGE] only and guarded by
     * [PERMISSION], so no other app can send it or hear it.
     */
    const val ACTION_CONVERSATION_READ =
        "com.thvjq.nexlink.social.action.CONVERSATION_READ"

    /** The conversation title, matching the one Social put on its notification. */
    const val EXTRA_CONVERSATION_TITLE = "conversation_title"

    /**
     * §16.6 — the two apps negotiate compatibility through this, not through
     * matching version numbers. §10.5 makes their versions deliberately
     * independent.
     *
     * Bump on any incompatible change to the AIDL or the Parcelables. Adding a
     * method to the end of the AIDL interface is compatible; reordering or
     * removing one is not.
     */
    const val INTERFACE_VERSION = 1

    /** The oldest interface version this build can still talk to. */
    const val MIN_SUPPORTED_INTERFACE_VERSION = 1

    /** Most conversations NexLink may request in one call. Bounds the Binder transaction size. */
    const val MAX_CONVERSATIONS = 100

    fun isCompatible(remoteVersion: Int): Boolean =
        remoteVersion >= MIN_SUPPORTED_INTERFACE_VERSION
}
