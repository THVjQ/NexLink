package com.nexlink.social.contract;

import com.nexlink.social.contract.SocialConversation;
import com.nexlink.social.contract.ReplyResult;
import com.nexlink.social.contract.ISocialBridgeCallback;
import android.app.PendingIntent;

/**
 * The direct link between NexLink and NexLink Social — §16.4.2.
 *
 * Level 1 of §16. Level 0 (notification aggregation, no code) already gives the
 * unified inbox Social conversations on day one; this exists to replace scraped
 * notification text with real conversation objects.
 *
 * Guarded by com.thvjq.nexlink.permission.SOCIAL_BRIDGE, which is
 * protectionLevel="signature" — only an app signed with the same key can bind
 * (§16.3.1). Social ALSO verifies the calling package and signature itself
 * (§16.3.2), because defence in depth is cheap here.
 *
 * WHAT MUST NEVER BE ADDED TO THIS INTERFACE (§16.4.3, §16.8):
 *   - full message history
 *   - any key material, device list or verification state
 *   - raw Matrix identifiers
 * NexLink gains no ability it did not have; it gains better data for a surface
 * it already owns.
 *
 * Versioning: append only. Adding a method to the end is compatible; reordering
 * or removing one is not, and needs SocialBridgeContract.INTERFACE_VERSION
 * bumped (§16.6).
 */
interface ISocialBridge {

    /**
     * Checked first, before anything else is called (§16.6).
     *
     * NOTE: §16.4.2 names this `getInterfaceVersion()`. It cannot be called
     * that — `aidl` reserves the name for its own stable-interface versioning
     * and fails the build with "method getInterfaceVersion() is reserved for
     * internal use". Renamed here; §16.4.2 carries the correction.
     */
    int getContractVersion();

    /** An AccountState id — see AccountState.fromId (§16.5.2). */
    int getAccountState();

    /**
     * Most recent conversations, newest first. `limit` is clamped to
     * SocialBridgeContract.MAX_CONVERSATIONS on the Social side.
     */
    List<SocialConversation> getConversations(int limit);

    int getUnreadTotal();

    /**
     * Returns a PendingIntent rather than launching, so NexLink controls when
     * and how it fires — matching how the existing notification listener handles
     * social app deep links (§16.4.2).
     */
    PendingIntent openConversation(String id);

    ReplyResult sendReply(String id, String body);

    void markRead(String id);

    void subscribe(ISocialBridgeCallback callback);

    /** §16.5.3: unbind when the inbox is not visible. Never hold this in the background. */
    void unsubscribe(ISocialBridgeCallback callback);
}
