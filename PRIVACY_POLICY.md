# Privacy Policy

**App:** NexLink  
**Last updated:** 22 July 2026

---

## 1. Overview

NexLink is a private, local-first communications app for Android. It replaces your default SMS/MMS app, displays social app notifications in one inbox, and (when both parties use NexLink) encrypts messages end-to-end on your device. **By default, NexLink operates no servers, collects no personal data, and transmits no information to third parties.**

**One optional exception — the Computer Bridge.** NexLink includes an optional feature, **off by default**, that lets you send and receive your own SMS from a computer by relaying them through **a server that you set up and run yourself**. It does nothing until you explicitly read a disclaimer, point the app at your own server, and enable it. The developer still operates no server and cannot see your data. See **Section 6a** for exactly what your server can and cannot see.

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
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | *(Computer Bridge only, when enabled)* Keep the bridge relay running reliably in the background |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | *(Computer Bridge only, when enabled)* Ask to be exempt from battery sleeping so the bridge delivers reliably |

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

## 6a. Optional Computer Bridge (off by default)

The Computer Bridge is an **opt-in** feature for reading and replying to your own texts from a computer. It is **disabled on a fresh install** and does nothing — no network activity, no background service, no extra permission prompts — until you complete a blocking disclaimer and setup wizard.

**Who runs the server:** You do. NexLink provides **no** server and **no** cloud service. You point the app at a server you own and operate (a home server, VPS, or free tier). The developer runs no bridge infrastructure and cannot access your data.

**What is transmitted, and to where:** When enabled, your outgoing and incoming SMS are relayed between your phone and your computer through **your** server. Message **contents are end-to-end encrypted** between your phone and your browser client using ECIES (P-256 ECDH → HKDF-SHA256 → AES-256-GCM), so **your server relays only ciphertext and cannot read your messages.**

**What your server can still see (metadata):** phone numbers, timing, message sizes, and device identifiers used for routing. Keep your server private, patched, and behind HTTPS or a private tunnel.

**Key exchange & verification:** Your phone and browser client exchange **public** keys through your server (private keys never leave their device). Because the server brokers this exchange, a *malicious* server could attempt a man-in-the-middle substitution. The setup wizard shows a **key fingerprint** you can compare against the one in your browser extension to rule this out.

**Scope:** Text SMS only. Picture messages (MMS) are **not** carried over the bridge in this version — an MMS still arrives on your phone but is not forwarded.

**Turning it off:** Disabling the bridge in Settings stops the relay service, cancels its background scheduling, and (optionally) forgets your stored server URL and keys. The app then behaves exactly as stock, on-device NexLink. Your NexLink message database and NexLink↔NexLink encryption keys are never affected by enabling or disabling the bridge.

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
