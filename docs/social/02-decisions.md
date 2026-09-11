# §2 — Fixed decisions and their consequences

> **Confidence:** `DURABLE`
> These are constraints, not options. Each entry records the decision, what it
> buys, what it costs, and what reversing it would take — so that a future
> reversal is a considered act rather than a drift.

---

## 2.1 Why decisions are recorded this way

The failure mode this chapter exists to prevent is the decision that erodes.
Someone hits friction in month four, quietly relaxes a constraint to get past
it, and the reasoning that produced the constraint is nowhere to be found. By
month eight the product has a plaintext fallback nobody remembers adding.

Each decision below therefore carries a **reversal cost** — an honest estimate
of what undoing it would involve. A decision with a low reversal cost can be
revisited casually. A decision with a high one cannot, and knowing which is
which is the point.

---

## 2.2 D1 — Separate application

**Decision.** NexLink Social ships as its own Android application with its own
application ID (`com.thvjq.nexlink.social`), from the same repository, under the
same signing key.

**Buys.** Regulatory isolation of the operator's core product; independent
release trains; no download-size penalty for NexLink users who never opt in.

**Costs.** Two Play listings; a user-visible install step; cross-app state
coherence; slower monorepo builds.

**Reversal cost: high.** Merging the apps later means a single application ID,
which means migrating every Social account's device identity into a different
package — and Android's Keystore does not move keys between packages. In
practice a merge would require every user to re-verify every device. Treat as
effectively irreversible once accounts exist.

Full argument: §1.4.

---

## 2.3 D2 — Matrix protocol, self-hosted

**Decision.** Matrix, with a homeserver operated by THVjQ. Not a bespoke
protocol, not Signal-protocol-only, not XMPP.

**Buys.** Audited E2EE (Olm/Megolm); multi-device and cross-signing already
solved; a recovery-key model that matches the product requirement almost
exactly; a free, maintained web client (§20); an admin API; a specification that
outlives any single library.

**Costs.** A large protocol surface with features this product does not need;
homeserver operational burden; coupling to the ecosystem's release cadence.

**Reversal cost: very high.** Migrating to a different protocol means migrating
identity, history and device trust. There is no meaningful in-place path. This
is the foundational bet of the project.

**The alternatives, and why they lost:**

| Alternative | Why rejected |
|---|---|
| Signal Protocol directly (libsignal) | Gives the ratchet, nothing else. Identity, transport, multi-device, backup, groups and calling all remain to be designed and built. Roughly the same cryptography, an order of magnitude more product work. |
| XMPP + OMEMO | Multi-device and history sync remain persistently awkward. Smaller mobile SDK ecosystem. No comparable web client to adopt. |
| Bespoke protocol | Would require designing and auditing cryptography for a solo project. Not defensible. |
| A commercial BaaS (Stream, Sendbird) | Vendor holds message content, or E2EE is bolted on. Contradicts §3.5. Per-user pricing scales badly against the cost model in §28. |

---

## 2.4 D3 — Screen sharing only, never recording

**Decision.** Users may share their screen into a live call. The application
provides no capability to record a call, a screen, or any participant's
audio or video to a file, in any release covered by this document.

**Buys.** Removes an entire category of consent law from the compliance surface;
materially simplifies Play review; removes storage, retention and disclosure
questions about recorded media.

**Costs.** A feature some users will ask for.

**Reversal cost: medium, and the cost is legal rather than technical.** The
`MediaProjection` plumbing for sharing is most of the plumbing for recording —
the code is nearly free. What is not free is the jurisdiction-by-jurisdiction
analysis of two-party consent, the notification obligations, the retention
policy for recorded files, and the Play review posture. **Any proposal to add
recording is a legal decision first and a development ticket second.**

Implementation constraints that follow (§18): sharing is initiated explicitly
per session; every participant sees a persistent indicator for its full
duration; sharing stops when the call ends, with no background continuation.

---

## 2.5 D4 — Invite-only, with a mandatory acceptance gate

**Decision.** No account can be created without a valid invite token, and no
account is created until the invitee has completed the acceptance flow (§9.6).

**Buys.** Spam control; predictable growth and therefore predictable cost;
traceable accountability via the invite tree; a defensible record of consent and
age confirmation; a materially lighter regulatory profile than an open service.

**Costs.** Growth is capped by design. Onboarding has more steps. The operator
must administer tokens.

**Reversal cost: low in one direction, high in the other.** Opening
registration is a configuration change. Closing it after a public launch means
removing accounts from real people. **Start closed.**

---

## 2.6 D5 — Federation disabled

**Decision.** The homeserver does not federate with the public Matrix network.

**Buys.** Dramatically lower resource consumption — federation traffic dominates
the load of a small public homeserver; a closed abuse surface; no moderation
obligations arising from other servers' users; a much simpler operational and
legal position.

**Costs.** Users cannot talk to people on other Matrix servers. The product is
an island.

**Reversal cost: medium, and asymmetric.** Enabling federation later is a
configuration change plus a substantial capacity and moderation re-plan.
Disabling it after users have federated contacts breaks live conversations.
Both directions are possible; the second is user-hostile.

**Consequence for the threat model:** a closed server means the operator is the
only server that sees metadata. That is simpler to reason about and simpler to
disclose (§3.7).

---

## 2.7 Decisions deliberately left open

Recorded so their openness is intentional rather than overlooked. Each is
resolved in the chapter noted.

| Open decision | Resolved in | Why not decided yet |
|---|---|---|
| Which Matrix client SDK | §11 | Depends on the state of the Rust SDK's Kotlin bindings at spike time. Deciding now would be guessing. |
| Synapse or conduwuit | §22 | Should be decided on observed resource use during the spike, not on documentation. |
| Element Web or a bespoke web client | §20 | Element Web first regardless; a bespoke client is a later product question. |
| Bearer vs. targeted invites | §9.9 | Product call, low reversal cost either way. |
| Push via FCM, UnifiedPush, or both | §13 | FCM is the pragmatic default; UnifiedPush matters only if de-Googled users are a target audience. |
| Minimum age | §4 | Depends on which jurisdictions are actually served, which depends on where invites go. |

---

## 2.8 The invariants these produce

Collected from the decisions above and §3.5, in the form a code reviewer can
check against. If a change violates one of these, it is not a bug — it is a
decision reversal, and needs treating as one.

1. No code path sends message content the server can read.
2. No private key material leaves the device unencrypted, for any reason.
3. No account is created without a redeemed invite token and a completed
   acceptance record.
4. No API is called that captures screen, camera or microphone content to
   persistent storage.
5. The homeserver's federation listener is not enabled.
6. NexLink's own package gains no new permissions and no new background work as
   a result of Social existing.
7. Crash reports, logs and analytics carry no message content, room IDs, user
   IDs or key material.
