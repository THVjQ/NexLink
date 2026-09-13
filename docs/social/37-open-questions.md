# §37 — Open questions

> **Confidence:** —
> A single index of everything this document leaves undecided, with who decides
> it and what it blocks. Maintained as questions are answered — §29.7's
> quarterly review includes closing out the ones that have become answerable.

---

## 37.1 Blocking — decide before the next phase starts

| # | Question | Decided by | Blocks | § |
|---|---|---|---|---|
| Q2 | Can the acceptance gate run before the account exists — does Synapse permit reserve-then-redeem, or must the invite service proxy registration? | Phase 1 finding | Shape of the invite service | §22.9 |
| Q3 | Minimum age, which depends on which jurisdictions are served, which depends on where invites go. | Product owner + legal | Acceptance gate copy, phase 5 | §2.7, §4 |

**Q1 is closed** — see §37.7. Q2 is now the one phase 1 resolves.

---

## 37.2 Product decisions, low reversal cost

| # | Question | Lean | § |
|---|---|---|---|
| Q4 | Bearer invites, or targeted to a person? | **Bearer**, short expiry | §9.9 |
| Q5 | Can a user see who they invited? | **No** — leaks the tree | §9.9 |
| Q6 | What happens to an invitee when their inviter deletes? | **Nothing** — the tree is history, not a live dependency | §9.9 |
| Q7 | Ship messaging-only and defer calling? | **On the table from the start** — §21.2.1 makes the cut clean | §33.9.1 |
| Q8 | Is the web client reachable before phase 6? | **Cloudflare Access-gated until then** | §20.8 |
| Q9 | Allow mobile browser sessions? | Allow, do not promote | §20.8 |
| Q10 | Invite quota values (0 / 3 / 5) | Tune against phase 6 behaviour | §9.5 |

---

## 37.3 Technical, resolved by a spike

| # | Question | Resolved in | § |
|---|---|---|---|
| Q11 | Which Matrix SDK | Phase 2, timeboxed to 5 days | §11.7 |
| Q12 | Element Call widget, or native LiveKit? | Phase 4 spike, 5 days | §17.6.1 |
| Q13 | Does the recovery key round-trip Android ↔ Element Web in both representations? | Phase 3 | §5.9, §7.4.3 |
| ~~Q14~~ | ~~Device-management surface?~~ **ANSWERED 2026-09-11: no.** No device-listing API in the bindings; §8.6 needs raw C-S API calls, and new-device alerts need polling. Bounded extra work, budget it in phase 3 | §11.7.3, §8.9a |
| Q15 | How is the SDK's own store encrypted, and does it satisfy §12? | Phase 2 | §11.9 |
| Q16 | What does an SDK breaking change cost? Measure once, deliberately. | Phase 2 | §11.9 |
| Q17 | One `rtc.` hostname or two, given the services are on different hosts? | Phase 4 — try the documented shape first | §26.4.1 |
| Q18 | Does the Android client honour TURN over TCP 443? | Phase 4 | §24.9 |
| Q19 | Does a full media store fail cleanly? | Phase 1 | §25.9 |
| Q20 | Does `user_directory.search_all_users` expose more than exact-match lookup? | Phase 1 | §22.9 |
| Q21 | Does the SDK provide a test harness, or does CI need a real homeserver? | Phase 2 | §34.9 |

---

## 37.3a Disclosure

| # | Question | Lean | § |
|---|---|---|---|
| **Q44** | **Reactions are NOT encrypted** — the server sees which emoji, from whom, on which event. §9.6.1's screen does not mention it. Add a line to the "We can see" column, or stop using reactions in encrypted rooms? | **Add the line.** §9.6.1 already argues the honest version is the more trustworthy one, and dropping reactions contradicts §1.2 | §5.5 — **ANSWERED 2026-09-13: disclosed.** Encrypting reactions was asked for and is not available; see §9.6.1. |

---

## 37.4 Architecture, decide when it hurts

| # | Question | Lean | § |
|---|---|---|---|
| Q22 | Does `:social-ui` stay separate or fold into `:social`? | Fold initially, split when it hurts | §10.10 |
| Q23 | Should `:social` live in this repository at all? | Same repo; revisit if build times degrade | §10.10, §2.7 |
| Q24 | Is the invite service its own app or a route beside Synapse? | **Separate** — it is the only unauthenticated write path | §21.9 |
| Q25 | Synapse or continuwuity? | **Settled: Synapse** (§22.1). Revisit only on a 10× resource surprise | §22.1 |
| Q26 | FCM, UnifiedPush, or both? | FCM; UnifiedPush only if de-Googled users are a target | §2.7, §13 |
| Q27 | WAL archiving for point-in-time recovery? | No — complexity for a service whose primary copy is on devices | §23.8 |
| Q28 | Per-user media quota? | None natively; the invite tree makes abuse accountable. A real gap | §25.9 |
| Q29 | Server-side retention on? | No — no readable content to purge; metadata bounded separately | §25.6 |

