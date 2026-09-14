# §17 — Call architecture

> **Confidence:** `REVISABLE`
> Raised from `SPECULATIVE` on 2026-09-11 after checking the actual state of the
> stack. The component list is now verified against released versions. The
> client-side integration (§17.6) remains the least certain part and is the
> primary subject of the phase 4 spike (§33.4).

---

## 17.1 D3 restated, because this chapter is where it bites

From §2.4: **screen sharing into a live call is in scope; recording anything to
a file is not.** This chapter designs a media path that carries audio, video and
a screen track between participants and terminates them in a renderer. At no
point does it acquire a file handle.

That is not a comment about discipline. It is a structural property worth
preserving: if no component in the pipeline has a sink that writes to storage,
then adding recording is a visible architectural change rather than a two-line
diff, and the legal analysis in §2.4 gets its chance to happen first.

---

## 17.2 The choice: MatrixRTC, not legacy VoIP

Matrix has two calling mechanisms and they are not alternatives of equal
standing.

| | Legacy VoIP (MSC2746) | MatrixRTC (MSC4143 / MSC4195) |
|---|---|---|
| Topology | Peer-to-peer, 1:1 only | SFU-mediated, 1:1 and group |
| Group calls | Not supported | Native |
| Signalling | `m.call.*` room events | Room state + delayed events |
| Media | Direct between peers | Via a Selective Forwarding Unit |
| Investment | Legacy | Where all current work happens |
| Screen share | Awkward | First-class |

**Decision: MatrixRTC with a LiveKit backend.** The product requires group
calls (§1.2), and the legacy path cannot provide them at all. Building 1:1 on
one mechanism and groups on another would mean two call stacks, two sets of
platform bugs, and a user-visible discontinuity at the moment a third person
joins.

One stack, used for both. A 1:1 call is a group call with two participants.

**Reversal cost: high.** The SFU choice determines the server topology (§21),
the cost model (§28) and the client media library. It is the second-largest
technical bet in the project after Matrix itself.

### 17.2.1 Why a SFU at all, when 1:1 could be peer-to-peer

It could. It should not.

Peer-to-peer for 1:1 saves server bandwidth and adds a second code path,
a second set of NAT-traversal failures to debug, and a discontinuity when a
call is escalated to a third participant. At this product's scale (§28) the
bandwidth saved is not worth a second implementation.

A more consequential reason: peer-to-peer media **reveals each participant's IP
address to the other**. For a product whose privacy claims are as explicit as
§9.6.1's "what we can and cannot see" screen, that is a disclosure that would
have to be made on that screen. Routing all media through the SFU means the
only IP either party learns is the server's.

---

## 17.3 Components

Verified against released versions on 2026-09-11:

| Component | Version | Role |
|---|---|---|
| **LiveKit SFU** | `v1.13.6` | Receives each participant's media, forwards selectively to the others |
| **lk-jwt-service** | `v0.7.0` | The MatrixRTC Authorization Service. Validates a Matrix identity, issues a LiveKit JWT scoped to one room |
| **Element Call** | `v0.25.0` | The call frontend. Bundled with Element Web (§20); usable as a widget |
| **Synapse** | `v1.160.0` | Carries call membership state and delayed events |

The flow, once:

```
client                lk-jwt-service            LiveKit SFU
  │                         │                        │
  ├── openid token ────────►│                        │
  │   (from homeserver)     │ verify against HS      │
  │◄── LiveKit JWT ─────────┤ scoped to room+identity│
  │                         │                        │
  ├── connect with JWT ─────────────────────────────►│
  ├── publish audio/video/screen ───────────────────►│
  │◄── subscribe to other participants ──────────────┤
```

The homeserver never carries media. It carries **who is in the call**, as room
state, and that is the entire integration: a client discovers a call by seeing
call-membership state in a room, and joins by asking lk-jwt-service for a token.

### 17.3.1 Homeserver configuration

