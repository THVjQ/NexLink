# Privacy Policy — NexLink Social

**Version 0.1.0-draft** · Not yet published · §4.7, §32

*Plain-language summary: `transparency-note.md`. Where the two differ this one
governs — but they are written to agree, and a discrepancy is a bug.*

## 1. Who is responsible

NexLink Social is operated by an individual in New South Wales, Australia, not
by a company. Contact: the operator, at the address given in the app.

## 2. What we collect, and why

### 2.1 What we cannot collect

Message and call content is end-to-end encrypted. The server stores ciphertext
and has never held the keys. This is a property of the system's design, not an
undertaking about our conduct — it means we cannot produce message content in
response to any request, including a lawful one.

### 2.2 What we do hold

| Data | Why | Lawful basis | Kept for |
|---|---|---|---|
| Account identifier (MXID) | To route your messages | Contract | Permanently — see §5 |
| Display name, avatar | To show you to others | Contract | Until deleted |
| Encrypted message and file content | To deliver them | Contract | Until deleted or the retention setting removes it |
| Device list, public device keys | Encryption, and so you can see where you are signed in | Contract | Until the device is removed |
| Who messaged whom, and when | Unavoidable in routing | Legitimate interest — operating the service | Until account deletion |
| IP address and user agent | Abuse handling and diagnostics | Legitimate interest — security | **28 days**, and removed at account deletion |
| Reaction emoji, sender, target event | Matrix sends reactions unencrypted | Contract | Until deleted |
| Acceptance record (that terms were accepted, that age was confirmed) | To evidence consent | Legal obligation | **Kept after deletion** — see §5 |
| Invite record (one-way hash of the code, timestamps) | Abuse tracing, invite tree integrity | Legitimate interest | **Kept after deletion** — see §5 |

We do not collect a date of birth. The age check records only whether the
threshold was met.

We have no advertising, no analytics, no tracking, and nothing is sold or shared
for marketing.

## 3. Third parties

| Who | What they receive | Why |
|---|---|---|
| Cloudflare | Your IP address and connection metadata | Network transport; the server has no public address |
| Google (Firebase Cloud Messaging) | That a notification was sent to your device, and when. **Never content** | Push notification delivery |
| LiveKit | Call participation and timing. Media is encrypted; the conversation it belongs to is not disclosed to them | Call relay |

None can read message content.

## 4. Your rights

You may request access, correction, erasure, restriction, or a copy of your data
in a portable form.

- **Erasure** is self-service: in the app, or at
  `https://nexlink.thvjq.com.au/delete/` without the app.
- **Portability** is self-service: the app exports your messages as JSON. We
  cannot do this for you, because we cannot read them.
- Other requests: contact the operator.

## 5. What is kept after deletion, and why

Deletion is thorough but not total, and the exceptions are deliberate:

| Kept | Why | Basis |
|---|---|---|
| Your username, tombstoned | So it can never be reissued and used to impersonate you | Legitimate interest — protecting other users |
| The acceptance record | Evidence that terms were accepted and age confirmed | Legal obligation; establishing and defending legal claims |
| The invite record — a one-way hash and timestamps | Abuse tracing and the integrity of the invite tree | Legitimate interest |

Everything else goes: the account, every session, your profile, your uploaded
files, your encrypted backup, your email address if you gave one, and your IP
and user-agent records.

## 6. Security

Messages are end-to-end encrypted using the Matrix protocol (Olm and Megolm).
The server database is encrypted at rest. Backups are encrypted. Administrative
access is restricted to the operator.

**The honest limits:**

- Your device is the weak point. Anyone who unlocks it can read your messages.
- A recipient can screenshot or copy anything you send them.
- We hold the metadata in §2.2 and could be compelled to disclose it. We could
  not be compelled to disclose message content, because we do not have it.

## 7. Children

The service is not offered to people under the minimum age stated at sign-up,
and an age confirmation is required. We do not knowingly hold data belonging to
anyone below it.

## 8. Changes

This policy is versioned. A material change requires re-acceptance in the app,
so you cannot be moved to new terms silently.

## 9. Complaints

Contact the operator first. In Australia you may also complain to the Office of
the Australian Information Commissioner.
