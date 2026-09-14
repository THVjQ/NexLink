package com.nexlink.social.core.session

/**
 * §15.6 — somebody is ringing.
 *
 * Deliberately small. The ring notification says who, where, until when, and
 * whether there is video; everything else about the call is discovered after
 * the user answers, by the widget (§17.6.3).
 */
data class IncomingCall(
    val roomId: RoomId,
    val roomTitle: String,
    val callerId: String,
    /** Wall-clock ms after which this ring is a missed call. From the wire. */
    val expiresAtMs: Long,
    val video: Boolean
)