Required in `homeserver.yaml` (expanded in §22):

```yaml
experimental_features:
  msc3266_enabled: true      # room summary
  msc4143_enabled: true      # MatrixRTC
  msc4222_enabled: true      # state_after in sync

max_event_delay_duration: 24h

matrix_rtc:
  transports:
    - type: livekit
      livekit_service_url: https://rtc.thvjq.com.au/livekit/jwt
```

`max_event_delay_duration` is not optional and is easy to miss. MatrixRTC uses
**delayed events** as a dead-man's switch: a client joining a call schedules its
own "I have left" event to fire in the future and keeps refreshing it. If the
client dies, the delay expires and the membership self-cleans. Without this
setting, a crashed client leaves a ghost participant in the room forever.

---

## 17.4 The blocker: Willard cannot host the SFU

This is the most important paragraph in Part VI and it belongs here, where the
requirement originates.

**Verified on Willard, 2026-09-11:**

```
egress IP    65.181.12.240
hostname     customer.sydyaus1.isp.starlink.com
org          AS14593 Space Exploration Technologies Corporation
IPv6         none — no global address, no default v6 route
```

Willard is behind **Starlink CGNAT**. It has no public IPv4 address, no inbound
port forwarding, and — checked specifically, because it would have been the
escape hatch — **no IPv6 at all**. The only global-scope v6 addresses on the box
are Docker's own ULA ranges (`fdd0::/8`), which are not routable.

Everything Willard currently publishes reaches the internet by making an
**outbound** connection and having traffic pushed back down it: Cloudflare
Tunnel for HTTP, playit.gg for the Minecraft ports. Both are outbound-initiated
relays.

A SFU cannot work this way. LiveKit needs participants to reach it inbound on
UDP — nominally a `50000–60000` range plus TCP `7881` as a fallback — and it
must advertise an address in its ICE candidates that clients can actually route
to. Behind CGNAT there is no such address.

The workarounds do not survive contact with the requirement:

| Workaround | Why it fails |
|---|---|
| Cloudflare Tunnel | Carries HTTP/WebSocket. Does not carry the UDP media path. |
| playit.gg UDP tunnels | Already used for Geyser, so the pattern is familiar — but it is a hobbyist relay sized for game traffic, in the wrong place topologically, and routing every call's media through it adds a hop and a bandwidth ceiling the product cannot control. |
| IPv6-only media | No IPv6 on Willard, and it would exclude every v4-only client anyway. |
| Starlink public IP | Available on some plans. It moves the whole product's availability onto a single ISP option that can be withdrawn, and does nothing about Starlink's latency and jitter as a *media relay* path. |
| TURN-only, relay elsewhere | If media is relayed by a box with a public IP, that box is the SFU's network location. This is not a workaround; it is §17.5 with extra steps. |

### 17.4.1 The consequence

**The split is not optional:**

- **On Willard** — Synapse, PostgreSQL, the media repository, Element Web, the
  invite service, lk-jwt-service. All HTTP, all reachable through the existing
  Cloudflare Tunnel, all benefiting from pool replication (§23).
- **Not on Willard** — the LiveKit SFU and coturn. These need a public IP and
  belong on a small VPS (§24).

This does not contradict the decision to build on Willard. It bounds it: Willard
hosts the service; one small rented box carries the media. Phases 1 through 3 of
the delivery plan (§33) run entirely on Willard and need no VPS at all, because
messaging does not touch this stack. **The VPS is a phase 4 expense, not a
day-one one.**

Sizing, cost and configuration are in §24. The cost model in §28 treats it as a
separate line precisely because it scales with call-minutes rather than users.

---

## 17.5 End-to-end encryption in calls

A SFU forwards media. To forward it, it ordinarily decrypts the transport layer
— DTLS-SRTP terminates at the server. That would put call content in the clear
on infrastructure the operator runs, which §2.8's first invariant forbids.

