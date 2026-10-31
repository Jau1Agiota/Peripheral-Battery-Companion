# A2.3F — opt-in debug offline diagnostics and TXT report

Base: A2.3E `1fe95b5300c5cc5b2c7298c7f2c7325232fc57a5`.
**MOCK / OFFLINE ONLY. Gate E BLOCKED; Gate B independent.**
No real PC authentication, connection, pairing, TLS, keys, Keystore, Windows writes,
new permission, telemetry, background service, or production activation.

## Entry, execution and honest results

The existing separate **MOCK OFFLINE Harness (debug)** launcher now offers **Run All
Tests** on its opt-in landing. Opening the activity does not run tests. The old
manual offline/session modes remain separate and unchanged. The button explains
that a finite local catalog runs; it gives no estimated duration.

Suite version `A2.3F-1` has **78 stable case IDs**. A complete healthy on-device run
would execute 74 checks and explicitly skip 4 unexecuted scopes; these are not
preassigned successes. Each real invocation computes actual results from its installed
assets/core/models. Statuses are PASS/FAIL/SKIP with expected/actual observations,
measured milliseconds and bounded diagnostic codes. Failures remain visible; no
automatic retry, raw exception message, stack trace or validator replacement.

Coverage:

- Seventeen individual canonical accept/reject scenarios use BatterySnapshotV1.parse.
- Twenty individual SHA256 comparisons use fixed canonical expected digests (not a
  mutable digest manifest supplied alongside modified bytes).
- Device order, zero/null, current/lastKnown, source/confidence, HyperX ambiguity,
  unavailable/stale, timestamp/source restrictions, UTF-8/depth/byte limits.
- Real OfflineSnapshotSimulation ordering/rejection/reset; real MockSessionPolicy
  begin/approve/bind/receive, progression/duplicate/modified duplicate/rollback,
  invalid/synthetic input, wrong peer, cancel/stop/revoke/deadline/clock and late callbacks.
- Real DebugHarnessModel opt-in, truthful mock labels, accessibility description
  content, zero/null/stale projection, reset, background-model cancellation and demo
  noninterference. These are model checks, not physical UI/accessibility validation.
- Android runtime: bounded debug asset access, activity declaration/debuggable-flag
  inspection, absence of INTERNET/broad storage permissions, nonunique OS/model
  availability, and actual worker-thread check. Declaration inspection does not
  programmatically navigate, rotate, background, or validate a release APK.

Four result rows always say NOT_RUN/SKIP because this runner does not execute:
separate instrumentation tests; manual visual/accessibility/rotation checks; real
Windows connection; release-APK isolation inspection. The screen/report explicitly
separates these scopes. External build/instrumentation evidence must be recorded
separately; the runner never infers it from compilation or historical results.

A missing manual-mode asset leaves an invalid empty input so the opt-in diagnostics
screen remains reachable; it never substitutes valid sample data. Suite checks still
read the original asset directly and report failures.

Checks run sequentially on Dispatchers.Default in a ViewModel-owned coroutine.
Catalog size is capped at 128; asset reads at 65,536 bytes; suite/check cooperative
budgets are 60 seconds / 5 seconds, not completion estimates. Pure checks have bounded
inputs and no retry/scheduler. Coroutine deadlines cannot forcibly interrupt a blocked
OS/provider call or arbitrary synchronous native code; no hard OS preemption is claimed.
No such native security operation is part of this catalog.

Progress shows completed count/current ID/measured elapsed time. A lifecycle-bound
UI ticker updates elapsed display only while STARTED and never advances mock time.
Cancel or background preserves completed results and marks unfinished cases SKIP with
USER_CANCELED; timeout uses SUITE_TIMEOUT/CHECK_TIMEOUT. Rotation retains the ViewModel
and run; process death loses it and never auto-resumes. Back cancels and returns to
manual entry. ViewModel clearing cancels its child job. Results remain stable until
an explicit new run. Immediate cancellation before worker startup also becomes CANCELED.

## Privacy-safe summary and explicit export

Completed/canceled runs enable **Copy Summary** and **Export Report (TXT)**. Running
or idle state cannot be exported as a completed result. Summary copies only the
aggregate/scoping notice, not payloads or device identity. Clipboard is explicitly
user-triggered and subject to normal Android clipboard behavior.

Export snapshots the terminal report before opening ACTION_CREATE_DOCUMENT with
text/plain and a fixed filename, `mock-offline-diagnostics.txt`. The user chooses the
destination through the system picker; no broad storage permission or hardcoded path.
The app does not upload/share automatically. A cloud-backed document provider is the
user's selection, not an app networking feature.

