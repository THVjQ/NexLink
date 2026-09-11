package com.nexlink.social.core.session

/**
 * Identifier types for the application's vocabulary — §11.6.
 *
 * Value classes rather than bare Strings so that a room ID cannot be passed
 * where an event ID is expected. Zero runtime cost.
 *
 * These are the APPLICATION's types. They happen to wrap Matrix-shaped strings
 * today; nothing outside `:social-core` is entitled to assume that, and nothing
 * outside this module should ever parse them.
 */
@JvmInline value class RoomId(val value: String) {
    init { require(value.isNotEmpty()) { "RoomId must not be empty" } }
    override fun toString() = value
}

@JvmInline value class EventId(val value: String) {
    init { require(value.isNotEmpty()) { "EventId must not be empty" } }
    override fun toString() = value
}

@JvmInline value class DeviceId(val value: String) {
    init { require(value.isNotEmpty()) { "DeviceId must not be empty" } }
    override fun toString() = value
}

@JvmInline value class UserId(val value: String) {
    init { require(value.isNotEmpty()) { "UserId must not be empty" } }
    override fun toString() = value
}