MatrixRTC's answer is a **second encryption layer inside the transport one**.
Frames are encrypted by the sending client before they enter WebRTC, using a key
the SFU never receives; the SFU forwards opaque payloads.

- The per-call key is distributed **over Matrix**, to-device and Olm-encrypted,
  to exactly the set of devices in the call.
- It is **rotated on membership change**. Someone who leaves cannot decrypt what
  is said afterwards; someone who joins cannot decrypt what was said before.
- The SFU sees packet timing, sizes, and who is talking to whom. It does not see
  content.

**This must be verified, not assumed.** Specifically, during the phase 4 spike:

1. Confirm E2EE is actually **on** in the deployed configuration. It is a
   setting, and a call that silently falls back to transport-only encryption
   looks identical to the user.
2. Confirm key rotation fires on join and on leave, by observing it.
3. Confirm the Android client participates in the same key exchange as Element
   Web, because §20 makes the web client a full participant.

Item 1 is a §2.8 invariant. A test that asserts it should exist before the
feature ships, not after.

### 17.5.1 What this costs

Per-frame encryption in the client, and the loss of any server-side media
processing — no server-side recording (not wanted, §2.4), no transcription
(explicitly a non-goal, §1.3), no server-side noise suppression. Nothing the
product wants is given up.

---

## 17.6 Client integration — the genuinely open question

Two ways for the Android app to join a MatrixRTC call, and this is the part the
spike exists to resolve.

### Option A — the Element Call widget in a WebView

Load Element Call in a WebView, hand it the room, let it do everything.

**For:** the entire call implementation is upstream's problem. E2EE, key
rotation, simulcast, layout, reconnection, and every protocol change arrive for
free. This is what Element's own mobile clients do.

**Against:** §11.8 rejected "a JavaScript SDK in a WebView" for the *messaging*
client on battery, notification and key-storage grounds. Those objections are
much weaker for calls, which are foreground, short-lived and user-initiated —
but a WebView still constrains native integration with Telecom (§19),
hardware media buttons, and the incoming-call surface.

### Option B — livekit-android natively, MatrixRTC state in `:social-core`

Use the LiveKit Android SDK directly; implement the call-membership state
events and token exchange in `:social-core`.

**For:** a native call UI, native Telecom integration, full control of the
audio path.

**Against:** the E2EE layer of §17.5 must be implemented and kept compatible
with Element Call's, or the web client and the phone cannot be in the same call.
That compatibility is the hard part, it is a moving target, and getting it
subtly wrong produces a call that connects and shows black frames.

### 17.6.1 The decision procedure

Run in phase 4 (§33.4), timeboxed to **five working days**, exactly like §11.7
and for the same reason. Build the smallest thing that proves the risky part:

1. Two Android devices in a 1:1 call, audio both ways.
2. A third participant joining from Element Web, audio and video all three ways.
3. Screen share from Android, visible on Element Web.
4. Confirm E2EE is on and keys rotate when participant 3 leaves.
5. An incoming call received with the app swiped away (§15.6).

**Step 2 is the gate.** Android-to-Android proves very little; Android-to-web
proves the encryption layers agree. Step 5 is the gate for Option A — if the
WebView path cannot be driven from a cold start by a push notification, it is
disqualified regardless of how well steps 1–4 went.

**Provisional lean: Option A**, for the same reason §11.4 leans Rust — the
calling stack is the fastest-moving part of the ecosystem, and a solo operator
tracking it by hand in Kotlin is a standing commitment with no end date. Taking
the widget means taking upstream's compatibility work as a dependency instead of
a liability.

This lean is weaker than §11.4's and should not be treated as settled. Record
the findings here and raise the confidence marker when it is.

### 17.6.2 Spike evidence, 2026-09-13 — the backend is proven, the client is not

**The whole server-side chain works, verified end to end:**

```
Matrix user → Synapse OpenID token
            → lk-jwt-service
            → federation openid/userinfo (LAN, TLS)
            → LiveKit JWT
            → SFU WebSocket: ADMITTED
```

