# Privacy Policy

**App:** NexLink  
**Last updated:** 8 June 2026

---

## 1. Overview

NexLink is a private, local-first communications app for Android. It replaces your default SMS/MMS app, displays social app notifications in one inbox, and (when both parties use NexLink) encrypts messages end-to-end on your device. **NexLink does not operate any servers, does not collect personal data, and does not transmit any information to third parties.**

---

## 2. Data Collected and Where It Lives

| Data type | Where it is stored | Sent anywhere? |
|---|---|---|
| SMS & MMS messages | Android system SMS/MMS database on your device | No |
| Contacts (name, number) | Read-only from your device contacts — never copied | No |
| Call log | Read-only from your device call log — never copied | No |
| Social app notifications | In-memory only while the app is running | No |
| Encryption keys (identity key pair, session keys) | `SharedPreferences` on your device | No |
| Voice recordings & camera photos | Temporary files in app cache, deleted after sending | No |

NexLink has **no analytics SDK, no crash-reporting SDK, no advertising SDK, and no backend server** of any kind.

---

## 3. Permissions Used

| Permission | Why it is needed |
|---|---|
| `READ_SMS`, `WRITE_SMS`, `RECEIVE_SMS`, `SEND_SMS` | Read, store, and send text messages as the default SMS app |
| `RECEIVE_MMS` | Receive multimedia messages (photos, video, audio) |
| `READ_CONTACTS`, `WRITE_CONTACTS` | Display contact names next to phone numbers |
| `READ_CALL_LOG`, `CALL_PHONE`, `READ_PHONE_STATE` | Show call history and place calls |
| `RECORD_AUDIO` | Record voice messages you choose to send |
| `CAMERA` | Take photos to attach to messages |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` | Attach files from your gallery |
| `INTERNET`, `CHANGE_NETWORK_STATE` | Download incoming MMS from your carrier's MMSC server |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Mirror social app notifications (Signal, WhatsApp, etc.) into the unified inbox |
| `POST_NOTIFICATIONS` | Show message and call notifications |
| `ANSWER_PHONE_CALLS`, `MANAGE_OWN_CALLS` | In-call screen integration |
| `FOREGROUND_SERVICE`, `WAKE_LOCK` | Keep call handling alive while a call is in progress |
| `RECEIVE_BOOT_COMPLETED` | Restore notification listeners after a device reboot |

---

## 4. End-to-End Encryption (NexLink ↔ NexLink)

When both sender and recipient have NexLink installed and set as their default SMS app, messages are automatically encrypted using:

- **Key exchange:** Elliptic-curve Diffie-Hellman (ECDH) on curve P-256
- **Session key derivation:** SHA-256 of the ECDH shared secret
- **Message encryption:** AES-256-GCM with a random 96-bit IV per message

Encrypted messages are stored on-device in their encrypted form (`[NXMSG1:…]`). Only NexLink can decrypt them. Your carrier and any third party who intercepts the SMS in transit sees only ciphertext.

**Current limitation:** This implementation uses static identity keys (no Double Ratchet / forward secrecy). A future version will add per-session ephemeral keys.

---

## 5. Social Notification Mirroring

NexLink reads notification content from social apps (Signal, WhatsApp, Telegram, Messenger, Discord, Instagram, Steam) that you have enabled in the Inbox tab. This content is:

- Stored **in memory only** — it is discarded when the app process stops
- Never written to disk in a way that persists between app launches
- Never transmitted off-device

NexLink does not read the content of private messages inside those apps — it only reads the text that those apps chose to expose in a notification.

---

## 6. Third-Party Services

NexLink does **not** integrate with any third-party analytics, advertising, or data-sharing service. The only network communication NexLink performs is:

1. Downloading MMS content from your mobile carrier's MMSC server (standard SMS/MMS protocol)
2. Sending MMS content to your mobile carrier's MMSC server

Both operations go directly between your device and your carrier over your mobile data connection.

---

## 7. Data Retention and Deletion

All data (messages, keys, session state) is stored locally on your device. Uninstalling NexLink removes all app data, including encryption keys. Your SMS messages remain in the Android system SMS database (accessible to the next default SMS app you install).

To delete messages: use the delete conversation or delete message features within NexLink, or clear all app data from Android Settings.

---

## 8. Children's Privacy

NexLink does not knowingly collect information from children under 13. It is a messaging app intended for general audiences and performs no age verification.

---

## 9. Changes to This Policy

If this policy is updated, the new version will be published in this file with an updated "Last updated" date. Continued use of the app after a change constitutes acceptance of the updated policy.

---

## 10. Contact

NexLink is an open-source project. Questions, concerns, or security disclosures can be submitted via the GitHub repository:

**https://github.com/THVjQ/NexLink/issues**
