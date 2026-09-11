# §10 — Module layout and Gradle wiring

> **Confidence:** `REVISABLE`
> The boundaries are durable. The exact module count may collapse or expand once
> the SDK choice (§11) is made.

---

## 10.1 The constraint that drives the layout

From §1.5, success criterion 1: **a NexLink user who never enables Social is
unaffected.** No size penalty, no new permissions, no new background work.

That is not a preference; it is the justification for D1 (§2.2) and it collapses
if `:app` ever links the social stack. Therefore the single hard rule:

> **`:app` must not depend, directly or transitively, on any module that pulls
> in the Matrix SDK or WebRTC.**

Everything else in this chapter follows from enforcing that rule mechanically
rather than by discipline.

---

## 10.2 Current state

As of versionCode 35 the repository declares four modules:

```
rootProject.name = "NexLink"
include ':app'        // the phone app — SMS, dialer, inbox, bridge
include ':shared'     // shared models and helpers
include ':wear'       // Wear OS companion
```

Plus two directories that are **not** in `settings.gradle` and must stay out of
scope: `android-app/` (dead legacy) and `media/`.

---

## 10.3 Target layout

```
NexLink/
├── settings.gradle
│
├── :shared              existing  · contacts, models, pure Kotlin
├── :app                 existing  · com.thvjq.nexlink
├── :wear                existing  · Wear OS, NexLink only
│
├── :social-contract     NEW  · IPC types shared by :app and :social. NO SDK.
├── :social-core         NEW  · Matrix session, store, crypto, sync
├── :social-rtc          NEW  · WebRTC, LiveKit, MediaProjection
├── :social-ui           NEW  · chat, calls, settings screens
└── :social              NEW  · the application · com.thvjq.nexlink.social
```

### 10.3.1 Why `:social-contract` exists separately

It is the only social module `:app` is permitted to see. It contains the
Parcelable types and AIDL definitions for the cross-app link (§16) and nothing
else — no SDK, no networking, no Android dependencies beyond `Parcelable`.

Without it, `:app` would need to depend on `:social-core` to know what a
conversation looks like, which reintroduces exactly the coupling the rule
forbids. Keeping the contract in its own tiny module is what makes the
dependency rule enforceable rather than aspirational.

### 10.3.2 Dependency matrix

| Module | May depend on |
|---|---|
| `:shared` | nothing internal |
| `:social-contract` | nothing internal |
| `:app` | `:shared`, `:social-contract` |
| `:wear` | `:shared` |
| `:social-core` | `:shared`, `:social-contract`, Matrix SDK |
| `:social-rtc` | `:social-core`, WebRTC/LiveKit |
| `:social-ui` | `:social-core`, `:social-rtc` |
| `:social` | all `:social-*`, `:shared` |

**Forbidden, and worth stating explicitly:** `:app → :social-core`,
`:app → :social-rtc`, `:app → :social-ui`, `:wear → anything social`.

---

## 10.4 Enforcing the rule in the build

Discipline is not enforcement. A Gradle check that fails the build:

```groovy
// build.gradle (root)
gradle.projectsEvaluated {
    def app = project(':app')
    def forbidden = ['social-core', 'social-rtc', 'social-ui', 'social']
    app.configurations.each { cfg ->
        if (!cfg.canBeResolved) return
        cfg.allDependencies.each { d ->
            if (d instanceof ProjectDependency) {
                def name = d.dependencyProject.name
                if (forbidden.contains(name)) {
                    throw new GradleException(
                        ":app must not depend on :${name} — see docs/social/10-modules.md §10.1")
                }
            }
        }
    }
}
```

The error message names the document. A rule whose reasoning is not discoverable
at the point of violation gets deleted by whoever hits it.

**Additionally**, an APK size assertion in CI: if `:app`'s release bundle grows
by more than a set threshold between releases, fail and require an explicit
acknowledgement. The dependency check catches the direct route; the size check
catches everything else.

---

## 10.5 Application IDs and versioning

| Module | Application ID | Version |
|---|---|---|
| `:app` | `com.thvjq.nexlink` | Independent (currently 35 / 2.5.5) |
| `:social` | `com.thvjq.nexlink.social` | Independent, starts at 1 / 0.1.0 |

**Versions are deliberately independent.** Locking them together would recreate
the fused release train that D1 exists to prevent (§1.4.3). The two apps
negotiate compatibility through the contract interface version (§16.6), not
through matching version numbers.