The last step is the one that matters and it is not inferred. Opening LiveKit's
signalling socket with a token minted by our own chain returned a JoinResponse
containing the hashed room and the identity
`@ver211011:nexlink.thvjq.com.au:SPIKEDEV`. **The SFU validated the token,
accepted the room grant and admitted the participant.**

Element Web, signed in against this homeserver, independently confirms the
discovery half: it reads `rtc_foci` as
`https://nexlink.thvjq.com.au/livekit/jwt`, fetches
`/_matrix/client/unstable/org.matrix.msc4143/rtc/transports` (**200**) and
starts its `GroupCallEventHandler`.

#### What this does and does not settle

It settles that **§24.1.1's free hosted SFU is a working MatrixRTC backend**, and
that nothing about the CGNAT constraint (§17.4) survives into the client.

It does **not** settle §17.6. Steps 1–5 of §17.6.1 all require a client that can
place a call, and that client is the thing being chosen between. The spike's
gate — step 2, a third participant from Element Web — is still unrun.

#### It does move the lean, though, and in Option A's favour

The backend that now works is the **standard MatrixRTC contract**, and Element
Call is the reference implementation of exactly that contract. The evidence is
that everything upstream expects is already in place and answering correctly.
Option B would have to reimplement the call-membership state machine and, worse,
**match Element Call's E2EE layer** against a moving target — §17.6's own
"Against" column, and the failure mode it names is a call that connects and
shows black frames.

Set against that, the one thing measured today that counts *against* Option A:
Element Web's `element_call` config has no `url`, so it loads the widget from
**`call.element.io`** — a third party serving executable code into the call
surface of a privacy product. Self-hosting Element Call removes that and is a
deployment, not a rewrite. **If Option A is taken, self-hosting the widget is
not optional.**

#### Two operational findings from the spike

**Login rate limiting bites automation, not users.** `rc_login` is
`per_second: 0.05, burst_count: 5` — one login per twenty seconds. Repeated
browser-driven sign-ins hit `M_LIMIT_EXCEEDED` and the *browser* reports only
"There was a problem communicating with the homeserver". Fine for real use;
plan around it in any automated client test.

**The SFU never learns the Matrix room id.** The room in both the JWT grant and
the JoinResponse is a hash. See §24.1.2.

---

## 17.6.3 Decision: Option A, with the widget self-hosted — 2026-09-14

**§17.6 is resolved. Option A: the Element Call widget.**

The reasoning, from §17.6.2's evidence rather than from the provisional lean:

1. The backend that now works is the **standard MatrixRTC contract**, and
   Element Call is its reference implementation. Everything upstream expects is
   already in place and answering correctly.
2. Option B would have to reimplement the call-membership state machine **and
   match Element Call's E2EE layer** against a moving target. §17.6's own
   "Against" column names the failure mode: a call that connects and shows black
   frames.
3. §11.4's argument transfers intact — the calling stack is the fastest-moving
   part of this ecosystem, and a solo operator tracking it by hand in Kotlin is
   a standing commitment with no end date.

**The condition attached: the widget is self-hosted.** Element Web's
`element_call` config had no `url`, so it defaulted to `call.element.io` — a
third party serving executable code into the call surface of a privacy product.
That was the one measured fact counting against Option A, and self-hosting
removes it. It is a deployment, not a rewrite.

### Deployed

`ghcr.io/element-hq/element-call` as TrueNAS app `nexlink-social-call` on port
8065, proxied at `https://nexlink.thvjq.com.au/call/`. Its config points at this
homeserver and at the §24.1.2 JWT service.

**One routing problem, and the tidy solution.** Element Call references its
assets **absolutely** (`/assets/...`). Rewriting them with `sub_filter` worked
for the HTML and failed for the 29 sound and translation files the bundle
constructs at runtime. Element Web does not use `/assets/` — it serves
`/bundles/`, `/vector-icons/` and `/i18n/` — so `/assets/` is proxied straight
to the Element Call container and no rewriting is needed. Only `/config.json`,
which both apps genuinely want, is rewritten.

