# Data Retention Schedule — NexLink Social

**Version 0.2.0-draft** · Not yet published · §32.7

Forms part of the Privacy Policy. Every category the Service holds, how long it
is kept, and what causes it to go.

---

## 1. Account data

| Data | Retention | Deleted by |
|---|---|---|
| Username (MXID) | **Permanent, tombstoned after deletion** | Never — see §4 |
| Password hash | Until Account deletion | Account deletion |
| Display name | Until Account deletion | Account deletion, or you clear it |
| Profile picture | Until Account deletion | Account deletion, or you clear it |
| Email address (if provided) | Until Account deletion or removal | Account deletion |
| Age confirmation (boolean) | Account life + limitation period | Not deleted with the Account |
| Terms acceptance record | Account life + limitation period | Not deleted with the Account |

## 2. Message and media data

| Data | Retention | Deleted by |
|---|---|---|
| Encrypted message events | Indefinite, or your retention setting | Account deletion; room deletion |
| Encrypted media | Until Account deletion or orphan cleanup | Account deletion; the hourly sweeper |
| Redaction originals | **7 days** | Automatic |
| Room state (membership, names) | Life of the room | Leaving and deletion |
| Reactions (unencrypted) | Until deleted | Account deletion; redaction |
| Read receipts, typing | Transient — not durably stored | — |

## 3. Technical and security data

| Data | Retention | Deleted by |
|---|---|---|
| IP addresses | **28 days** | Automatic expiry; **and purged at Account deletion** |
| User agent strings | 28 days | As above |
| Device list and public keys | Until the Device is removed | Signing out; removing the Device |
| Push routing token | Until sign-out or Device removal | Sign-out |
| Server logs | 28 days | Automatic rotation |

**IP records are purged at deletion, not merely left to expire.** A deleted
Account does not leave 28 days of connection history behind.

## 4. What survives account deletion

| Data | Why | Retention |
|---|---|---|
| Tombstoned username | Prevents reissue and impersonation of a former user | Permanent |
| Acceptance record | Evidence terms were accepted and age confirmed | Limitation period |
| Invite record — one-way hash, timestamps, pseudonymised | Abuse tracing; integrity of the invite tree | Life of the tree |

Nothing in this table can identify you from the Service alone once the Account
is gone, other than the username itself — which is retained precisely so that it
continues to refer to nobody.

## 5. Moderation data

| Data | Retention |
|---|---|
| Reports (reporter, subject, description, time) | 2 years |
| Message content attached to a report with consent | 2 years |
| Moderation decisions and actions | 2 years |

## 6. Backups — the retention hole

| Data | Retention |
|---|---|
| Encrypted database backups | **14 days** |
| Server configuration and signing key backups | 14 days |

**A deleted Account persists inside encrypted backups for up to 14 days** before
those backups expire and are destroyed.

This is stated explicitly because a deletion promise that quietly contradicts a
backup schedule is a promise not kept. Backups are encrypted, are never searched,
and are used only to restore the Service after failure.

## 7. Call data

| Data | Retention |
|---|---|
| Call membership events (who joined, when) | With the room's events |
| Call media | **Not recorded.** Relayed and discarded |
| LiveKit-side participation records | Per LiveKit's own retention; the conversation is not disclosed to them |

## 8. What is never collected

Contacts, address book, location beyond IP inference, advertising identifiers,
behavioural analytics, date of birth, payment details, biometrics.

## 9. Triggers for review

This schedule is reviewed when: a new data category is introduced; a retention
period changes; a new third party is added; or annually, whichever is soonest.
