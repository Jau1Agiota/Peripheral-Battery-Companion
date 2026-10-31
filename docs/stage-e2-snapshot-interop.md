# E2 — Windows-to-Kotlin offline snapshot interoperability (review work)

Both local branches: `feature/stage-e2-snapshot-interop`.
Android base: `83b83c717690cee9fb07e0c1079942785a7d707f`.
Windows base: `ac78aa3eccc7c16c870d0d09f565f631f4a04dd5`.
Changes are intentionally UNCOMMITTED and UNPUSHED pending owner review.

## Provenance and truthfulness

All input battery values, timestamps and instance IDs are CONTROLLED TEST DATA.
Valid vectors use the nonsynthetic wire path (`synthetic=false`), but are NOT actual
live telemetry, hardware validation, authentication, or a working PC connection.
A fixed TimeProvider supplies UTC publication/receipt times; no clock/hardware is
consulted. No provider, scheduler, listener, pairing, Windows native/security test,
key/certificate operation or Gate B/E authorization guard is called by the harness.

The new Windows-only test project
`tests/PeripheralBatteryCompanion.InteropVectors/` directly ProjectReferences the
actual portable Core project. Production files are unchanged. Its executed path is:

`BatterySnapshotProjection.Build -> PublishedSnapshotStore.Publish -> BatterySnapshotJson.Serialize`

It writes the immutable publication's UTF-8 Json bytes without decoding, editing,
pretty-printing, line-ending conversion, BOM or added newline. The semantic manifest
is derived from the actual typed Windows publication; independent C# assertions and
Kotlin tests additionally check known zero/null/source/cache/sequence expectations.

`app/src/testInterop/resources/interop/e2/` contains those exact outputs, their SHA256
checksums, and clearly labeled controlled mutations. `.gitattributes` marks this new
resource root byte-preserved. The existing canonical resources/attributes/hashes/
scenarios are unchanged. This separate root is registered only for JVM tests; E2
vectors/classes are absent from debug AND release application APKs.

## Coverage

| Valid Windows-emitted cases | Evidence exercised |
| --- | --- |
| precise-both-hyperx-estimate | G915/G305 precise gateway percentages; HyperX ESTIMATE/AMBIGUOUS |
| approximate-both | Approximate percentages with SOC NOT_SUPPORTED but availability AVAILABLE |
| unsupported-both | Null current values and NOT_SUPPORTED/UNSUPPORTED, independently from PC state |
| zero-all | Zero on G915/G305/HyperX remains known zero |
| null-unavailable-all | Null/unavailable readings, unavailable HyperX |
| stale-lastknown-all | Current null, separate retained readings, precision and stale reason |
| absent-with-lastknown | Reported absent peripherals with retained cache, no promotion to current |
| legacy-g915-observation | Explicit stored-source receipt versus observation timestamps |
| unknown-confidence-both | Numeric value retained without upgrading unknown precision |
| null-receipt-times | Missing source times remain null |
| sequence-first / sequence-repeat | Exact repeated bytes, revision and capturedAt unchanged |
| sequence-second | Actual store increments revision; injected publication time advances one 100ns tick |
| sequence-restarted | New publisher UUID, revision restarts at 1 within the new scope |

Every valid vector is parsed with the actual Kotlin BatterySnapshotV1 implementation.
Tests compare all device IDs/order, current/lastKnown percentages, precision, source
kind/socSupport/ownership, availability/freshness/connection/reason, UTC times,
serverInstanceId, revision and capture metadata with the typed Windows expectations.
Offline projection stays DISCONNECTED, and existing UI mapping hides live-style
values; no parsed snapshot establishes authenticated PC connectivity.

Eleven controlled negative mutations exercise actual reader rejection on both sides:
malformed UTF-8, duplicate JSON key, unknown enum, invalid calendar timestamp,
non-UTC timestamp, oversized valid JSON with leading whitespace, HyperX precise
claim, HyperX attributed claim, HyperX gateway-source claim, gateway observation
claim, and null current percentage with AVAILABLE. These are mutated Windows bytes,
not valid producer output. Expected digests are never changed to excuse corruption.

## Measured policy differences — compatibility proposals NOT applied

Windows comparison uses the existing strict `ConnectionFoundations/SnapshotWirePolicy`
reader. BatterySnapshotJson itself is the producer serializer/typed validator, not a
public JSON deserializer. Both readers agree on all 14 normal producer outputs and
11 invalid mutations.

| Controlled input | Windows strict reader | Android reader | Impact / separate proposal |
| --- | --- | --- | --- |
| Uppercase UUID text | REJECT | ACCEPT | Producer emits lowercase. For lexical parity, separately review requiring UUID.toString() to equal the Kotlin input. Alternatively explicitly document permissive Android acceptance. |
| Numeric revision `-0` | REJECT | ACCEPT as zero | Producer emits positive publication revisions. If strict lexical parity is desired, separately review a nonnegative-integer token regex without a minus sign in Kotlin. |

