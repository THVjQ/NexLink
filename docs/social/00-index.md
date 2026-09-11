# NexLink Social — Technical Specification

**Status:** complete draft, pre-prototype
**Written against:** NexLink versionCode 35 / 2.5.5
**Author:** drafted with Claude Code, for THVjQ
**Last revised:** 2026-09-11 — Parts IV–VIII added

---

## What this document is

A full build and operations specification for **NexLink Social** — an encrypted
messaging and calling product delivered as a companion app to NexLink, built on
the Matrix protocol with a self-operated homeserver.

It covers the product decisions, the security model, the client architecture, the
server infrastructure, the operational obligations, and the delivery plan.

## What this document is not

It is not a validated design. Nothing in it has been prototyped. **Phase 1
([§33.2](33-phase-plan.md)) exists specifically to test the assumptions the rest
of this document is built on**, and several chapters — particularly Part III,
client architecture — should be expected to change once it has run.

Chapters carry a confidence marker in their front matter:

| Marker | Meaning |
|---|---|
| `DURABLE` | Unlikely to change. Product decisions, obligations, infrastructure shape. |
| `REVISABLE` | Correct as written, but depends on library and platform choices that move. Re-read after the spike. |
| `SPECULATIVE` | Best current understanding, unverified. Treat as a starting point for investigation, not instruction. |

## Decisions already fixed

These were settled before drafting and are treated as constraints throughout,
not options:

1. **Separate app**, same repository, same signing key, own application ID.
   Rationale in §1.4.
2. **Matrix protocol**, self-hosted homeserver. Rationale in §5.1.
3. **Screen sharing in call only.** No recording to file, in any form, in any
   release covered by this document. Rationale in §17.1.
4. **Invite-only registration**, with a mandatory acceptance gate before an
   account is created. Rationale in §9.
5. **Federation disabled.** Private homeserver. Rationale in §21.3.

---

## Table of contents

### Part I — Foundations

| § | Chapter | Confidence |
|---|---|---|
| 1 | [Scope, goals and non-goals](01-scope.md) | `DURABLE` |
| 2 | [Fixed decisions and their consequences](02-decisions.md) | `DURABLE` |
| 3 | [Threat model and security posture](03-threat-model.md) | `DURABLE` |
| 4 | [Regulatory and platform obligations](04-obligations.md) | `DURABLE` |

### Part II — Protocol and identity

| § | Chapter | Confidence |
|---|---|---|
| 5 | [Matrix, as it applies here](05-matrix-primer.md) | `DURABLE` |
| 6 | [Identity, usernames and account lifecycle](06-identity.md) | `REVISABLE` |
| 7 | [The no-email path and recovery keys](07-recovery.md) | `REVISABLE` |
| 8 | [Multi-device, cross-signing and verification](08-multidevice.md) | `REVISABLE` |
| 9 | [Invites and the acceptance gate](09-invites.md) | `DURABLE` |

### Part III — Client architecture

| § | Chapter | Confidence |
|---|---|---|
| 10 | [Module layout and Gradle wiring](10-modules.md) | `REVISABLE` |
| 11 | [SDK selection: Rust SDK vs. matrix-android-sdk2](11-sdk-selection.md) | `SPECULATIVE` |
| 12 | [Local store and encryption at rest](12-local-store.md) | `REVISABLE` |
| 13 | [Sync, offline behaviour and push](13-sync-push.md) | `REVISABLE` |
| 14 | [Chat surface: timeline, reactions, attachments](14-chat-surface.md) | `REVISABLE` |
| 15 | [Foreground services and background execution](15-foreground-services.md) | `DURABLE` |
| 16 | [NexLink integration and the unified inbox](16-nexlink-integration.md) | `REVISABLE` |

### Part IV — Realtime

| § | Chapter | Confidence |
|---|---|---|
| 17 | [Call architecture](17-call-architecture.md) | `REVISABLE` |
| 18 | [Screen sharing in call](18-screen-sharing.md) | `REVISABLE` |
| 19 | [Telecom integration and call UI](19-telecom-call-ui.md) | `REVISABLE` |

### Part V — Web

| § | Chapter | Confidence |
|---|---|---|
| 20 | [Element Web deployment and skinning](20-web-client.md) | `REVISABLE` |

### Part VI — Infrastructure

