package com.nexlink.social.core.rust

import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.EnableRecoveryProgress
import org.matrix.rustcomponents.sdk.EnableRecoveryProgressListener

/**
 * The no-email recovery path — §7.
 *
 * §11.7.2 audited these bindings and they cover §7 completely, which matters
 * because §11.5 makes an incomplete recovery model a disqualifier for the SDK:
 * *"These are not optional features for this product — the entire account
 * recovery model (§7) depends on them."*
 *
 * §7.2's distinction is the one to keep straight in the UI: the **password**
 * signs you in, the **recovery key** decrypts your history. Users conflate them,
 * and this class deliberately never handles the password.
 */
class RustRecovery(private val client: Client) {

    /**
     * Generate the recovery key and upload an encrypted key backup (§7.4.2).
     *
     * The returned string is the **only** copy. §7.3 is built around the fact
     * that nobody — including the operator — can produce it again: the server
     * holds ciphertext only (§1.3, §2.8 invariant 1).
     *
     * @param waitForBackupToUpload when true, does not return until the existing
     *   history has finished uploading. §8.5.2 — a user told "you're protected"
     *   while the backup is still empty has been misled.
     */
    suspend fun enableRecovery(
        waitForBackupToUpload: Boolean = true,
        onProgress: (RecoveryProgress) -> Unit = {}
    ): String =
        client.encryption().enableRecovery(
            waitForBackupToUpload,
            null,
            object : EnableRecoveryProgressListener {
                override fun onUpdate(status: EnableRecoveryProgress) {
                    onProgress(
                        when (status) {
                            is EnableRecoveryProgress.Starting -> RecoveryProgress.Starting
                            is EnableRecoveryProgress.CreatingBackup -> RecoveryProgress.CreatingBackup
                            is EnableRecoveryProgress.CreatingRecoveryKey -> RecoveryProgress.CreatingRecoveryKey
                            is EnableRecoveryProgress.BackingUp -> RecoveryProgress.BackingUp(
                                status.backedUpCount.toInt(), status.totalCount.toInt()
                            )
                            is EnableRecoveryProgress.RoomKeyUploadError -> RecoveryProgress.Error
                            is EnableRecoveryProgress.Done -> RecoveryProgress.Done
                        }
                    )
                }
            }
        )

    /** Restore history on a new device from the recovery key (§8.5). */
    suspend fun recover(recoveryKey: String) = client.encryption().recover(recoveryKey)

    /** §30.4.2 — a disclosed key cannot be revoked, only superseded. */
    suspend fun resetRecoveryKey(): String? = client.encryption().resetRecoveryKey()

    /** §8.5.2 — is there actually a backup on the server? */
    suspend fun backupExistsOnServer(): Boolean = client.encryption().backupExistsOnServer()

    /** §8.5 — can this device be verified against another, or is it alone? */
    suspend fun hasDevicesToVerifyAgainst(): Boolean = client.encryption().hasDevicesToVerifyAgainst()

    /** §7.6 — the deletion warning needs to know if this is the last device. */
    suspend fun isLastDevice(): Boolean = client.encryption().isLastDevice()
}

sealed interface RecoveryProgress {
    data object Starting : RecoveryProgress
    data object CreatingBackup : RecoveryProgress
    data object CreatingRecoveryKey : RecoveryProgress
    data class BackingUp(val done: Int, val total: Int) : RecoveryProgress
    data object Error : RecoveryProgress
    data object Done : RecoveryProgress
}
