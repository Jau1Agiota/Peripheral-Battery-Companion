# W1 Gate P blocker resolution — Android implications only

**Recommendation: REVISE pending the Windows owner design choices.** No Android
code/test/project/manifest/asset/configuration or runtime changes; no execution.

Windows canonical documents on
[the blocker-resolution branch](https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/tree/feature/w1-gate-p-blocker-resolution/docs):

- `W1_GATE_P_BLOCKER_RESOLUTION.txt` — provenance and evidence grades.
- `W1_GATE_P_METADATA_AND_HOST_GATES.md` — public metadata/host designs and gates.
- `W1_GATE_P_OWNER_DECISIONS.md` — pre-I versus pre-E decisions.

**P1 resolved at source-provenance level:** checksum-verified runtime10.0.13 ZIP
embedded revision `4099f83977f4d29604a9aeb971fb0a7bc1d5f699` belongs to official
`dotnet/dotnet` VMR v10.0.13. Its immutable
[source manifest lines81–86](https://github.com/dotnet/dotnet/blob/4099f83977f4d29604a9aeb971fb0a7bc1d5f699/src/source-manifest.json#L81-L86)
identifies runtime commit `6aa72daa24f66c01c3d7b6a32519e9d21396deec`, the public
runtime tag.94 selected VMR/constituent source files match byte-for-byte. This
explains the prior runtime-repo lookup failure; older reports stay unchanged.
Documented manifest plus static text correspondence is not reproducible-build,
selected/loaded-owner-binary, KSP or runtime behavior proof.

**P2 specified:** public GetProperty/GetValue readback distinguishes missing,
malformed, provider error, mismatch and exact expected metadata; none substitutes
missing for zero/None/current-user. CLR marker/name observations are not native
nonpersistence evidence. Public API allocates provider-reported length before caller
bounds checks and clones values; accepted response bounds are not hard process/native
memory limits. No PInvoke/reflection/export probe/native metadata call made.

**P3 specified:** exact SDK10.0.402/CWD/global.json disable and Core runtime10.0.13 /
rollForward Disable, explicit x64 testhost path/architecture and no fallback proposal.
VSTest17.14.1 source commit `490850ae3fdc1b470e3804ceab4f6a41cf89ae51` reviewed for
runtimeconfig and host fallback paths. Packages/actual host/runtime are not resolved
or observed; later independently approved B/E evidence remains required.

**P4 prepared:** owner chooses exact target and disclosed allocation/timeout/limited
cleanup-evidence model before Gate I; exact test SAN/serial/UTC/clock-confidence,
criticalities/output/consent are needed before E. No real address/serial/permit
invented. Accepted one unnamed nonexportable P-256 key plus PUBLIC-only signed leaf
and TEST-ONLY ACL NOT ESTABLISHED direction is not reopened. Production
NonExportable AND CurrentUserAclRestricted stays unchanged.

A W1 PUBLIC leaf with HasPrivateKey=false is **not an associated usable TLS server
identity** and grants **no Android activity**. No leaf/key/credential created or
transferred. No Android parser/JSSE/Keystore/device/emulator/install, pairing,
trust store, chain/anchor/hostname/time/SPKI validation, listener/TLS/LAN or durable
auth operations authorized. No DNS/CN fallback, silent identity migration or trust-all
substitute. Restored credentials remain RESTART_LOCKED; revocation/anti-rollback
remain unproven. A future server identity and Android proof need separate gates.

Baselines verified clean, remote-matching, open/draft/unmerged: Windows PR19
`9a913fd4bfb2d4a5449ea466adb7f6af489e57c1`; Android PR8
`d49b97e27de1288c053d771ac50ba981669d3a9a`. Their base is W1-PREP. New branch in
both repositories:`feature/w1-gate-p-blocker-resolution`, new draft PRs target
`feature/w1-gate-p-runtime-source-reconciliation`. Only this NEW Android document
changes. All source/assets/recovery exports/history/older docs preserved.

D3.3A11 certificate-generation + two secret-sample out-of-scope cases remain
explicit. No old test.ps1/Option2/preview/key/journal inspection/cleanup, native CI,
negative-only legacy selection or automatic fallback/retry. Documentation commits
use `[skip ci]`; remote SHAs/changed-file allowlists/Actions checked after publication.

**NATIVE NOT RUN / TESTS NOT RUN / LAN NOT RUN / PRODUCTION SECURITY NOT PROVEN.**

Stop for owner review. Gate I/B/E remain unapproved; no merge or release.
