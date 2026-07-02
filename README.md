<h1 align="center">NexLink</h1>
<p align="center"><b>One inbox. Every message.</b><br>
A private, local-first Android messenger by <a href="https://thvjq.com.au">THVjQ</a>.</p>

<p align="center">
  <a href="https://thvjq.com.au/nexlink/">Website</a> ·
  <a href="https://thvjq.com.au/nexlink/privacy/">Privacy Policy</a> ·
  <a href="./PRIVACY_POLICY.md">Privacy (Markdown)</a>
</p>

---

NexLink replaces your default Android SMS/MMS app, gathers your social-app notifications
into a single unified inbox, and encrypts NexLink-to-NexLink conversations end-to-end —
**with no servers, no analytics, and no tracking.** Everything runs on your device.

## Features

- **Unified inbox** — SMS, MMS and social notifications in one chronological list.
- **End-to-end encryption** — NexLink↔NexLink messages encrypted on-device; your carrier only sees ciphertext.
- **Calls & voice** — native call handling plus recorded voice messages.
- **Wear OS companion** — read and reply from a paired watch.
- **Media & camera** — attach gallery photos or snap a new one in-chat.
- **No backend** — no server, no SDKs, no ads.

## One inbox for

SMS / MMS · Signal · WhatsApp · Telegram · Messenger _(social notifications are mirrored **read-only** — nothing leaves your device)._

## Encryption

When both parties use NexLink, messages are automatically encrypted on-device:

| Stage | Algorithm |
|---|---|
| Key exchange | Elliptic-curve Diffie-Hellman (ECDH), curve **P-256** |
| Session key | **SHA-256** of the ECDH shared secret |
| Message | **AES-256-GCM**, random 96-bit IV per message |

Encrypted messages are stored as ciphertext (`[NXMSG1:…]`). Only NexLink can decrypt them.

> **Current limitation:** static identity keys (no Double Ratchet / forward secrecy). Per-session ephemeral keys are planned.

## Modules

| Path | What it is |
|---|---|
| `app/` | Main Android application |
| `wear/` | Wear OS companion |
| `shared/` | Shared code / Data Layer contract |
| `android-app/` | Android app resources |

## Privacy

NexLink collects no personal data and operates no servers. See the full
[Privacy Policy](./PRIVACY_POLICY.md) (also hosted at
[thvjq.com.au/nexlink/privacy](https://thvjq.com.au/nexlink/privacy/)).

## Build

Standard Gradle project:

```bash
./gradlew assembleDebug
```

## License

See [LICENSE](./LICENSE).

---

<p align="center"><sub>Part of the <a href="https://thvjq.com.au">THVjQ</a> ecosystem.</sub></p>
