# §35.1 — Play Console declarations, drafted

Paste-ready answers for the console, drafted 2026-09-16 against the **merged
release manifest** rather than from memory. Regenerate the permission list with:

```bash
grep -oE 'uses-permission android:name="[^"]+"' \
  social/build/intermediates/merged_manifest/release/AndroidManifest.xml
```

**Everything here must stay true.** A Data safety form that disagrees with
§9.6.1's screen is worse than either one alone: the app would be telling the
user one thing and Google another, and §9.6.1 is the version the user acted on.

---

## Before any of this can be submitted

- [ ] **The privacy policy needs a public URL.** Play will not accept a listing
      without one. The document exists (`docs/social/legal/privacy-policy.md`,
      now shipped in-app too) but is not hosted. Anything stable works —
      `nexlink.thvjq.com.au/privacy` is the obvious home.
- [ ] Play package name must be `com.thvjq.nexlink.social`.

---

## Data safety

### Does your app collect or share any of the required user data types?
**Yes** — collected, not shared with third parties.

### Is all user data encrypted in transit?
**Yes.** TLS to the homeserver, plus end-to-end encryption (Olm/Megolm) for
message and call content.

### Do you provide a way for users to request that their data be deleted?
**Yes.** In-app (Settings → account deletion) and from a public web page
(§32.3). Deletion purges media (§25.7), verified end to end.

### Data types — declare exactly these

| Type | Collected | Shared | Purpose | Optional? |
|---|---|---|---|---|
| **Messages** (other in-app messages) | Yes | No | App functionality | Required |
| **Photos and videos** | Yes | No | App functionality | Required |
| **Files and docs** | Yes | No | App functionality | Required |
| **User IDs** | Yes | No | App functionality, Account management | Required |
| **App interactions** | Yes | No | App functionality | Required |
| **Crash logs / Diagnostics** | No | No | — | — |
| **Approximate/precise location** | No | No | — | — |
| **Contacts** | No | No | — | — |
| **Name, email, phone number** | No | No | — | — |

**Messages / Photos / Files are declared as collected even though the operator
cannot read them.** Play's definition of "collected" is transmitted off the
device, not readable by the developer — ciphertext transits and is stored on the
server, so "no" would be false. The encryption is stated in the *"encrypted in
transit"* answer and in the store description, which is where it belongs.

**No name, email or phone number is collected**, and that is unusual enough to
be worth stating in the listing: an account is a username and a password, there
is no directory, and there is no way to find someone by phone number (§6).

**"App interactions" covers the §9.6.1 right-hand column** — who messages whom
and when, frequency and rough volume, which devices, and that a message arrived.
This is metadata the server necessarily sees to route messages.

**IP address** is not a Play data type but IS logged by the homeserver. It is
disclosed in §9.6.1 and in the privacy policy; mention it in the listing rather
than leaving the impression nothing is recorded.

---

## Sensitive permissions and APIs

### `RECORD_AUDIO` + `CAMERA`, with foreground service types `microphone|camera`

> NexLink Social is a private messenger with voice and video calling. The
> microphone and camera are used only during a call the user has started or
> answered, and only while the in-call screen or its ongoing-call notification
> is present. The foreground service exists so that an active call survives the
> user switching apps or locking the screen; it is started when a call connects
> and stopped when it ends. There is no recording: the app contains no
> MediaRecorder or MediaMuxer, and a CI check asserts this on every build.

*The last sentence is true and mechanically enforced —
`tools/check-invariants.sh`, check 1 (§2.8 invariant 4, §18.2). It is worth
saying because "calling app wants the microphone" is exactly the claim reviewers
are sceptical of.*

**Demo video must show:** starting a call, the ongoing-call notification, and
the service stopping when the call ends.

### `USE_FULL_SCREEN_INTENT`

> The app is a calling app. A full-screen intent is used for one thing: showing
> the incoming-call screen when a call arrives while the device is locked, which
> is the behaviour a user expects of any phone call. It is never used for
> promotions, reminders or any non-call notification. Calls are presented with
> CallStyle notifications and the app integrates with Telecom as a self-managed
> connection service.

### `MANAGE_OWN_CALLS`

> Used to register the app's voice and video calls with Android's Telecom stack
> as a self-managed connection service, so that a Social call and a cellular
> call interact correctly — an incoming mobile call holds or ends the in-app
> call rather than the two competing for the microphone.

### `POST_NOTIFICATIONS`

> Used for incoming messages and incoming calls. Requested at first run;
> declining leaves the app fully functional and quieter.

### `RECEIVE_BOOT_COMPLETED`

> Re-registers the push receiver after a restart so that messages continue to
> arrive. No work is performed at boot beyond that.

### Photo and video permissions

**None requested.** Attachments use the system photo picker
(`ActivityResultContracts.GetContent`), so the app receives only the single file
the user chose and never asks for media access (§14.5).

---

## Content and policy

### App category
Communication.

### Target audience
**16 and over.** The acceptance gate asks for a date of birth and refuses under
16 (§9.6.2). The date itself is **never stored** — only the pass/fail — which is
enforced by a CI check.

### User-generated content

> The app is invite-only: an account cannot be created without a single-use code
> issued by an existing user or the operator, so there is no open sign-up.
>
> Every message carries a report action and a block action, both reachable in
> one tap from a message, a profile, or the conversation list. Blocking is
> immediate and enforced server-side for messages, invitations and calls.
> Reports reach the operator with the reporting user, the room and the event
> reference.
>
> Because conversations are end-to-end encrypted, the operator cannot read
> reported content. Moderation therefore acts on accounts — suspension and
> deactivation — rather than on individual messages, and the reporting screen
> tells the user this plainly rather than implying a human will read the
> message.

*That last paragraph is the honest version of §31.3.2 and should not be softened
for the form: claiming content review that cannot happen is the failure mode.*

### Ads
**None.** The app contains no advertising SDK and does not request the
advertising ID.

### Account deletion URL
Required by Play for any app with accounts. Point it at the public deletion page
(§32.3) once it has a stable URL.

---

## Release details

| | |
|---|---|
| Package | `com.thvjq.nexlink.social` |
| versionCode / versionName | `1` / `0.1.0` |
| Signing | release keystore, certificate valid to 2053 |
| Minified | R8 + resource shrinking from the first release (§10.6.3) |
| targetSdk | 36 |
| Bundle | ~102 MB, dominated by the Rust SDK's native libraries across four ABIs; Play delivers per-device splits, so the download is ~25-30 MB |

**Bump `versionCode` for every upload.** Play rejects a repeat of the same one,
and it is the single most common way a second upload wastes ten minutes.

## Sequencing

§33.8 places Play behind phase 6 — a private launch with 10-20 real users and
two incident-free weeks. **Internal testing track now is the right move**;
production before phase 6 would be submitting an app nobody has used.
