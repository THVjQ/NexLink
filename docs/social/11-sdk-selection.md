# §11 — SDK selection

> **Confidence:** `SPECULATIVE`
>
> **This is the highest-risk chapter in the document.** The Matrix Android client
> ecosystem has been mid-migration, and the correct answer depends on the state
> of both options at the moment you actually start. Nothing here should be acted
> on without checking current reality first — §11.7 defines exactly what to
> check and how to decide.

---

## 11.1 Why this decision is expensive

The SDK owns the crypto store, the session, the sync loop and the room model. It
is not a library the application calls; it is the substrate the application is
built on. Changing it later is close to rewriting the client.

It also determines things that surface much later and are hard to reverse:
binary size, crash debuggability, the ceiling on sync performance, and how
quickly protocol improvements reach users.

**Getting this wrong is the most expensive mistake available in this project.**
Hence a chapter, rather than a line in §10.

---

## 11.2 The two candidates

### 11.2.1 matrix-android-sdk2

The Kotlin SDK developed for Element Android. Mature, widely deployed, idiomatic
Kotlin, well-understood.

Its problem is strategic rather than technical: Element's development effort
moved to a next-generation client built on the Rust SDK. A library whose primary
consumer has moved on receives maintenance, not investment — and for a project
starting now with a multi-year horizon, adopting the trailing option means
adopting an increasing divergence from where the protocol work happens.

### 11.2.2 matrix-rust-sdk

A shared Rust core with bindings generated for Kotlin and Swift, consumed by
Element's newer clients. One implementation of crypto, sync and state serves all
platforms.

Advantages are real: protocol work lands once and reaches every platform;
sliding sync and modern sync semantics are native rather than retrofitted;
cryptography receives the most scrutiny because it is the shared path.

Costs are equally real: a Kotlin API generated through FFI rather than
hand-designed; a stack trace that crosses a language boundary; native
libraries per ABI; and an API that has been evolving.

---

## 11.3 Comparison

| Dimension | matrix-android-sdk2 | matrix-rust-sdk |
|---|---|---|
| Language | Kotlin | Rust + generated Kotlin bindings |
| Maturity | High | Newer, evolving |
| Active investment | Maintenance | Primary |
| API ergonomics | Idiomatic Kotlin | FFI-shaped; improving |
| Sliding sync | Retrofitted | Native |
| Crypto implementation | Kotlin/JNI over libolm-lineage | Shared Rust crypto crate |
| Binary size | Smaller | Larger — native libs per ABI |
| Debuggability | Standard | Traces cross the FFI boundary |
| Local store | Realm | SQLite |
| Documentation | Better established | Thinner, moves faster |
| Risk profile | Stagnation | Churn |

**The choice is therefore between two different risks, not between a good and a
bad option.** Stagnation risk means the SDK slowly stops receiving the protocol
improvements this product needs. Churn risk means time spent tracking a moving
API. For a solo developer, churn is survivable and stagnation compounds.

---

## 11.4 Provisional recommendation

**Lean toward the Rust SDK**, subject to §11.7 passing.

The reasoning:

1. **Horizon.** This is a multi-year product. Aligning with where protocol work
   happens matters more than a smoother first month.
2. **Crypto scrutiny.** A shared implementation used by every platform receives
   more review than a platform-specific one.
3. **Sync performance.** Cold start and reconnect behaviour dominate perceived
   quality in a messaging app, and native modern-sync support is a direct
   advantage.
4. **Web parity.** The Rust core is closer to the direction the ecosystem's other
   clients are moving, which reduces divergence with §20.

The costs are accepted deliberately: a larger binary — mitigated by ABI splits
(§10.6.2) — and harder debugging, mitigated by §11.6.

**This recommendation is provisional.** It is exactly the kind of judgement that
becomes wrong when the ground moves, which is why the next section exists.

---

## 11.5 What could change the answer

Any of these observed during the spike flips the recommendation to
matrix-android-sdk2:

- The Kotlin bindings are not published in a consumable form, or lag the Rust
  core meaningfully.
- Key backup or cross-signing (§8) are incomplete in the bindings. **These are
  not optional features for this product** — the entire account recovery model
  (§7) depends on them.
- Crash rates or FFI-boundary instability make debugging impractical.
- Binary size after ABI splitting is unacceptable.
- The API changes so fast that upgrades cost days rather than hours.

Any of these observed flips it back:

- matrix-android-sdk2 has been formally deprecated or archived.
- It lacks a feature the product requires with no path to gaining it.

---

## 11.6 Insulating against the decision

Because the decision is genuinely uncertain, the architecture should make it
less catastrophic to get wrong. This is cheap insurance and should be built from
the first commit.

**`:social-core` exposes an application-facing interface; nothing outside it
imports SDK types.**

```kotlin
// :social-core — the app's vocabulary, not the SDK's
interface SocialSession {
    val state: Flow<SessionState>
    fun rooms(): Flow<List<RoomSummary>>
    fun timeline(roomId: RoomId): Timeline
    suspend fun send(roomId: RoomId, body: MessageBody): Result<EventId>
    suspend fun react(eventId: EventId, emoji: String): Result<Unit>
    fun devices(): Flow<List<DeviceInfo>>
    suspend fun verifyDevice(deviceId: DeviceId): VerificationFlow
}
```

`:social-ui` depends on `SocialSession`, never on the SDK. The SDK is an
implementation detail of one module.

**What this buys:** an SDK change becomes a rewrite of one module rather than of
the application. It also makes the UI testable against a fake session, which is
worth the abstraction on its own merits (§34).

**What it costs:** a translation layer, and the temptation to leak SDK types
through it for convenience. The dependency rule from §10.4 should be extended to
forbid SDK imports outside `:social-core`, enforced the same mechanical way.

**Do not over-abstract.** The goal is a seam at one module boundary, not a
protocol-agnostic messaging framework. Anything resembling the latter is scope
creep and should be rejected.

---

## 11.7 The decision procedure

Run during phase 2 (§33), timeboxed to **five working days**. Build the same
minimal client twice — once per SDK — against the spike homeserver from phase 1.

**The client must:**

1. Log in with username and password.
2. List rooms and open one.
3. Send and receive an encrypted text message.
4. Set up cross-signing and a recovery key.
5. Log in on a **second** device and verify it by QR.
6. Restore history on that second device from key backup.
7. Send and display a reaction with a multi-code-point emoji (skin tone or ZWJ).

**Steps 4–6 are the decision.** They exercise the machinery the entire account
model depends on (§7, §8). An SDK that makes steps 1–3 pleasant and step 6
impossible is the wrong SDK, and only building it reveals that.

### 11.7.0 Measured so far — matrix-rust-sdk, 2026-09-11

Gathered before the spike proper, because these are cheap and two of them are
§11.5 disqualifiers.

| Measure | Result |
|---|---|
| **Published in a consumable form?** | **Yes.** `org.matrix.rustcomponents:sdk-android:26.09.9` on Maven Central, published 2026-09-09. |
| **Does it lag the Rust core?** | **No.** The Maven version matches the GitHub release tag `sdk-v26.09.9` exactly, same day. |
| Resolves and links in `:social-core` | Yes, no exclusions or conflicts needed |
| **Per-device download, arm64-v8a** | **~24.5 MB** |
| Per-device download, armeabi-v7a | ~22.3 MB |
| Universal APK, all ABIs | **95.2 MB** |
| Delta over an SDK-free build | ~22 MB (baseline was 2.4 MB) |

**Trap: Maven Central's search API reports a stale latest version** — it said
`25.5.26`, sixteen months behind. `repo1.maven.org/maven2/org/matrix/rustcomponents/sdk-android/maven-metadata.xml`
is authoritative and said `26.09.9`. Reading the search API alone would have
falsely triggered §11.5's first disqualifier and flipped this chapter's
recommendation on bad data.

**§10.6.2 was right, and now it is measured.** It predicted that shipping every
ABI in one artifact "turns a 25 MB app into an 80 MB one". Actual: **24.5 MB per
device, 95.2 MB universal.** This is the concrete reason `:social` must ship as
an AAB and never as a universal APK, and it should be re-measured whenever the
SDK is upgraded.

The AAR also carries stub `armeabi`, `mips` and `mips64` directories from JNA.
They are a few hundred bytes and not worth excluding, but they explain why an
ABI listing shows seven entries for four real architectures.

### 11.7.2 API surface audit — §11.5's second disqualifier

§11.5 lists as a disqualifier: *"Key backup or cross-signing (§8) are incomplete
in the bindings. These are not optional features for this product — the entire
account recovery model (§7) depends on them."*

Audited the published AAR's class list and `EncryptionInterface` directly on
2026-09-11. **The disqualifier does not fire.** Everything §7 needs is there:

| §7 / §8 requirement | Binding |
|---|---|
| Generate a recovery key (§7.4) | `enableRecovery(...)` → returns the key, with a progress listener |
| Recover from a recovery key | `recover(key)`, `recoverAndFixBackup(key)` |
| Rotate a compromised key (§30.4.2) | `resetRecoveryKey()`, `recoverAndReset(key)` |
| Recovery state, observable | `recoveryState()`, `recoveryStateListener(...)` |
| Key backup (§7.4) | `enableBackups()`, `backupState()`, `backupExistsOnServer()`, `BackupSteadyStateListener` |
| Cross-signing reset (§8.3) | `resetIdentity()` → `IdentityResetHandle` |
| Verification (§8.4) | `Client.getSessionVerificationController()`, `SessionVerificationEmoji` |
| "Can I verify against anything?" (§8.5) | `hasDevicesToVerifyAgainst()` |
| Verification state | `verificationState()` |
| **Direct device transfer (§7.5.1)** | **`importSecretsBundle(SecretsBundleWithUserId)`** |
| Last-device check (§7.6) | `isLastDevice()` — useful for the deletion warning |
| Send queue (§13.5.1) | `enableAllSendQueues(...)`, `SendHandle` |
| Sliding sync (§13.2.3) | `SyncService`, `RoomListService` |
| Reactions (§14.4) | `Reaction`, `ReactionSenderData` |

`importSecretsBundle` is a pleasant surprise: §7.5.1's direct device-to-device
transfer has a first-class binding rather than needing to be built.

### 11.7.3 The one real gap — device management (§8.6)

**§11.9's first open question is now answered, and the answer is that it IS a
gap.** There is no device-listing API in the bindings. What exists is
`AccountManagementAction.DevicesList` / `.DeviceView` / `.DeviceDelete`, and
those are **deep links into the homeserver's own account-management web UI**, not
data the client can render.

So §8.6's surface — show the user their devices, with first-seen and last-seen,
and let them remove one — has to be built on **raw Client-Server API calls**:

```
GET    /_matrix/client/v3/devices
GET    /_matrix/client/v3/devices/{deviceId}
PUT    /_matrix/client/v3/devices/{deviceId}      (rename)
DELETE /_matrix/client/v3/devices/{deviceId}      (needs UIA)
```

Consequences, and they are not trivial:

- `:social-core` needs an authenticated HTTP path alongside the SDK, sharing its
  access token. NexLink already carries OkHttp (§10.7), so the library is not new.
- **Device deletion requires User-Interactive Auth**, the same staged flow Q2
  uncovered for registration (§22.10) — so the password re-prompt is mandatory,
  not a courtesy.
- §8.6's **new-device alert** — the §8.3.2 defence against a malicious server —
  has no binding either. It must be built by polling the device list and
  diffing, which is worse than an event and needs a deliberate cadence.

**This does not disqualify the SDK.** §11.5's disqualifiers are about key backup
and cross-signing, which are complete. It is a bounded piece of extra work in one
module, and it is exactly the kind of thing §11.6's seam exists to absorb. But it
is real, it was not budgeted, and it belongs in the phase 3 estimate.

### 11.7.4 Bake-off run on hardware, 2026-09-11

Run as an instrumented test (`SdkBakeOffTest`) on a Samsung SM-G990E, Android 16,
against the live phase-1 homeserver.

| §11.7 step | Result |
|---|---|
| 1. Log in with username and password | **PASS** |
| 2. List rooms | **PASS** |
| 3. Send an encrypted message | Sent; **the test's own assertion was wrong** — see below |
| **4. Cross-signing and a recovery key** | **PASS — this is the gate** |
| 5. Second device, verify by QR | Outstanding — needs two screens |
| **6. Restore history from key backup** | **PASS** — `RecoveryRestoreTest`, see §7.4.4 |
| 7. Multi-code-point reaction | **PASS** |

**Step 3 was a test defect, not a product one.** `Room.encryptionState()` returned
`NOT_ENCRYPTED` when read immediately after `awaitRoomRemoteEcho`, because the
`m.room.encryption` state event had not synced yet. Checked server-side, the room
is `m.megolm.v1.aes-sha2`. **The lesson generalises: `encryptionState()` is a
view of synced state, so it must never be used as a pre-send safety check** —
a client that gates sending on it would refuse to send in exactly the moments
right after room creation. §2.8's invariant is enforced by the server config
(`encryption_enabled_by_default_for_room_type: all`) and by the SDK, not by a
client-side poll.

