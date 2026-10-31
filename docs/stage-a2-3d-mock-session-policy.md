# A2.3D — inert mock session and candidate replay policy

**MOCK ONLY. No real authentication or transport.** Gate E remains BLOCKED; Gate B
is independent. This stage models the owner's provisional pinned-server-TLS plus
per-phone snapshot.read bearer hypothesis with Boolean fake authorization decisions,
not tokens, pins, certificates, credentials, native APIs, or a TLS implementation.
No production provider/screen is wired to these classes.

Base: A2.3C `d55f61089b19dede55a3ffaf79711fa306f65026`. The 97-test inherited
baseline passed before implementation. A2.3C remains a proposed architecture; this
narrow mock prototype does not settle its OPEN deployment decisions.

## Components and explicit invocation

New package: `dev.peripheral.battery.mocksession`.

- `MockSessionTypes.kt`: immutable identity-generation labels, explicit synthetic
  ticks, phases/outcomes/denials, opaque mock handle, required injected test seam,
  and coherent session/evidence/display state. Labels are test metadata, not pins.
- `CandidateReplayPolicy.kt`: isolated bounded in-memory high-water/retired-instance
  policy. No persistence, clock, transport, or changes to A2.3B simulation.
- `MockSessionPolicy.kt`: synchronized explicit actions and one authoritative
  read-only StateFlow; no job, timer, system clock, file I/O, or background worker.

A test must explicitly provide `MockAuthenticationTestSeam`; no default, real
implementation, or automatic approval exists. `MockSessionHandle` has a private
constructor; its internal factory requires that seam. The handle carries no secret
and is not any Windows authorization permit. Parsing payloads cannot issue handles.
The seam is a trusted test control, not a security boundary against malicious code
inside this module/process; replacing it is not real authentication.

```kotlin
val policy = MockSessionPolicy(explicitFakeSeam, deadlineTicks = 10)
val generation = policy.begin(testIdentity, MockTick(0))!!
val handle = policy.authenticate(generation, testIdentity, MockTick(1))!!
policy.bindInstance(handle, testPublisherUuid, MockTick(1))
policy.receive(handle, explicitlySuppliedPayloadBytes, MockTick(2))
// Observe policy.state for matching phase, outcome, retained evidence, and display.
policy.checkDeadline(MockTick(12)) // Caller-driven; nothing schedules this action.
policy.stop() // Terminal for this engine instance.
```

All labels/bytes/ticks come from an explicit local caller; no resource loader is in
main code. Tests reuse unchanged canonical and A2.3B simulation resources in memory;
controlled mock-live inputs set synthetic=false only in test-created byte copies.
They remain test data, not real telemetry or proof of producer provenance.

## Session and projection rules

| Phase | Display mapping and transition |
| --- | --- |
| DISCONNECTED | Empty readings; no active handle. Initial, canceled, or timed out under default retention policy |
| CONNECTING | Empty readings; explicit begin creates a new monotonically increasing local generation |
| AWAITING_RESPONSE | CONNECTING display, empty readings; fake authentication and explicit publisher binding are not first-response success |
| CONNECTED | Candidate-only CONNECTED display after active fake authorization, explicit binding, strict parsing and an advanced valid nonsynthetic publication |
| STALE | Optional test-only retention after loss/deadline; explicitly STALE readings, pcOnline=false |
| ERROR | Trust/policy/input rejection; handle invalidated and all displayed values hidden |
| STOPPED | Terminal; DISCONNECTED display, no begin/authentication/late callback can restart it |

Every state has mockOnly=true. Display metadata explicitly says
`MOCK ONLY · Not real authentication`. Candidate CONNECTED is a test assertion only;
the existing app never consumes this engine. Accepted `OfflineSnapshotProjection`
retains its original DISCONNECTED metadata plus all validated wire evidence; only
this separate candidate display is derived from fake session state. No parser field
creates a connection. Existing Compose UI/default demo/manifest are untouched.

`lastAcceptedIdentity` records its mock peer independently from `expectedIdentity`
for a new attempt, preventing an old peer's evidence being mislabeled as the new peer.
`lastAccepted` survives errors/loss as separate immutable evidence, not automatic
current display. Default retention is HIDE. Optional ALLOW_STALE_AFTER_LOSS is an
explicit test assumption for cancellation/deadline only, same expected identity;
trust revocation, identity mismatch and policy failure always hide values. No disk
retention or production stale threshold is approved. No fallback to synthetic demo.

