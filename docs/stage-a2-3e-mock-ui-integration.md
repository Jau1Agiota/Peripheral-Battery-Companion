# A2.3E — debug-only MOCK / OFFLINE visual harness

Base: A2.3D `1f8602d6e4bf1aa3e3a0e785fc4f4713f9cdf504`.
This is an opt-in local debug tool, not a PC connection or real authentication.
Gate E remains BLOCKED; Gate B independent. No networking, credentials, TLS,
Keystore, Windows changes, background jobs, telemetry or added permissions.

## Isolation and behavior

The normal MainActivity, Compose layout, demo repository and all main sources are
unchanged. `src/debug/AndroidManifest.xml` adds a separate debug-only launcher named
**MOCK OFFLINE Harness (debug)**. It always opens an explicit choice screen; external
Intent extras cannot select a scenario or issue a fake handle. The ordinary launcher
continues to show the original demo, even in debug builds.

DebugHarnessActivity, DebugHarnessModel, the injected fake seam and harness UI exist
only in `src/debug/`. The debug Gradle source set packages existing `src/test/resources`
as assets, without modifying or copying canonical fixture bytes. No dependency is
added. Release builds have no harness activity/classes, fake-seam implementation,
launcher or test-fixture assets. The prior inert A2.3D policy types remain unchanged.

The debug ViewModel keeps local state across configuration recreation/rotation.
No SavedStateHandle, preferences, file writes, service, job or implicit progression
exists. Backgrounding cancels the mock session and hides current values; returning
requires an explicit begin/approve/bind. Offline fixture position remains local.
Process death/relaunch creates a fresh opt-in screen. ViewModel clearing stops the
engine and fences further actions. Reset creates fresh local engines and fences old
handles; it is not secure session/trust recovery and has no durable replay guarantee.
Exit/Back stops the harness, launches the unmodified normal demo and finishes the
harness activity. A release install has no debug entry at all.

The warning **MOCK / OFFLINE · No real PC connection or authentication** and Gate E
notice remain visible. “MOCK PC session · CONNECTED” is explicitly followed by
“No real PC connected”; it does not reuse the normal app's live-connection label.
Offline simulation and mock session/replay controls are separate modes. Switching
from the session to offline cancels the active fake session.

Device cards distinguish displayed current from **Retained evidence only**: wire
current and lastKnown never become each other. They expose wire availability,
freshness, deviceConnection and reason separately from PC session phase; source,
precision, ownership, socSupport and supplied receipt/observation times stay explicit.
Zero is shown as 0%; null or hidden current is unknown. HyperX is an estimate with
AMBIGUOUS attribution. Missing timestamps remain unknown, never generated from a
clock. Retained inspector values may remain visible after a policy error, but are
labeled as retained evidence; they are not current readings or live retention.
Merged accessibility descriptions repeat current/retained/confidence/time distinctions
and MOCK OFFLINE. Controls have at least 48dp touch height; all content scrolls.

Mock live-style sample actions create clearly noncanonical byte copies in memory
(synthetic=false, incrementing test revision) from local fixtures before strict
parsing. This is simulation-only input generation; it does not establish real producer
provenance, alter fixture files or construct an authenticated handle from payloads.
Fake evidence is issued solely through the existing explicitly injected debug seam.
Ticks advance only on explicit buttons; “Advance test deadline” adds synthetic ticks,
not real elapsed seconds. No timer/polling/reconnect runs in the background.

## Build and installation

From this checkout, with the prepared Android environment:

```sh
source /workspace/.tools/android-env.sh
./gradlew --no-daemon --max-workers=4 testDebugUnitTest assembleDebug assembleRelease lintDebug assembleDebugAndroidTest
```

Debug artifact: `app/build/outputs/apk/debug/app-debug.apk`.
Verified build SHA-256:
`096020836e0f1348a748eee80403e51a188867553b4ac6682e72548f5575397e`.
Rebuilding/signing may produce a different artifact; record the installed file's hash.
Release artifact is `app/build/outputs/apk/release/app-release-unsigned.apk` (unsigned,
not a ready-to-install release). Instrumentation APK is compiled separately.

For the owner-selected Xiaomi 17T or emulator, enable ordinary USB debugging and
verify the intended target in `adb devices`. Install the debug artifact:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If signatures differ from an existing installation, stop and resolve that choice;
do not silently uninstall user data. Launch the separate MOCK launcher or run:

```sh
adb shell am start -n dev.peripheral.battery/.debugharness.DebugHarnessActivity
```

This command opens the opt-in screen only. Normal demo entry remains:

```sh
adb shell am start -n dev.peripheral.battery/.MainActivity
```

## Xiaomi/emulator manual script — NOT EXECUTED here

Use the separate MOCK launcher. Scroll to controls/cards/status as needed. Record
Android/HyperOS version, APK hash, orientation, font scale, TalkBack status, results
and screenshots of test data only. No PC, Wi-Fi or mobile-data access is required.