### A deployment trap worth recording: Cloudflare cached the 404s

While `/assets/` was briefly missing, Cloudflare cached the 404 responses **for
four hours** (`cache-control: max-age=14400`, its default for errors), and kept
serving them after the origin was fixed.

It presented as a contradiction: `curl` returned 200 while the browser returned
404 for the same URL. The difference was the **`Origin` header** — module
preloads are CORS-mode, and CORS responses get their own cache key, so the
poisoned entry was only reachable with that header. Reproduced exactly:

```
curl                         -> 200
curl -H 'Origin: https://…'  -> 404, cf-cache-status: HIT, age: 168
curl -H 'Origin: …' '…?bust' -> 200
```

Verified against the origin chain with the `Origin` header present: **8 of 8
assets serve correctly**. The deployment is right; the edge was stale.

**The lesson is about sequencing, not nginx.** Deploying a path in a broken
state, even for a minute, can poison an edge cache for hours behind a cache key
you are not testing with. Validate the origin before the path is reachable
publicly, and when a browser and `curl` disagree, compare the request headers
before suspecting the server.

---

## 17.6.4 Client spike results, 2026-09-14 — Option A works on hardware

§17.6.2 proved the backend and said plainly that the client half was unproven.
It is proven now. A Galaxy S21 placed a real MatrixRTC call from the NexLink
Social app, and a second account on the same handset joined it.

### What was verified

| §17.6.1 step | Result |
|---|---|
| 1. Two parties in a call, media both ways | **Pass** — two accounts, each seeing the other's decrypted video, `Subscribed: video camera TR_… of @push204943:…` |
| 2. A third participant from Element Web | **Pass** — the gate; see §17.6.4.4 |
| 3. Screen share from Android | Not run (§18) |
| 4. E2EE on, keys rotate | **Partly** — `encrypted=true` per participant and `MatrixKeyProvider: Sent new key to livekit … encryptionKeyIndex=0`; rotation on leave not yet observed |
| 5. Incoming call from a cold start | Not run (§15.6) |

Supporting evidence, all from the device:

- `connected to Livekit Server edition: 1, version: 1.13.6, region: Australia,
  nodeId: NM_OSYDNEY1A_…` — §24.1.1's free hosted SFU, from the phone.
- `livekitRoom.connect SUCCESS wss://nexlink-social-ro8eroxb.livekit.cloud`.
- `org.matrix.msc3401.call.member` appears in room state with
  `focus_active.type: livekit` — written **through the app's own Matrix
  session**, which is the point of the widget driver.
- On hang-up, both members' `m.call.member` events are emptied to `{}`:
  no ghost participants (§17.7).

**Step 2 was the gate, and it passes** — §17.6.4.4.

### 17.6.4.1 The host bridge: three things that had to be right

The widget is Element Call, unmodified, in a WebView. Everything below is the
host side — the part §17.6's "Against" column warned would be the real work.

**1. A real iframe, not a shimmed `window.parent`.** The first attempt loaded
the widget as the top-level page, replaced `window.parent` with an object
forwarding to native, and delivered replies by dispatching a synthetic
`MessageEvent`. It got further than expected — the console showed
`[PostmessageTransport] Sending object` and *"Using a matryoshka client"*, so
the widget really was in widget mode — and then every request timed out:

```
non-fatal error getting supported client versions: Error: Request timed out
Could not send DeviceMute action to widget  Error: Request timed out
```

`matrix-widget-api` validates that a reply came from the frame it posted to.
A synthetic event cannot satisfy that; `MessageEvent.source` must be a real
window. The fix was to stop faking it: put the widget in an actual iframe
inside a host page, and let parent and child talk the ordinary way.

