# W1-PREP Android dependencies — documentation only

[Windows implementation specification](https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/blob/feature/w1-prep-isolated-implementation-spec/docs/W1_PREP_ISOLATED_IMPLEMENTATION_SPEC.txt) ·
[Owner inspection and approval gates](https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/blob/feature/w1-prep-isolated-implementation-spec/docs/W1_PREP_APPROVAL_GATES.md) ·
[Static evidence](https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/blob/feature/w1-prep-isolated-implementation-spec/docs/W1_PREP_STATIC_EVIDENCE.txt)

S0 bases verified locally/remotely before branching: Android
`a9c933d01af446ec06fb802fbb9d7f4c3799b2d5`, Windows
`3c374e6f595f62c0cb011888bf764ba01b9cba98`. S0 PR6/PR17 remained draft and
unmerged. New branch targets the corresponding S0 branch, not main.

The owner accepts **preparation** for one unnamed nonexportable P-256 key and one
**public-only** signed leaf, with test-only key ACL evidence **NOT ESTABLISHED**.
This supersedes the pending preparation-direction decision in historical S0 notes;
those notes are preserved rather than edited. It grants no runnable implementation,
build, discovery, crypto operation, Android execution or networking permission.
Production `NonExportable AND CurrentUserAclRestricted` remains mandatory/unchanged.

## Platform and source mapping remain blocked

Actual current Windows edition/build/architecture, SDK/runtime servicing selection
and Software KSP context are **UNKNOWN**. `global.json`, historical Windows SDK and
Linux CI do not establish them. Windows approval gates contain **one owner-only
read-only metadata command**, not executed in cloud. It prints no user paths/SIDs;
installed .NET versions and KSP DLL metadata do not prove selected runtime or a
working/registered provider. Exact servicing-source reconciliation must precede
an independent implementation request. No platform facts or binary hashes invented.

The inherited v10.0.0 source findings remain version-pinned inferences: implicit
ECDsa factory may request plaintext export; unnamed-key public private-association
path may export/import. Proposed W1 excludes those factories, `CreateSelfSigned`,
`CopyWithPrivateKey`, private export/import and any named/persisted workaround.
It uses an explicit None-policy key, existing-key wrapper and reviewed public
generator/`CertificateRequest.Create` overload. No API is implemented or called here.

## What public-leaf W1 would and would not establish

An eventual separately approved W1 could examine requested/observed key metadata
and one public leaf's exact type7 IP SAN, independent SHA256 SPKI, serverAuth EKU,
digitalSignature, non-CA and UTC validity fields, with `HasPrivateKey=false`.
That public leaf is **not an associated usable TLS server certificate**. It proves
no chain/anchor trust, Android path/hostname enforcement, native export denial,
owner-DACL restriction, OS nonpersistence or successful native cleanup. Managed
Dispose is limited evidence; ambiguous ownership/release must stop without deletion.

No leaf bytes/credentials/keys are generated or transferred to Android in W1-PREP.
No Android project/test/manifest/dependency/Keystore changes, installation,
emulator, native parsing/handshake, pairing or listener/client operation occurs.

## Future Android work still needs separate approval

- A real server private-key association/lifetime proof; public W1 does not supply it.
- Exact canonical private IPv4/ULA + type7 SAN and independently derived SPKI pin,
  with authentic per-PC trust and normal platform chain/hostname/EKU/time validation
  before any application credential. Legacy Windows DNS policy stays separate;
  no CN/DNS alias fallback, silent TOFU or identity migration now.
- Independently proved self-signed leaf-anchor compatibility, supported API/provider/
  patch matrix and actual Xiaomi17t runtime facts, all still unknown/unproven.
- An audited pre-request TLS validation boundary, no trust-all/pin-only bypass,
  permissive verifier, HTTP fallback or credential-bearing redirects.
- Separate JSSE/Keystore/storage/backup/invalidation/device-lock approval. Durable
  revocation/anti-rollback is unproven and restored credentials remain `RESTART_LOCKED`.
- A lossless snapshot adapter preserving source/confidence, readAt versus observedAt,
  lastKnown, stale/unknown and honest demo/offline labels; no invented live status.

## Gates and current result

Windows Gate P collects metadata/static servicing reconciliation, Gate I authorizes
only future isolated source, Gate B separately permits reviewed builds/dependency/
discovery, and Gate E separately permits exactly one reviewed native case. Only
preparation is approved now. No broad test/CI runner or automatic stage progression.
The D3.3A11 certificate + two secret-sample incident stays explicit; no unfiltered
legacy tests, previews or Option2. Old keys/journals/IDs remain untouched.

Only this new Android document changes. Source/tests/CI/old records/configuration
unchanged; `[skip ci]` retained on publication, no jobs dispatched or rerun.
Next owner input is the separate Windows metadata inspection, not Android execution.

**NATIVE NOT RUN; TESTS NOT RUN; LAN NOT RUN; PRODUCTION SECURITY NOT PROVEN.**

Stop for owner review. No runnable W1, merge, release or production activation.