### 10.5.1 Signing

Both apps sign with the existing `nexlink-release.jks`, which is what makes the
`signature`-level permission in §16 possible. The existing arrangement already
supports this — `keystore.properties` is gitignored and read by
`app/build.gradle`; the same block moves to a shared location so `:social`
consumes it identically.

**Consequence to be aware of:** losing the keystore now breaks *both* products
irrecoverably, including the cross-app link. Keystore backup becomes a
higher-severity concern than it already was. It belongs in the backup procedure
(§23) even though it is not server data.

---

## 10.6 Build configuration

### 10.6.1 Shared configuration

Compile and target SDK, Kotlin and JVM targets should be defined once. The
repository currently sets them per-module; consolidating into a version catalogue
(`gradle/libs.versions.toml`) plus a convention plugin is worth doing **before**
five new modules multiply the duplication, not after.

### 10.6.2 ABI splits

`:social` will carry native libraries — WebRTC certainly, and the Matrix SDK too
if the Rust path is taken (§11). Native code is per-ABI, and shipping every ABI
in one artifact is what turns a 25 MB app into an 80 MB one.

App Bundle handles this automatically: Play generates per-device APKs and each
device downloads one ABI. **This is a strong reason `:social` must ship as an
AAB and never as a universal APK** — sideloading a universal build for testing
is fine, but it is not representative of what users download.

### 10.6.3 Minification

`:app` currently sets `minifyEnabled false`. For `:social`, R8 should be
**enabled** from the first release. Retrofitting minification to a large app
with reflection-based SDK code is painful; starting with it means keep-rules
grow incrementally alongside the code that needs them.

---

## 10.7 Where existing code is reused

An honest inventory, because "shared modules" is easy to say and often yields
nothing:

| NexLink code | Reusable in Social? | Notes |
|---|---|---|
| Contact resolution | Partly | Social has no phone numbers. The name/avatar formatting is reusable; the lookup is not. |
| `DebugLog` | Yes | Move to `:shared`. Same category model. |
| UI theme, colours, styles | Yes | Move to `:shared`, subject to §20's branding. |
| Foreground service pattern | **Yes, conceptually** | The `promoteToForeground` / `stopCleanly` discipline from `BridgePollingService` is directly applicable — see §15. |
| `BridgeCrypto`, `BridgeKeyManager` | No | Bridge-specific. Social uses the SDK's crypto. |
| `SmsHelper` | No | Different transport entirely. |
| `NotificationStore`, inbox adapters | Partly | The inbox aggregation model is reusable; NexLink keeps ownership (§16). |
| Wear integration | No | Out of scope (§1.6). |

The realistic reuse is theme, logging and *patterns* rather than logic. Stated
so the shared-module work is not over-planned.

---

## 10.8 Build time

Adding five modules to a monorepo has a cost, acknowledged in §2.2 as one of
D1's downsides. Mitigations, in order of value:

1. **Gradle configuration cache and build cache** on. Largest single win.
2. **Parallel execution** — the module graph is wide, which suits it.
3. `:social-*` modules are only configured for `:social` builds; a
   `./gradlew :app:assembleRelease` should not build them at all.
4. Avoid `api` dependencies between social modules; use `implementation` so
   changes do not cascade recompilation.

**Measure before optimising.** If `:app` build time is unchanged, the cost has
landed entirely on social development, which is the correct place for it.

---

## 10.9 Repository layout for documentation and infrastructure

```
docs/social/          this specification
infra/                NEW — server configuration, not application code
├── compose/          docker-compose for the spike (§33.1)
├── synapse/          homeserver.yaml templates
├── livekit/          SFU configuration
└── runbooks/         operational procedures (§29)
```

Infrastructure configuration belongs in version control alongside the code it
serves. **It must not contain secrets** — templates with placeholders, with real
values injected from the environment. The existing `.gitignore` already excludes
`keystore.properties` and `*.jks`; the same discipline extends to
`infra/**/*.secret.*`.

---

## 10.10 Open questions

- Should `:social-ui` exist separately, or fold into `:social`? Separate is
  cleaner and costs a module. **Leaning fold in** initially, split when it hurts.
- Should the social app live in this repository at all? §2.7 leaves it open.
  Same repo is the easier start; a split becomes attractive if build times
  degrade materially.
- Version catalogue migration: worth doing as its own change before the social
  modules land, so the diff is reviewable in isolation.
