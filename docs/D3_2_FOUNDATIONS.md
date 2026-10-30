# D3.2 Android inert foundations

Canonical full D3.1 design and D3.2 architecture/adapter review:
https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/blob/feature/d3-2-inert-foundations/docs/D3_1_Secure_Connection_Design_Review.txt
https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/blob/feature/d3-2-inert-foundations/docs/D3_2_FOUNDATIONS.md

The independent Kotlin foundations are unwired synthetic metadata/state policies:
consent default-off/restart, injected admission limits, exact literal IP SAN/anchor/
SPKI/path/validity/EKU decisions, serialized single-use fake pairing, scoped expiring/
revocable credential decisions, and bounded revision/session tracking. No secret,
credential, actual storage, crypto, TLS, socket, listener, QR, Keystore or UI adapter.
Injected evidence booleans never constitute native proof or remote consent.

No approved policy defaults. Test values are synthetic only. Invalid/uncertain
pairing closes or becomes terminal UNCERTAIN; lost response cannot reissue.
Clock/revocation failures deny in this instance; durable/restart guarantees remain
unimplemented. Admission owns no timer/queue: adapters must enforce real limits.
New/advanced publications remain unknown freshness; duplicate/receding/demo/cached/
unauthenticated publications never promote freshness. Disconnect retains history.
Retired-session capacity is required; exhaustion denies new sessions.

SnapshotWirePolicy keeps structured source/confidence, readAt, observedAt,
lastKnown, freshness, unavailable reasons and nulls. StrictJsonGuard bounds syntax/
depth and rejects JSON extensions/duplicate names before platform JSONObject.
These are conservative unwired wire readers, not a full JSON Schema engine.
Existing Windows v1 schema/example are byte-identical; digest manifest and expected
golden/rejected cases are tested. Tests pin schema digest independently of manifest.
org.json dependency is JVM test-only to supply the Android platform JSON API under
unit tests; no new runtime dependency, INTERNET permission or app wiring.

Adapter implementation deliberately deferred: DeviceReading cannot losslessly
represent structured provenance, separate receipt/observation, lastKnown, all wire
availability/reason/connection/freshness distinctions. A reviewed domain/DTO/UI
extension is required; no lossy source-string mapping or schema revision chosen.
Existing dashboard/demo repository/manifest/navigation/monitoring unchanged.

Native proof remains NO-GO pending explicit owner approval of ephemeral Windows
key/certificate lifecycle, DPAPI/ACL/envelope durability, narrow-anchor TLS+SAN+pin
platform tests, Keystore, durable revocation, clocks, bootstrap QR, port/interface,
lifetimes and budgets. No candidate numeric values approved by this stage.
No Option2/retired/preview operation execution or artifact access.

Verification:
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
See D3_2_Implementation_Report.txt for exact commit/file inventory and actual results.
No emulator, native or physical LAN tests are claimed.
