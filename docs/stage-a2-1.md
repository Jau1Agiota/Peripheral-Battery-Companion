# Stage A2.1 — connection foundation, no networking

Base: visual-fidelity commit 100c39c7d699124804ad9fe2f4e0794ff1bc56cd.
Branch: feature/stage-a2-1-android-data-foundation. Local-only; no push/PR/merge/release.

## Architecture

- DeviceIdentity binds stable IDs, display names and keyboard/mouse/headset types.
- DeviceReading retains validated nullable percentage, confidence (including UNKNOWN),
  optional actual observation time/source/caution and per-device availability.
- BatterySnapshot adds optional capture time and explicit ConnectionMetadata.
  Duplicate device identities and fabricated demo timestamps are rejected.
- BatteryRepository exposes read-only StateFlow snapshots plus a local demo reload
  hook. No network operation or Windows wire format is defined.
- DemoBatteryRepository publishes exactly the original synthetic samples with null
  timestamps. DisconnectedBatteryRepository publishes three stable unknown readings,
  no sources/times, and DISCONNECTED state. Neither ever emits CONNECTED.
- BatteryViewModel collects provider updates in viewModelScope, which cancels on
  lifecycle clearing. Tests inject a controlled scope without new dependencies.
  Refresh works only for demo and preserves its existing confirmation behavior.
- forUi normalizes provider order, supplies missing supported identities with null
  readings/unknown confidence, hides cached percentages while disconnected/connecting,
  and labels cached values stale after stale/error events. It never generates
  observations, times or substitute samples. Known source/confidence/time survive.
- Connection presentation drives existing text slots. Default demo strings, geometry,
  colors, icons, images, navigation and enabled demo controls remain unchanged.
  Refresh is disabled outside demo; a previous demo confirmation is hidden there.
  Unknown confidence never silently becomes precise or draws a misleading progress bar.

## Connection-state meanings

| State | Meaning |
| --- | --- |
| DEMO | Synthetic samples only; not connected; no observation/capture times |
| DISCONNECTED | No connection; charge is unavailable |
| CONNECTING | Future provider is attempting connection; no claim of success |
| CONNECTED | Future provider explicitly reports a genuine connection; never inferred from readings |
| STALE | Connection/freshness no longer current; available cached readings are marked stale |
| ERROR | Provider reports failure; cached readings are marked stale, unavailable ones stay unavailable |

`pcOnline` is a compatibility accessor true only for explicit CONNECTED; synthetic
is derived only from DEMO, so demo can never claim a live connection. Optional
connection.message preserves provider diagnostics without blindly displaying it.
There is no freshness timer, authentication, transport selection or protocol in A2.1.
No new permissions/dependencies, background jobs or persistence were added.
A future provider must supply genuine observations and connection transitions;
the app's default provider remains demo, and no UI connection switch was added.

## Verification

Executed with JDK17 / Gradle8.9 / SDK35:
`./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --max-workers=2 --console=plain`
Result: BUILD SUCCESSFUL, final run 19 executed tasks and 57 up-to-date.
30 JVM tests passed: 10 preserved tests + 20 foundation tests; 0 failures/errors/skips.
Coverage includes ranges, null vs zero, every confidence/connection state, stable
identity/order, source/timestamp preservation, missing snapshots, provider updates,
demo-only refresh and collection cancellation. Three instrumentation tests compiled
(including disconnected/empty rendering); none executed.
Lint: 0 errors, 9 existing warnings: 7 newer-dependency notices,
DataExtractionRules and MissingApplicationIcon. No assertions disabled or lint baseline.
No source/reference/resource image changes; manifest and dependency files unchanged;
no INTERNET permission. APK signature verification and git diff --check passed.

APK copy: /workspace/stage-a2-1-artifacts/stage-a2-1-debug.apk
Build output: app/build/outputs/apk/debug/app-debug.apk
ID dev.peripheral.battery, version0.1.0-demo (code1), minSDK26/targetSDK35.
SHA-256: 8952f6a1e54cb99970d6ba9cdc7ac3957b1240f0f2fa1dd98773abc7f7896d46
Reports/XML and build log: /workspace/stage-a2-1-artifacts/.

Screenshots and visual/device execution were NOT performed in this stage. This
runtime has no hardware acceleration; the prior software-emulator attempt failed
before installing the baseline, as recorded in docs/visual-fidelity.md. No new
pixel-level or physical Xiaomi acceptance is claimed. Demo-content mapping is
regression-tested and the UI diff changes only state bindings/control enablement.

## Xiaomi17t checklist

1. Install the debug APK and launch; verify the same compact layout/assets as the
   approved visual-fidelity version and capture a screenshot for comparison.
2. Verify all three cards: G91599% precise, G305~90% approximate, HyperX76% estimate
   with source-under-validation wording; demo/synthetic and not-connected labels.
3. Refresh: values stay unchanged; reload confirmation appears, with no timestamp
   or Connected claim. Settings/header/button navigation and disabled History work.
4. Check landscape, small width, large fonts, TalkBack, scrolling and system insets.
5. Repeat offline; no connectivity prompt or background activity should occur.
6. Record Android/HyperOS version, APK checksum and screenshots. Non-demo states
   are covered by provider/state tests; there is deliberately no real-connection UI.

No Windows application code was touched. Stop for review before Stage A2.2.