**2. The host page must be served from the real origin.** `loadDataWithBaseURL`
is the obvious way to inject a generated page, and it is wrong here: the
resulting document's origin is not reliably the base URL's, and the transport
compares `event.origin` against `window.origin` before accepting anything. A
mismatch is silent — no error, the message is simply dropped. The host page is
therefore served by `shouldInterceptRequest` at one made-up path on the real
origin, so parent and iframe are genuinely same-origin.

**3. `MODIFY_AUDIO_SETTINGS`.** With `RECORD_AUDIO` granted and the foreground
service running, the first working call was still silent, and the only evidence
was two lines from inside Chromium:

```
E chromium: audio_manager_android.cc:885 Unable to select communication device!
I chromium: CONSOLE "NotReadableError: Could not start audio source"
```

WebRTC puts the device into communication mode before opening the input, and
`AudioManager.setCommunicationDevice()` needs that permission. Without it the
call connects, encrypts, publishes `m.call.member` and joins the SFU —
everything except audio. **The failure is invisible at the Matrix layer**, which
is what makes it worth recording: every log a Matrix developer would think to
check said the call was fine.

### 17.6.4.2 Actions the host must answer

The widget sends these to the host. Observed live, in order, on a real call:

```
capabilities · notify_capabilities · content_loaded · supported_api_versions
get_openid · openid_credentials · org.matrix.msc4515.get_rtc_transports
send_event · update_state · org.matrix.msc4157.update_delayed_event
io.element.join · io.element.device_mute · set_always_on_screen
io.element.close
```

The Rust `WidgetDriver` handles the Matrix ones. Two are the host's own job and
neither is optional:

- **`io.element.close`** — the widget's red hang-up button tears down media
  correctly and then cannot close a window it does not own. Ignoring this left
  the user on a black screen with the foreground service still running, which
  is §17.7's crash row seen from the device side. The host finishes the
  activity.
- **`set_always_on_screen`** — a call is watched, not touched. Without
  `FLAG_KEEP_SCREEN_ON` the display sleeps mid-conversation.

`io.element.device_mute` is rejected by the driver as an unknown variant. That
is correct for now: it exists to sync mute state with a **native** call UI,
which is §19.5 and is not built. Revisit it there.

### 17.6.4.3 Noise that is not a bug

Two log lines look alarming and are not:

- `MissingKey: key set not found for <self> at index 0`, repeating about once a
  second and then `Suppressing further decryption errors` — the SFU echoing the
  publisher's own track back. It stops on its own and does not affect remote
  decryption, which was confirmed working in the same call.
- `Failed to resolve address for ip-…-.host.livekit.cloud, errorcode: -105` —
  a STUN host lookup. ICE succeeded by other candidates.

---

---

### 17.6.4.4 Step 2 — the gate — passes

Element Web, signed in as a second account and driven headlessly with
Chromium's fake camera, joined the same call. **Both directions carry decoded
video:**

- On the phone: peer195112's tile shows Chromium's green test pattern with its
  timer running — decoded frames, not a still. `Subscribed: video camera
  TR_VCj8u5guzKF9Br of @peer195112:…`, `Subscribed: audio microphone TR_AMv…`,
  and `matrixLivekitMembers$ updated [@peer195112:…|IFZKNPRYRR]`.
- On the web: call190359's camera fills the frame, the phone filming a desk.

§17.6.1 called step 2 the gate because "Android-to-Android proves very little;
Android-to-web proves the encryption layers agree". They agree.

Element Web's toolbar also carries a screen-share control that Android's does
not — the same measurement §18.2.1 records from the other side.

#### A correction to §17.6.2

§17.6.2 recorded that Element Web's missing `element_call.url` meant it loaded
the widget from `call.element.io`. **That is not what this build does.** The
iframe URL observed live is

```
https://nexlink.thvjq.com.au/widgets/element-call/index.html?widgetId=…
```