---

## 37.5 Operational

| # | Question | Note | § |
|---|---|---|---|
| Q30 | VPS provider and region | AU region, dedicated IPv4, shaped transfer, snapshots | §24.9 |
| Q31 | Where do the external health checks run from? | Must be outside Willard. The VPS is the obvious host | §27.8 |
| Q32 | What is an acceptable overnight outage, and are users told? | One operator, who sleeps. Should be stated honestly at the gate | §27.8, §30.9 |
| Q33 | Who is the legal contact, and is the relationship in place before it is needed? | §30.5 assumes one exists | §30.9 |
| Q34 | Should the dump be encrypted at rest? | Mandatory once it ships to B2 | §23.8 |
| Q35 | Does TrueNAS's own alerting cover any of §27.3 once SMTP exists? | Check before building duplicates | §27.8 |
| Q36 | Does split DNS break TLS for strict clients? | Test before rolling out | §26.7 |
| Q37 | Who tests the OEM matrix with one phone? | Possibly the phase 6 cohort — making device diversity a selection criterion | §34.9 |

---

## 37.6 Legal and compliance

| # | Question | Needs | § |
|---|---|---|---|
| Q38 | Does Play accept this UGC declaration? | Confirm exact wording pre-submission. **Most likely cause of rejection** | §31.8, §35.3 |
| Q39 | Pseudonymising the inviter MXID on deletion (§32.3.3) — defensible? | Legal input | §32.8 |
| Q40 | Obligation when a user reports illegal content the operator cannot verify? | Legal input, **before** the first case | §31.8 |
| Q41 | Which jurisdictions actually apply? | Knowable, because invite-only. Exploit that | §32.8 |
| Q42 | Is a canary warrant worth it? | Leaning no — a permanent commitment for a small signalling gain | §30.9 |
| Q43 | Formal privacy notice sufficient, or is a designated contact point needed? | §4.3.2 leans yes | §32.8 |

---

## 37.7 Questions answered while completing this document

Recorded so they are not re-opened.

| Question | Answer | Where |
|---|---|---|
| **Q1 — the domain, and therefore `server_name`** | **`nexlink.thvjq.com.au`**, decided 2026-09-11. Permanent from the moment the first account exists | §26.2.1 |
| Delegate to a `matrix.` subdomain? | **No** — Cloudflare's free Universal SSL does not cover second-level subdomains, so one hostname fans out by path instead | §26.2.2 |
| Where does the homeserver run? | **Willard, as TrueNAS custom apps** | §21.1 |
| Can the SFU run on Willard too? | **No.** Starlink CGNAT, no inbound, and no IPv6 — verified live 2026-09-11. A VPS is required, from phase 4 | §17.4, §21.2 |
| Synapse or conduwuit? | Synapse. conduwuit was archived upstream; Synapse is what this product's required features are specified against | §22.1 |
| Legacy VoIP or MatrixRTC? | MatrixRTC. Legacy cannot do group calls at all | §17.2 |
| Element Web or bespoke web client? | Element Web, configured not forked | §20.1 |
| Object storage or filesystem for media? | Filesystem on the replicated pool, until the pool is the constraint | §25.3 |
| `InCallService` or self-managed `ConnectionService`? | **Self-managed.** NexLink is already the dialer; two dialers from one developer is not a viable posture | §19.2 |
| Peer-to-peer for 1:1 calls? | No. One stack for both, and P2P would leak participants' IPs to each other | §17.2.1 |
| Synapse workers? | Not at this scale | §21.7 |

---

## 37.8 How this chapter is maintained

1. A question answered moves to §37.7 with its answer and a section reference.
2. A new question gets an ID and a home in one of the sections above.
3. **IDs are never reused.** A retired Q-number stays retired, so that a
   reference in a commit message or a note still resolves.
4. §29.7's quarterly review asks whether anything here has become answerable.

A question that has sat in §37.1 for two review cycles is not an open question
— it is a decision nobody is making, and it should be escalated or the work it
blocks should be formally abandoned.
