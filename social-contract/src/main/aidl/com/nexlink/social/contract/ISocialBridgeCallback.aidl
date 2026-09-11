package com.nexlink.social.contract;

/**
 * Pushed from Social to NexLink so the inbox does not have to poll — §16.4.2.
 *
 * Oneway throughout: NexLink must never be able to block the Social process by
 * being slow in a callback.
 */
oneway interface ISocialBridgeCallback {

    /** A conversation changed: new message, read state, title, mute. */
    void onConversationsChanged();

    /** The account state changed (§16.5.2). Argument is an AccountState id. */
    void onAccountStateChanged(int accountState);

    /** Total unread across all conversations changed. Cheap badge update. */
    void onUnreadTotalChanged(int total);
}