These differences are explicitly tested, not hidden as unexplained failures. Neither
production reader is modified. The semantic JSON schema alone does not require
lowercase UUID text or distinguish mathematical negative zero; the stricter Windows
lexical policy is a separate choice needing review.

Potential further difference: Windows uses DateTimeOffset.TryParse for Z-terminated
reader input, while Kotlin has a narrower UTC lexical form. The tested real serializer
outputs (including seven fractional digits and null source times) interoperate.
Arbitrary alternate timestamp spellings are not exhaustively characterized here;
review a shared lexical policy separately if broader parity is required.

## Reproduce without native/security/transport execution

Before the first SDK command set `DOTNET_GENERATE_ASPNET_CERTIFICATE=false` and
`DOTNET_CLI_TELEMETRY_OPTOUT=1`. Restore ONLY the isolated project with its lockfile:

```sh
dotnet restore tests/PeripheralBatteryCompanion.InteropVectors/PeripheralBatteryCompanion.InteropVectors.csproj --locked-mode
```

From Android, verify a fresh actual Windows emission against the vendored bytes:

```sh
python tools/verify-e2-vectors.py --windows-root <windows-checkout> --dotnet <dotnet-executable>
```

This rebuilds/runs only the portable producer with --no-restore into a fresh temporary
directory; it never rewrites expected Android resources. It compares all 29 generated
files exactly (14 publications + 13 mutations + manifest + SHA256SUMS). Existing output
is never overwritten by the producer. Regeneration disagreement is a review failure,
not permission to automatically replace fixtures.

Run Android offline validation:

```sh
source /workspace/.tools/android-env.sh
./gradlew --no-daemon --max-workers=4 testDebugUnitTest assembleDebug assembleRelease lintDebug
```

The environment activation line is cloud-specific; on an owner machine use its normal
JDK 17/SDK setup. Do not invoke the broad Windows solution, test.ps1 or unfiltered
native/security test projects as part of this portable verification.

## Actual validation and limitations

- Official SDK 10.0.403 artifact SHA512 verified before installation; root Windows
  SDK policy is 10.0.402/latestPatch. No Gate B runtime-provenance claim follows.
- Android baseline at 83b83c7: 161 JVM tests PASS before E2 changes.
- Portable Core/HelperProtocol/InteropVectors Release build: PASS, 0 warnings/errors.
- Producer: 14 real serializer publications + 13 controlled mutations; 84 assertions
  PASS. Independent executions and the end-to-end regeneration checker matched all 29 files
  without rewriting any candidate resource.
- Android: 223 JVM tests PASS, 0 failures/errors/skips (161 existing + 62 E2 tests:
  27 parameterized acceptance/semantic tests, 27 exact-byte hash tests, 8 invariants).
- Debug and release APK builds PASS; release unsigned. Lint PASS: 0 errors, 9 existing
  warnings. All 17 canonical outcomes and 20 canonical SHA256 entries remain intact.
- APK inspection: no E2 data/classes in either variant and no INTERNET permission.
- Full Windows regression, native Windows/security/device tests, emulator/Xiaomi,
  instrumentation and end-to-end connectivity NOT RUN. These tests establish offline
  serialization/reader compatibility only. Gate E BLOCKED; Gate B independent.

SDK SETUP CAVEAT: first-run .NET initialization during the initial restore reported
installing a local HTTPS development certificate. This was unintended and is outside
the E2 producer. It was not used or trusted, and was not a Windows CNG/X509 experiment.
All later SDK commands explicitly disable automatic certificate generation; no
certificate trust/cleanup command was run. This side effect is disclosed rather than
claiming the overall setup performed no certificate operation.

No production source, provider, authentication/transport, main UI/manifest, canonical
fixture or gate guard changed. New Windows project dependencies are inherited Core
references, with a new test-project lockfile; no production package changed. Changes
remain on isolated local branches for review, with no commit, push, PR or merge.

## Corrective pre-commit patch

The Windows test-only manifest serializer now sets `NewLine = "\n"` explicitly.
Three added producer assertions require an LF newline policy, emitted LF bytes and
no CRLF sequence. This removes dependence on Environment.NewLine; snapshot serializer
production options and all expected snapshot/canonical bytes remain unchanged.

Android semantic comparisons now require an expectation object for every accepted
publication. Only the exact uppercase-UUID and negative-zero policy IDs may lack
one, with explicit metadata and parsed-value assertions. Three new regressions reject
missing/mistyped expectation blocks, unreviewed policy IDs and inconsistent named
policy metadata. The manifest still contains 14 publication and 13 mutation cases.

Corrective validation ran on Linux with SDK 10.0.403: producer 84 assertions PASS,
all 29 regenerated files byte-identical, 223 JVM tests PASS (0 failures/errors/skips),
debug/release builds and lint PASS (0 errors, 9 existing warnings). Both diff checks
and canonical checksum checks pass. No real Windows runner is attached to this
session, so Windows OS execution is UNVERIFIED. Before claiming cross-OS execution,
run the isolated producer/verifier on Windows and record its actual output; no
native/security experiment or guard modification is needed or authorized for that
portable check. Offline E2 is ready for owner commit review; no commit/push was made.