#### Three undocumented requirements, none in the SDK's README

Each failed with an error that named something other than the missing piece, and
together they cost most of the bake-off's time:

1. **`initPlatform(TracingConfiguration, Boolean)` must be called before any
   network call.** Symptom: `InternalException: Expect rustls-platform-verifier
   to be initialized`. Belongs in `Application.onCreate`, once.
2. **`rustls-platform-verifier`'s Android class must be on the classpath** — the
   Rust side finds it by JNI class name. It is **not** bundled in the AAR and
   **not** on Maven Central; upstream ships it inside a Rust crate. Symptom
   *after* fixing (1): `failed to call native verifier: Error`. Element X Android
   vendors the single file as its own module and so do we — `:rustls-tls`, see
   its `VENDORED.md`. It needs its own module because the file reads
   `BuildConfig` unqualified, so `BuildConfig` must be generated into the
   package `org.rustls.platformverifier`.
3. **`slidingSyncVersionBuilder(DISCOVER_NATIVE)` is mandatory** or every
   room-list call fails with `Sliding sync version is missing`. Synapse has
   served MSC4186 natively since 1.114, so discovery resolves to native.

None of these is a reason to reject the SDK, but all three are the kind of thing
§11.3 meant by *"documentation: thinner, moves faster"*. They are recorded here
so the second developer does not rediscover them.

### 11.7.5 The FFI blocks, even where Kotlin says `suspend`

Found in phase 3, after two ANRs with **nothing in the crash log**.

Most SDK calls block the calling thread. That includes calls declared `suspend`
in the generated Kotlin: `suspend` describes the Kotlin side and says nothing
about what the Rust side does with the thread it was handed. Some calls
(`roomListService.room()`, `subscribeToTypingNotifications()`) are not even
declared `suspend` and block outright.

Called from a `lifecycleScope` coroutine — which defaults to the **main**
dispatcher — this freezes the UI. The symptom is "isn't responding", and because
an ANR is not a crash there is no stack trace to follow.

**The rule adopted: `:social-core` never assumes its caller is off the main
thread.** Every SDK call goes through an `io { }` / `ioCatching { }` helper that
hops to `Dispatchers.IO`. The hop belongs in the seam rather than at each call
site, where it would eventually be forgotten by whoever adds the next screen.

This is a genuine cost of the FFI that §11.2.2 anticipated in general terms
("a stack trace that crosses a language boundary") but not this specific one.
It is not a reason to reject the SDK. It **is** something a second developer
must know on day one, because the failure mode teaches nothing on its own.

### 11.7.1 Recorded for each candidate

| Measure | How |
|---|---|
| Time to working login | Wall clock, honestly |
| Whether steps 4–6 completed | Binary. No partial credit. |
| Release AAB size delta, per-ABI | Compare against an empty app |
| Cold sync time, 20 rooms | Median of five runs |
| Crash or FFI instability | Observed, described |
| API friction | Written impression — this one is subjective and worth recording anyway |
| Documentation adequacy | Written impression |

### 11.7.2 Deciding

Steps 4–6 are a gate: an SDK that fails them is eliminated regardless of every
other measure. If both pass, the recommendation in §11.4 stands unless a
specific measure contradicts it, and the contradiction is recorded here.

**Whatever is chosen, this chapter is rewritten with the actual findings and its
confidence marker raised from `SPECULATIVE`.** A spike whose results are not
written down has to be run twice.

---

## 11.8 What is explicitly not being considered

| Option | Why not |
|---|---|
| Writing a Matrix client from scratch | The crypto alone is a multi-year project. Contradicts §2.3. |
| Forking Element Android | Inherits a large codebase and its maintenance, for a product with different requirements. |
| A JavaScript SDK in a WebView | Unacceptable for battery, notifications, calls and key storage. |
| Building on Element X directly | It is an application, not a library. Its SDK is the Rust SDK, which is already candidate two. |

---

## 11.9 Open questions

- Does the chosen SDK expose enough to implement the §8.6 device-management
  surface, particularly new-device alerts? If not, that is a gap requiring
  direct API work.
- How is the SDK's own local store encrypted, and does it satisfy §12? The SDK
  owns the crypto store; the application cannot substitute its own.
- What is the upgrade cadence, and what does a breaking change cost? Worth
  measuring once during the spike by deliberately upgrading a version.