Element Web 1.12.27 ships its own copy of Element Call at `/widgets/element-call/`
and uses it in preference to the configured URL — verified inside the container.
So no third party was ever serving code into the call surface here; the risk
§17.6.2 named was real in principle and absent in fact.

`element_call.url` is set anyway (§20.4.1). It costs nothing, and it is the
setting that decides the question rather than leaving it to what a future
Element Web release happens to bundle.

#### The harness, for whoever runs this next

Two things cost most of the time and neither is about calling:

- **Element greets a fresh web session with a queue of modals** — "Confirm your
  digital identity" (whose close button has no accessible name), then "Verify
  this device", then "Enable desktop notifications" — each covering the room
  list. Automation has to clear all three.
- **`rc_login` is one login per twenty seconds** (§17.6.2 records this). Several
  scripted attempts in a row earn `M_LIMIT_EXCEEDED`, which the browser reports
  only as *"There was a problem communicating with the homeserver"*.

---

## 17.7 Call lifecycle

Independent of which option wins.

| Stage | What happens |
|---|---|
| **Place** | Client writes its call-membership state into the room, schedules its delayed "left" event, fetches a JWT, connects to the SFU |
| **Ring** | Other devices see the membership state via sync; those with push receive a high-priority notification (§13.6.3) and post a full-screen-intent notification (§15.6) |
| **Answer** | Callee writes its own membership state, fetches its own JWT, connects. The foreground service starts **now**, not before (§15.4.1) |
| **In call** | Media flows client↔SFU. Membership state refreshed to keep the delayed event at bay |
| **Leave** | Membership state removed, SFU disconnected, foreground service stopped cleanly (§15.2.1) |
| **Crash** | Nothing removes the state — so the delayed event fires and does it. This is why §17.3.1 exists |

### 17.7.1 Ringing is state, not a message

There is no "ring" event. A client rings because it observes membership state
appear in a room it is in. Two consequences worth stating because they surprise
people:

- **A call to an offline device is not lost.** The state persists; the device
  rings when it syncs, and the UI must decide whether a two-minute-old call is
  still worth ringing for. Proposed: ring if the membership is under 60 seconds
  old, otherwise present it as missed.
- **Every one of the user's devices rings.** Answering on one must visibly
  cancel the others, which is a UI obligation (§19), not something the protocol
  does.

---

## 17.8 Group calls and capacity

| Participants | Expectation |
|---|---|
| 2 | Baseline. Must be flawless. |
| 3–4 | The §1.5 success criterion: four participants and one screen share, on a mid-range phone, over domestic broadband. |
| 5–8 | Should work. Video quality degrades by design. |
| 9+ | Not a target. No hard block, but untested and unsupported. |

The success criterion names four because that is what the product is for. Design
to it and let the ceiling be wherever it lands.

**Simulcast** is what makes this survivable: each publisher sends several
quality layers and the SFU forwards the appropriate one per subscriber, so a
phone showing four small tiles receives four low-resolution streams rather than
four high-resolution ones it immediately downscales. It must be on. Verify it
is, because the symptom of it being off is battery drain and heat rather than
an error.

---

## 17.9 Open questions

- **Does the chosen client option support hold, and what happens to a Social
  call when a cellular call arrives?** §19 specifies the required behaviour;
  whether Option A can implement it is unverified and is a genuine risk to that
  option.
- **What is the SFU's behaviour when the last participant leaves — is the room
  torn down promptly, and does anything need to reap it?** Matters for §28,
  where idle rooms are a cost.
- **Bandwidth ceiling on Starlink.** The SFU is on a VPS (§17.4.1), so Willard's
  uplink is not in the media path. But the operator's own testing will be over
  Starlink, and Starlink's jitter is not representative of what users see.
  Test from a non-Starlink network before drawing conclusions about quality.
- **Is `max_event_delay_duration: 24h` the right value?** It bounds how long a
  ghost participant can persist if a client dies at exactly the wrong moment.
  Shorter is safer and costs more refresh traffic.
