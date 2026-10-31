# Stage A2.3A — offline BatterySnapshotV1

The Android implementation imports bytes explicitly, without transport, a clock,
a service, authentication, Windows native operations, or a network permission.
The application still uses DemoBatteryRepository; Compose UI is unchanged.

## Provenance

Canonical repository: https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-

Windows main inspected at `ac78aa3eccc7c16c870d0d09f565f631f4a04dd5`.
The original Kotlin implementation follows
`src/PeripheralBatteryCompanion.Core/Snapshots/BatterySnapshotV1.cs` and
`docs/contracts/battery-snapshot-v1.schema.json` (schemaVersion 1).
No Windows implementation code is vendored or modified.

Exact canonical schema, synthetic example, scenario manifest, SHA256SUMS, four
golden fixtures, and thirteen rejected fixtures are under
`app/src/test/resources/contracts/`. Bytes and line endings were copied unchanged.
All 20 manifest hashes passed `sha256sum -c SHA256SUMS`. The canonical SHA256SUMS
records each individual hash, including:

- Schema: `2d9ac5bf5e5263340bcd15736df7a9f6f6c201f5f4ad30b1fb7e858ec694bdc2`
- Scenarios: `ffcf9fbb3c656180cc48bfc300fea1d14ef75070ec797b7439d0057109c8363c`

Android base: A2.1 `29871c38ae5c69f8182d7d5f125910a9e3ed291c`.
The initial cloud checkout was clean on `work` at A1 main
`ee0bd8bf1aae5423392f1f5bc35e944385f93348`; the dedicated branch was created
from the fetched, verified A2.1 foundation without resetting or discarding work.
The A2.1 baseline's 30 existing unit tests passed before implementation.

## Reader and validation

`BatterySnapshotV1.parse(ByteArray)` is the sole constructor of validated snapshots.
Its immutable DTOs retain source, precision, availability, freshness, device
connection, reason, receipt/observation times, current, and lastKnown separately.
The devices collection is a defensive unmodifiable copy.

The small original JSON reader has no new dependency and is independent of Android
framework JSON APIs, so production and JVM tests use the same strict behavior.
It rejects duplicate decoded keys at every object level, unknown/missing fields,
invalid UTF-8 and string escapes, malformed syntax, trailing content, coercion,
non-integral number spellings, overflow, invalid UUIDs, payloads above 65,536 bytes,
and nesting above 16 containers. Null is accepted only where the schema permits it.

Validation uses exact schema enum spellings and the stricter C# canonical order
(g915, g305, hyperx), nonempty GUID, availability/value consistency, synthetic-time
restrictions, HyperX estimate/ambiguous-source constraints, and gateway observation
restrictions. Dates use the C# UTC serialization form: uppercase T/Z, seconds,
optional 1–7 fractional digits, valid calendar/time, years 0001–9999. Local and
numeric-offset timestamps are rejected rather than converted into inferred UTC.
The JSON schema alone does not enforce all C# semantic constraints; both apply.

## Offline projection and adapter

`projectOffline()` returns `OfflineSnapshotProjection`: full validated wire
`evidence` plus the narrower Android `snapshot`. A retained lastKnown value never
becomes current. Wire NOT_SUPPORTED/UNAVAILABLE do not establish a disconnected
device; current stays null and the full availability/reason remains in evidence.
STALE maps to Android STALE without copying its cached percentage. Wire device
connection never determines PC connection. Precision is copied without upgrade;
source kind and ambiguous-ownership caution remain explicit.

Synthetic imports use DEMO. Nonsynthetic imports use DISCONNECTED, never CONNECTED;
existing `forUi()` therefore hides their readings as offline. Actual supplied times
remain in evidence and domain metadata; no time is generated. This deliberate
conservative projection does not extend the current UI/domain enums.

`OfflineSnapshotRepository` is an opt-in in-memory BatteryRepository with explicit
`importSnapshot(bytes)`. Its read-only StateFlows expose projections and Android
snapshots. Parsing completes before any update; failed imports preserve previous
state. These are two flows, not a transactional pair: consumers needing matching
wire/domain evidence should observe `projections`. No runtime wiring or default
provider changes were made.

## Validation

```sh
source /workspace/.tools/android-env.sh
./gradlew --no-daemon --max-workers=4 testDebugUnitTest assembleDebug lintDebug
```

The final implementation run passed 78 tests (30 existing, 48 new), with zero
failures/errors/skips. Parameterized cases read the canonical scenarios manifest
and verify all 17 fixture outcomes and hashes. Additional regressions cover
number types and bounds, UUID, canonical order, duplicate/unknown/missing fields,
malformed UTF-8/JSON, payload boundary, depth, UTC/synthetic constraints, retained
readings, HyperX confidence, zero/null, unavailable/unsupported, no false connected
state, immutable evidence, failed imports, observable ViewModel updates, and the
unchanged demo. A complete-manifest test also verifies schema, synthetic example,
and scenario hashes.

Debug APK build and lint passed. Lint: 0 errors, 9 warnings (7 dependency updates,
backup extraction guidance, missing application icon). No device/emulator,
rendered-screenshot, LAN, or Windows security validation was performed or claimed.