| § | Chapter | Confidence |
|---|---|---|
| 21 | [Reference topology](21-topology.md) | `DURABLE` |
| 22 | [Homeserver deployment](22-homeserver.md) | `REVISABLE` |
| 23 | [PostgreSQL, backup and restore](23-postgres-backup.md) | `DURABLE` |
| 24 | [LiveKit and coturn](24-livekit-coturn.md) | `REVISABLE` |
| 25 | [Media store and retention](25-media-store.md) | `DURABLE` |
| 26 | [DNS, TLS and delegation](26-dns-tls.md) | `DURABLE` |
| 27 | [Observability and alerting](27-observability.md) | `DURABLE` |
| 28 | [Capacity planning and cost model](28-capacity-cost.md) | `DURABLE` |

### Part VII — Operations

| § | Chapter | Confidence |
|---|---|---|
| 29 | [Runbooks](29-runbooks.md) | `DURABLE` |
| 30 | [Incident response](30-incident-response.md) | `DURABLE` |
| 31 | [Moderation and abuse handling](31-moderation.md) | `DURABLE` |
| 32 | [Data subject processes](32-data-subject.md) | `DURABLE` |

### Part VIII — Delivery

| § | Chapter | Confidence |
|---|---|---|
| 33 | [Phase plan and acceptance criteria](33-phase-plan.md) | `DURABLE` |
| 34 | [Testing strategy](34-testing.md) | `REVISABLE` |
| 35 | [Play Store submission](35-play-submission.md) | `DURABLE` |
| 36 | [Risk register](36-risk-register.md) | `DURABLE` |
| 37 | [Open questions](37-open-questions.md) | — |

---

## Reading order

If you read nothing else before the spike: **§1, §2, §3, §9, §21, §28, §33.**

Those seven chapters contain every decision that is expensive to reverse. The
rest can be read as you reach the phase that needs it.

---

## Where the build stands

**Phase 0 and Phase 1 are built** ([§33.1.1](33-phase-plan.md),
[§33.2.1](33-phase-plan.md)). The homeserver is live on Willard and the Android
module skeleton compiles. **Next: [§33.3 — the SDK bake-off](33-phase-plan.md),
five days, timeboxed.**

The one thing blocking public access is the **Cloudflare Tunnel routing**, which
has to be added in the Cloudflare dashboard because the tunnel is token-based —
see [§33.2.2](33-phase-plan.md).

**The domain is decided: `nexlink.thvjq.com.au`** (2026-09-11,
[§26.2.1](26-dns-tls.md)). MXIDs are `@username:nexlink.thvjq.com.au`, and that
string is permanent from the moment the first account exists.

One hostname serves everything on Willard, fanned out by path, because
Cloudflare's free Universal SSL does not cover a second-level subdomain like
`matrix.nexlink.thvjq.com.au` ([§26.2.2](26-dns-tls.md)).

### Findings from completing Parts IV–VIII

Three things were established against live systems on 2026-09-11 and changed the
plan:

1. **Willard cannot host the media path.** It is behind Starlink CGNAT with no
   inbound connectivity and — checked specifically — no IPv6 at all. A WebRTC
   SFU must be reachable inbound, so LiveKit and coturn need a small VPS with a
   public IP ([§17.4](17-call-architecture.md), [§21.2](21-topology.md)).
   Everything else runs on Willard as TrueNAS custom apps.
2. **Messaging is unaffected by that.** Phases 0–3 run entirely on Willard, so
   the project has **no recurring cost until calling starts** in phase 4
   ([§21.2.1](21-topology.md), [§28.4](28-capacity-cost.md)) — and a
   messaging-only product is a clean place to stop
   ([§33.9.1](33-phase-plan.md)).
3. **The SDK question has moved.** `matrix-rust-components-kotlin` released
   `sdk-v26.09.9` on 2026-09-09; `matrix-android-sdk2`'s last release was
   `v1.6.50` on 2026-02-04. Seven months against two days is the divergence
   [§11.2.1](11-sdk-selection.md) predicted. It strengthens the lean toward the
   Rust SDK without settling it — [§33.3](33-phase-plan.md) still gates on
   cross-signing, verification and key backup actually working.

### Upstream versions cited

Verified 2026-09-11.

| Component | Version | Released |
|---|---|---|
| Synapse | `v1.160.0` | 2026-09-02 |
| LiveKit | `v1.13.6` | 2026-08-26 |
| lk-jwt-service | `v0.7.0` | 2026-09-10 |
| Element Call | `v0.25.0` | 2026-09-01 |
| Element Web | `v1.12.27` | 2026-09-01 |
| matrix-rust-components-kotlin | `sdk-v26.09.9` | 2026-09-09 |
| matrix-android-sdk2 | `v1.6.50` | 2026-02-04 |
