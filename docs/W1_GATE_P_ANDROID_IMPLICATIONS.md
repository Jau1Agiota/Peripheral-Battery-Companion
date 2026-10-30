# W1 Gate P — Android implications

**REVISE: Windows Gate I is not ready to request.** Documentation only. No Android
activity is authorized. The accepted preparation direction remains one unnamed
nonexportable P-256 key, one PUBLIC-ONLY signed leaf, test-only ACL evidence
**NOT ESTABLISHED**. Production `NonExportable AND CurrentUserAclRestricted` stays
unchanged. No source/test/build/manifest/dependency/asset/CI changes.

Windows report and readiness documents are on
[the Gate P branch](https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/tree/feature/w1-gate-p-runtime-source-reconciliation/docs):
`W1_GATE_P_RUNTIME_RECONCILIATION.txt`, `W1_GATE_P_IMPLEMENTATION_READINESS.md`,
`W1_GATE_P_STATIC_EVIDENCE.txt`.

Owner platform metadata now identifies Windows11 Home build26200.9711, x64 ordinary
interactive/profile-loaded context, installed SDK10.0.402/runtime10.0.13. They remain
**proposed, not selected/locked/testhost-observed** targets. ncryptprov.dll file
presence/version does not establish actual registered/loaded Software KSP behavior.
No owner paths/identities/credentials are included.

Official runtime v10.0.13 tag resolves to
`6aa72daa24f66c01c3d7b6a32519e9d21396deec`. Microsoft release metadata pairs
SDK10.0.402/runtime10.0.13; checksum-verified official Windows x64 runtime ZIP
`.version` instead reports `4099f83977f4d29604a9aeb971fb0a7bc1d5f699`, unresolved
in public dotnet/runtime (422; compare404). Exact source-to-binary mapping is
**INCONCLUSIVE**; owner-loaded runtime remains **UNKNOWN**. No tag/SDK inference
closes this discrepancy.85/87 selected files are unchanged versus v10.0.0, two
changed files concern PEM/composite private-key handling, but this is a bounded
tag comparison and not a completed actual-runtime audit.

Unchanged intended tag paths still show explicit None-policy unnamed creation,
existing-key wrapper, public generator/Create and public DER loading. Forbidden
factories request AllowPlaintextExport; CreateSelfSigned/unnamed CopyWithPrivateKey
can reach private export/import. A newly identified unchanged metadata hazard is
that ExportPolicy's primitive getter can map missing to zero, also the value None.
Future fail-closed presence/length readback design needs review; no probe/fix
implementation or production policy relaxation is permitted here.

A public leaf with `HasPrivateKey=false` is **not an associated TLS server identity**.
It proves no export denial, current-user DACL, native cleanup/nonpersistence,
chain/anchor trust, Android identity/hostname/time validation or TLS connection.
No leaf/key/credential is generated or transferred. No JSSE/Keystore/Android parser,
emulator/device/install, network client/listener, LAN, pairing or durable-auth work.

Windows source mapping, complete callees/import/testhost review and exact SDK/runtime
locking remain blockers. Exact canonical private IPv4/ULA, serial, UTC bounds/clock
confidence, criticalities and resource/output/consent terms remain owner inputs;
none becomes an executable default. No need to reapprove the accepted preparation
direction. Later real server association and independent chain + literal-IP type7
SAN + SPKI validation before any credential remain separately gated; no DNS/CN
fallback, silent identity migration or trust-all/pin-only substitute. Restored
credentials remain RESTART_LOCKED; anti-rollback/revocation is unproven.

Android W1-PREP baseline:`a69f59411567595e13079b1660b4d9365edcf28b` (draftPR7);
Windows:`a02d7792538893bd4ed95c590bf09e13c2155fe0` (draftPR18). Both verified
open/draft/unmerged, clean, S0 bases before branching. This new branch targets
`feature/w1-prep-isolated-implementation-spec` in both repositories, not main.
Only this NEW Android document changes. Historical reports/assets/recovery exports
and D3.3A11-certificate+two-secret-sample incident remain intact. No unfiltered legacy
runner, negative-only filters, old key/journal inspection or Option2 operation.

Publication uses `[skip ci]`; remote exact SHAs/Actions checked separately. Skipped
CI is not build/native verification. Gate I/B/E remain independent and unapproved.

**NATIVE NOT RUN. TESTS NOT RUN. LAN NOT RUN. PRODUCTION SECURITY NOT PROVEN.**

Stop for owner review. No automatic implementation, execution, merge or release.