The selected URI is used only to open a write stream on Dispatchers.IO. Success is
shown only after UTF-8 write, flush AND close return successfully. Cancel, unavailable
snapshot after process recreation, null stream, write/flush/close or permission failure
show explicit non-success. A failed write may leave a partial file at the chosen
destination; no arbitrary provider file is silently deleted or rewritten by a retry.
Close success means the provider accepted the write, not independently proven remote
provider durability. Export is bounded to 128 KiB and owned by the activity lifecycle.

Report allowlist: fixed app name/debug variant, version, suite version, Android API,
nonunique Build.MODEL, execution environment, start/end UTC, total elapsed time,
case IDs/categories/expected/actual/status/duration and sanitized enum codes. Current
source commit is explicitly **unavailable (not embedded)** rather than fabricated.
Start/end and durations are diagnostics only, never snapshot freshness evidence.
Model/version/environment use strict formatting/length checks; unsafe strings redact.
No serial, Android ID, MAC/IP, account, credentials, token, keys, URI/path, raw payload,
exception text or full stack trace enters a report. Fixture identifiers are stable
catalog names. Reports contain synthetic/offline results only.

## Xiaomi 17T workflow — owner execution pending

1. Install `app/build/outputs/apk/debug/app-debug.apk` with ordinary authorized USB
   debugging on the selected device:

   ```sh
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n dev.peripheral.battery/.debugharness.DebugHarnessActivity
   ```

2. Confirm the separate MOCK / OFFLINE opt-in screen; no suite should have started.
   The normal app launcher must still show the unchanged default demo.
3. Tap **Run All Tests** once. Watch progress/current ID. Wait for COMPLETED, or cancel
   deliberately to test partial results. Inspect PASS/FAIL/SKIP totals and scroll each
   failed row; use IDs/codes, not a global green indicator, to report problems.
4. Confirm the four unrun scopes remain explicit. This does not prove any live Windows
   connection or physical accessibility/rotation test. Repeat without Wi-Fi/mobile data
   if desired; the suite is local and deterministic except measured diagnostic timing.
5. Tap **Copy Summary** and paste the safe aggregate into the developer conversation.
6. Tap **Export Report (TXT)**, choose the intended destination in the Android system
   picker, and save. Wait for **Report written successfully**. Canceling the picker
   must instead show **Export canceled**. On failure, do not assume a complete file.
7. Open the saved TXT in Files and inspect it; share that user-selected file with the
   developer through your chosen app. No automatic transmission is provided.
8. Manually check portrait, landscape, 200% font scale and TalkBack: scroll progress,
   results and export controls; ensure MOCK/UNRUN labels remain accessible. Actual
   checks should be described separately from the suite report.
9. **Back to manual harness** preserves the older controls as a separate path; system
   Back/Exit returns to the normal demo. Starting a new run is always explicit.

For optional separately executed instrumentation:

```sh
./gradlew connectedDebugAndroidTest
```

Three new instrumentation tests cover actual catalog execution, picker cancellation
(SKIP if no document provider resolves), and UTF-8 report writing/readback in Android
app cache. The latter is writer coverage, not a successful user-selected SAF export
or proof of cloud-provider storage. No programmatic rotation/backgrounding was added.

## Verification in this cloud task

```sh
source /workspace/.tools/android-env.sh
./gradlew --no-daemon --max-workers=4 testDebugUnitTest assembleDebug assembleRelease lintDebug assembleDebugAndroidTest
```

- Existing baseline: 138 JVM tests passed.
- Final JVM run: **161 passed, 0 failures/errors/skips** (138 existing + 23 new).
- The real 78-case catalog was exercised on JVM: core/model checks ran; the five
  Android-runtime checks used explicitly labeled test doubles. This is not an
  on-device run. Corrupted/missing asset tests demonstrate real failures, not fixed PASS.
- Debug/release builds, lint and instrumentation APK compilation passed. Release APK
  is unsigned. Lint: **0 errors / 9 existing warnings** (7 dependency updates, backup
  extraction guidance, missing icon).
- All 17 canonical fixture outcomes and 20 canonical hashes preserved/verified.
- Built APK DEX/assets/manifest inspection: diagnostics/harness exist only in debug;
  release has no debugharness/diagnostics classes, fixture assets or harness entry.
  Both manifests have no INTERNET or broad storage permission.
- Nine instrumentation tests compile (three added here). **connectedDebugAndroidTest
  NOT RUN; Xiaomi/emulator/physical UI/TalkBack/200% font/rotation and successful SAF
  picker export NOT RUN.** Owner-run device acceptance remains outstanding.
- Only debug implementation, debug unit tests, instrumentation tests and this document
  change. Main/release sources, manifest, Gradle/dependencies, canonical bytes, Windows
  repository and all security gates are unchanged. No PR is merged.
