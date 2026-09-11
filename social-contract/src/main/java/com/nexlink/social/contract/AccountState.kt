package com.nexlink.social.contract

/**
 * §16.4.2 / §16.5.2 — what the inbox should show for the Social row.
 *
 * Crosses AIDL as an [Int] rather than as a Parcelable enum: an enum added to
 * one side and not the other is a versioning hazard, and an unknown ordinal is
 * a crash. [fromId] maps anything it does not recognise to [UNKNOWN], so a
 * NexLink built against an older contract survives a newer Social.
 */
enum class AccountState(val id: Int) {
    /** Social is installed but nobody has signed in. Inbox shows a sign-in prompt. */
    SIGNED_OUT(0),

    /** Signed in and usable. */
    SIGNED_IN(1),

    /**
     * Signed in, but the local store is locked and content cannot be read
     * (§12.4.4 — the screen-lock problem). The inbox must show the row without
     * previews rather than showing nothing.
     */
    LOCKED(2),

    /** A state this build of the contract does not know about. Treat as SIGNED_OUT. */
    UNKNOWN(-1);

    companion object {
        fun fromId(id: Int): AccountState = entries.firstOrNull { it.id == id } ?: UNKNOWN
    }
}
