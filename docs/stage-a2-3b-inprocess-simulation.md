# A2.3B — in-process offline snapshot simulation

Opt-in `OfflineSnapshotSimulation` runs explicitly supplied in-memory byte inputs
through the existing strict `BatterySnapshotV1.parse` and `projectOffline` path.
No new dependency, screen, default-provider change, timer, scheduler, clock, file
loader, network permission, transport, security operation, or Windows change.

## Invocation and scenarios

A caller supplies a fixed list; constructing a controller does not parse or advance:

```kotlin
val simulation = OfflineSnapshotSimulation(listOf(
    SimulationStep(SimulationScenario.SYNTHETIC_DEMO, demoBytes),
    SimulationStep(SimulationScenario.ZERO_VERSUS_NULL, zeroBytes),
    SimulationStep(SimulationScenario.STALE_LAST_KNOWN, staleBytes),
    SimulationStep(SimulationScenario.ALL_UNAVAILABLE, unavailableBytes),
    SimulationStep(SimulationScenario.INVALID_PAYLOAD, rejectedBytes),
    SimulationStep(SimulationScenario.REVISION_ONE, revisionOneBytes),
    SimulationStep(SimulationScenario.REVISION_TWO, revisionTwoBytes),
))
val outcome = simulation.advance() // Exactly one explicit step
val repository: BatteryRepository = simulation // Optional existing ViewModel adapter
// Observe simulation.state for the coherent projection + step outcome.
simulation.reset() // Explicitly clear accepted state and replay from the beginning
```

Supply bytes from an explicit local test caller; there is no runtime resource lookup.
The JVM test helper demonstrates all these scenarios plus NOT_SUPPORTED. Canonical
fixtures are read unchanged from `contracts/`; separate `simulation/` resources are
clearly labeled noncanonical. The revision examples intentionally set synthetic=false
with fixed test UUIDs and null timestamps; they are not live/production telemetry.
A test-supplied fixed timestamp checks preservation only, never clock-derived evidence.
Scenario names identify attempted inputs; parser results determine acceptance.

## State and safety semantics

`state` is the only MutableStateFlow. Each immutable SimulationState contains step
index/name, latest accepted validated projection, outcome, and safe error category.
Inputs and sequence membership are copied; evidence and domain device collections
are unmodifiable. The complete wire evidence is retained in the projection.

- IDLE: no accepted input. The repository view supplies empty DISCONNECTED readings.
- ACCEPTED: parsing/validation succeeded and a complete new projection is published.
- REJECTED: INVALID_PAYLOAD only; prior accepted projection retained by identity.
  Exception messages and input contents are not logged or exposed as diagnostics.
  A rejected step is consumed; the next explicit advance can recover.
- COMPLETE: no more steps; last projection/index/name retained, error cleared.
  Repeated advance is a no-op. Empty sequences complete with no accepted evidence.
- reset: explicitly returns to IDLE and clears the accepted projection.

`snapshots` is a read-only derived StateFlow compatibility view for BatteryRepository.
It reads the authoritative state directly and suppresses equal snapshot emissions;
there is no second mutable flow, worker job, or independent publication. Consumers
needing outcome and matching wire/domain data must observe `state`. StateFlow may
conflate rapid updates for slow collectors; it is latest state, not an audit log.

The parser enforces nonnegative wire revisions, but this controller imposes no
monotonic-revision or server-continuity rule. Duplicate/decreasing revisions and a
new instance UUID are accepted as independent local imports. None establishes,
resumes, or authenticates a session. This keeps revision policy distinct from wire
validation; a future transport policy would require separate implementation/review.

Synthetic imports retain explicit demo/synthetic presentation. Nonsynthetic imports
remain DISCONNECTED and the existing ViewModel mapping hides their current values.
DeviceConnection never implies PC connectivity; unsupported/unavailable do not prove
a device disconnect. Zero/null, current/lastKnown, source precision/ownership,
availability/freshness/reason, revision/instance, and supplied times remain distinct.
The Windows demo's G305 is UNKNOWN; the separate Android default demo remains ~90%.

## Verification

Base: A2.3A `58d46fb1ad6563dcfabbbd73eb4e6afabcc92773`; existing 78-test baseline passed.

```sh
source /workspace/.tools/android-env.sh
./gradlew --no-daemon --max-workers=4 testDebugUnitTest assembleDebug lintDebug
cd app/src/test/resources/contracts
sha256sum -c SHA256SUMS
```

Final validation passed: 97 tests (78 existing + 19 new), zero failures/errors/skips;
debug APK build passed; lint passed with 0 errors and 9 existing warnings
(7 dependency updates, backup extraction guidance, missing icon). All 17 canonical
fixture outcomes and all 20 canonical SHA256 hashes passed. Tests cover explicit ordering,
deterministic replay, rollback/recovery, revisions/instance changes, immutable and
coherent publication, controlled coroutine ViewModel propagation/cancellation,
synthetic labeling, evidence distinctions, no false connection, and unchanged demo.
No physical-device/emulator execution, rendered UI validation, or security/transport
validation is performed. The controller is intentionally not wired into the app.
No debug entrypoint is needed for this JVM-only harness.

This work is stacked on A2.3A for review; main and PR #10 remain unmerged.