Zero/null, current/lastKnown, wire availability/reason/freshness/deviceConnection,
precision/ownership, timestamps, instance and revision are preserved. Unsupported
or unavailable peripherals do not disconnect an accepted mock PC session. HyperX
remains estimate/ambiguous. Values are never promoted from retained lastKnown.
Collections and payload comparison bytes are defensively copied; there is no mutable
StateFlow for display independent of session/evidence. StateFlow is latest state,
not a non-conflating event log. Callers should observe that single state.

## Candidate replay and time assumptions — not deployment decisions

- Identity scope is a synthetic peer label AND identity generation; publisher UUID
  is a separate explicitly confirmed binding. A wire UUID cannot bind itself.
- Every local begin invalidates the old handle and advances local generation.
  Handle identity is also checked by object identity, preventing cross-engine reuse.
  Stale callbacks return IGNORED_CALLBACK without touching current state or clock.
- The injected seam must approve both snapshot.read authorization and an uncached
  publisher binding. Authorization is rechecked before handling each response/bind;
  exceptions deny safely without propagating their diagnostic text. The fake confirms
  metadata only; it does not demonstrate that a real response is authenticated/uncached.
- First valid publication or larger revision advances evidence. Lower revision fails.
  Equal revision with byte-identical payload is DUPLICATE; changed bytes fail with
  MODIFIED_DUPLICATE, even whitespace-only changes. Byte equality is a conservative
  prototype assumption, not a frozen protocol canonicalization decision.
- Duplicates never replace evidence or extend liveness deadlines. After reconnect or
  explicit rebind, an equal revision leaves AWAITING_RESPONSE until a newer publication.
  That conservative availability tradeoff needs review for future production behavior.
- Explicitly approved new publisher UUID retires the prior one; reverting to a retired
  UUID fails. History persists across local cancel/reconnect but only in this object.
  Default capacity is 8 identity/publisher scopes, explicitly configurable from 1..64.
  At capacity, deny rather than evict anti-replay history. No persistence or rollback
  resistance across engine recreation, process death, device restore, or OS restart.
- Injected tick units and positive deadlineTicks are test parameters, not seconds or
  approved lifetimes. Negative, uncertain, backward, or overflowing time denies;
  exact deadline is expired. Accepted new publications extend the injected deadline;
  equal revisions and binding actions do not. No wall clock or timestamp freshness
  inference exists. All time-sensitive checks occur on explicit caller actions.
- Errors invalidate the current handle. Recovery requires explicit begin/authenticate/
  bind, preserving history. stop is terminal; new engine construction loses history
  and must never be represented as trusted production recovery. Clock uncertainty
  cannot be repaired by continuing to use an old handle.

## Verification and scope

```sh
source /workspace/.tools/android-env.sh
./gradlew --no-daemon --max-workers=4 testDebugUnitTest assembleDebug lintDebug
cd app/src/test/resources/contracts
sha256sum -c SHA256SUMS
```

Validation passed: **123 tests (97 existing + 26 new), zero failures/errors/skips**;
debug APK build passed; lint passed with **0 errors and 9 existing warnings**
(7 dependency updates, backup extraction guidance, missing icon). Tests cover
phase transitions, fake-denied/wrong-identity handles, explicit binding, equal/changed/
lower revisions, reconnect/rebind, identity generations, retired history/capacity,
clock uncertainty/deadlines, default-hidden/optional-stale retention, revocation,
cancellation/terminal stop/foreign and late handles, invalid/synthetic/oversized input,
null/zero/unavailable/unsupported, HyperX evidence, coherent immutable publication,
input copying and reentrant seam stop. All previous tests and canonical hash checks
are preserved; no new dependency or permission is added.

Real TLS, pairing, keys/certificates, Android Keystore, Windows CNG/X509, native or
physical-device/emulator testing: **NOT RUN**. No Windows files, gates, permit or
security ledger were modified. Mock success is not OS/network/security evidence.

## Open architecture choices and handoff

Only the bearer-based authorization hypothesis, fake session generation, in-memory
replay semantics, and explicit test retention/time budgets are modeled here.
Server key custody, IP SAN versus DNS, exact route/version/bootstrap encoding,
credential lifetime/storage/rotation/revocation persistence, durable replay and
trusted time, production retention/Android lifecycle, and any network exposure
remain OPEN. No real credential is issued or stored.

Future real-platform work needs independent Windows AND Android security review,
verified runtime/dependency provenance, explicit owner scope and test permissions,
real TLS/identity negative tests, OS storage/key lifetime and rollback/revocation
proof, and separately approved interface/exposure and rollback plans. Gate B and
Gate E approvals remain independent. This stage clears neither gate and does not
authorize a listener, production authentication, or merge. The draft PR stacks on
A2.3C; main and all prior PRs remain unmerged.