1. Confirm explicit opt-in landing and persistent MOCK / OFFLINE warning. Open the
   normal launcher separately: unchanged 99%, ~90%, estimate 76% demo, not the Windows
   fixture's unknown G305. Return to the MOCK launcher explicitly.
2. Choose **Open OFFLINE fixture simulator**, then **Next local fixture** five times:
   demo (G915 99, G305 unknown, HyperX estimate 76); zero/null (0 vs unknown); stale
   lastKnown (current unknown, retained G915 99); all unavailable; invalid version
   rejected with prior unavailable evidence unchanged. A further step is COMPLETE.
3. **Reset local engines** returns the selected mode to idle. Choose **Open MOCK
   session policy**. Use **Begin mock attempt → Approve fake session → Bind test
   publisher → Receive revision 1**. Before the valid response, current stays hidden.
   Afterwards status is MOCK CONNECTED with “No real PC connected”.
4. **Receive revision 2 → Repeat revision 2** yields DUPLICATE without changing evidence.
   **Reject revision rollback** yields REPLAY_REGRESSION/ERROR: displayed current
   hidden, prior data labeled retained evidence only. No real trust is implied.
5. Reset and repeat the begin/approve/bind/receive-1 sequence. Try **Receive zero /
   unknown**, **Receive all unavailable**, **Receive unsupported**, then **Receive
   stale last-known**. PC phase remains mock-connected while per-device availability
   differs; zero/null and current/lastKnown remain distinct. Confirm HyperX confidence.
6. Reset/connect before each independent denial: **Reject invalid fixture** gives
   INVALID_PAYLOAD; **Reject synthetic live input** gives SYNTHETIC_PAYLOAD. Neither
   replaces accepted evidence or silently switches to ordinary demo.
7. Reset/connect; **Cancel mock session → Deliver old callback** must remain disconnected
   with IGNORED_CALLBACK. Repeat with **Stop mock engine**; late callback cannot restart.
   Reset creates a new local engine; delivering the saved old callback still cannot
   authenticate it. A new explicit begin/approve/bind is required.
8. Reset/connect; **Advance test deadline** hides current with no reconnect. Reset/connect
   again; **Revoke fake trust** hides current and reports TRUST_REVOKED.
9. Rotate with the harness open: selected mode/step stays, without replaying actions.
   Background and return: session is paused/disconnected and needs explicit begin.
   Restart the activity/process: fresh opt-in; no saved authorization or mode activation.
10. Repeat at large font scale (200%), narrow portrait and landscape. Scroll every
    control/card; use TalkBack to confirm current versus retained, unknown versus 0,
    source ambiguity, timestamps and MOCK OFFLINE labels. Check top/bottom safe areas.
11. **Exit to normal demo** and system Back restore the ordinary demo; no mock controls
    or marker remain there. Reopening the MOCK launcher starts opt-in again.
12. Verify with Wi-Fi/mobile data disabled; behavior remains deterministic. No server,
    pairing, credentials or security operation should be requested.

Optional authorized emulator/device instrumentation command:

```sh
./gradlew connectedDebugAndroidTest
```

This is not a claim that instrumentation or physical execution occurred.

## Verification evidence and limitations

- Existing 123-test baseline passed before changes.
- Final JVM run: **138 passed, 0 failed/errors/skipped** (123 existing + 15 debug-model tests).
- `assembleDebug`, `assembleRelease` and `lintDebug` passed; lint **0 errors / 9 existing
  warnings** (7 dependency updates, backup extraction guidance, missing icon).
- `assembleDebugAndroidTest` passed. Three new harness UI tests plus three existing
  UI tests compiled. All six instrumentation/UI tests **NOT RUN**; no Android runtime
  or physical Xiaomi execution, screenshots or visual acceptance is claimed.
- All 17 canonical outcomes pass in the inherited parameterized suite and all 20
  SHA256SUMS entries match unchanged canonical bytes.
- APK checks: debug DEX contains the harness and 26 fixture asset files; release DEX
  contains no debugharness package, no fixture assets and no harness manifest entry.
  Both compiled manifests have no INTERNET permission. Release is unsigned.
- Debug unit tests exercise opt-in/routing guards, ViewModelStore recreation/clearing,
  reset/restart, pause/switch, coherent outcomes, replay/invalid/synthetic rejection,
  stale callbacks, zero/null/source/accessibility descriptions and demo exit routing.
  Activity recreation/rotation and actual exit-to-demo are instrumentation cases
  compiled but unexecuted. No fake test is presented as actual device behavior.
- Initial build retried a Kotlin daemon startup automatically and completed; SDK XML
  compatibility and graphics-symbol stripping warnings do not indicate native testing.

Only debug source/manifest, debug-only unit/UI tests, debug asset configuration and
this document are added/changed. Main source/manifest, dependencies, parser, simulator,
policy, canonical fixtures and Windows repository remain unchanged. No secrets or
private personal/network details are intentionally included. Keep this PR and all
prior stages unmerged; physical review and all real security/transport decisions
remain separately owner-controlled.
