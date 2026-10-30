# S0 cross-platform identity implications — static review only

[Canonical Windows S0 report](https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/blob/feature/s0-static-windows-api-policy-review/docs/S0_STATIC_WINDOWS_API_POLICY_REVIEW.txt)
and [future W1 gate checklist](https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/blob/feature/s0-static-windows-api-policy-review/docs/S0_W1_EXECUTION_GATE_CHECKLIST.md).

Base: Android D3.3B `cba7f15fbb0ee2dbb3e3a06d26b16038a905bb5e`, Windows D3.3B
`a6fa1fb35fc3b28d517844e1d0afc3ef7867ee08`. Local/remote heads matched before
branching. Source, tests, manifest, CI, fixtures and prior reports are unchanged.
No build, test, discovery, script, JSSE/Keystore object or emulator/device ran.
Documentation commits retain `[skip ci]` to avoid automatic execution.

## Static findings that affect Android proof

The Microsoft public contract documents null-name CNG creation as ephemeral and
`CngExportPolicies.None` as not exportable. This is a policy request, not production
enforcement proof. Current-user scope does not prove an owner-restricted DACL;
the native security-descriptor property is documented for persistent keys only.
A proposed ACL-evidence exception would be **test-only**, never production approval.

Official **v10.0.0** Windows implementation research found two hazards:

- `ECDsa.Create(nistP256)` reaches a factory requesting `AllowPlaintextExport`.
- `CreateSelfSigned` calls `CopyWithPrivateKey`; for an unnamed ECDSA key the
  inspected public association path falls back to private export/import. That is
  incompatible with a no-private-export, nonexportable W1 candidate.

These are version-pinned source inferences, not observations of the owner's runtime.
The exact Windows/.NET patch must be supplied and reconciled before future approval.
No alternative association API, policy relaxation or internal helper is implemented.

The proposed revision is an explicitly created null-name None-policy key and a
**public leaf** signed by it using the reviewed `CertificateRequest.Create` generator
overload. That overload documents `HasPrivateKey=false`; its inspected path avoids
public `CopyWithPrivateKey`. Owner must approve the narrower objective before an
executable request. Such a leaf is **not a proven usable server TLS identity**, and
cannot by itself enable any Android connection or pairing.

## Identity contract remains strict

The D3 production candidate remains one exact canonical private IPv4 or IPv6 ULA,
type7 IP SAN plus independent SHA256 pin of DER SubjectPublicKeyInfo. Windows
`SubjectAlternativeNameBuilder.AddIpAddress` must encode IP address bytes, not a
DNS SAN containing numeric text. No CN/wildcard/DNS alias fallback or silent IP
change. The older Windows DNS-oriented policy is not equivalent and remains intact;
a separately reviewed migration is needed before production integration.

Android's future proof must read actual peer certificate SAN type7 values, validate
canonical literal semantics/address bytes with no DNS resolution, perform normal
platform chain/anchor/hostname/validity/EKU checks, and compare an independently
derived SPKI hash against authentic stored trust. A public leaf, matching pin,
public signature or caller-supplied synthetic evidence does not replace those checks.

Self-signed leaf anchoring on Android remains **unproven**. An offline public Windows
leaf does not prove path trust, certificate revocation or hostname enforcement.
API26/API35 coverage and the Xiaomi17t's actual Android/HyperOS/provider/patch remain
future scope decisions, not facts established by minSdk/compileSdk or prior install.
No certificate export/transfer/import or native Android parsing is authorized now.

## Future Android dependencies remain gated

- Separately approved Windows key/certificate association and ownership lifecycle
  before claiming a real TLS server identity.
- Independently reviewed per-PC narrow-anchor chain validation, exact IP SAN and
  SPKI enforcement **before application secrets**; no trust-all, pin-only bypass,
  permissive verifier, silent TOFU, HTTP fallback or credential-bearing redirect.
- A proven pre-request gate: “connect, inspect pin, then add Authorization” on an
  already-connected `HttpsURLConnection` is not assumed valid. The D3.3B JSSE/socket
  or strict platform-delegating integration question remains unresolved.
- Separate approval for any native JSSE parsing/handshake, loopback/LAN listener,
  emulator, phone installation or physical test. No generic W1 approval covers them.
- Keystore wrapping-key/backup/invalidation/lock behavior and durable anti-rollback
  still unproven. Protected ciphertext is not freshness; restored credentials
  remain `RESTART_LOCKED`, with no unlock shortcut.
- Wire provenance and demo/live separation unchanged: no invented observation times,
  unknown-as-zero, receipt-as-measurement or connection state inferred from samples.

Sources: [CNG null-name contract](https://learn.microsoft.com/en-us/windows/win32/api/ncrypt/nf-ncrypt-ncryptcreatepersistedkey),
[export policy](https://learn.microsoft.com/en-us/dotnet/api/system.security.cryptography.cngexportpolicies?view=net-10.0),
[key security-descriptor restriction](https://learn.microsoft.com/en-us/windows/win32/seccng/key-storage-property-identifiers),
[IP SAN encoding](https://learn.microsoft.com/en-us/dotnet/api/system.security.cryptography.x509certificates.subjectalternativenamebuilder.addipaddress?view=net-10.0).
Exact source paths, inference limits and full call graph are in the canonical report.

Recommendation for a future W1 request: **REVISE**. Owner first decides whether to
accept a public-leaf-only experiment with ACL evidence explicitly unestablished.
This does not authorize implementation, native execution or Android work.

**Native NOT RUN. LAN NOT RUN. Production security NOT PROVEN. Stop for review.**
