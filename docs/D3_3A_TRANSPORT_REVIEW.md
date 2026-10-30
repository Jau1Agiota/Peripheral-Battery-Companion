# D3.3A Android inert TLS transport
Canonical architecture, crash model, platform proof gates and validation incident:
https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/blob/feature/d3-3a-inert-tls-transport/docs/D3_3A_TRANSPORT_REVIEW.md

Independent Kotlin contracts/state models; no concrete I/O adapter or activation.
AuthenticatedTlsPeer has no issuer; DisabledTlsBoundary always blocks, including
snapshot-source invocation. Synthetic trust metadata cannot become native proof.
Intended later Android stack is a dedicated per-PC HttpsURLConnection/
SSLSocketFactory with narrow exact anchor, normal platform chain+hostname checks
and additional exact IP SAN/SPKI validation. No platform TLS object is constructed.
Server counterpart candidate is isolated Kestrel/.NET TLS with separately proven
ephemeral identity lifecycle; no native implementation approved in this stage.

Synthetic credential commit model is atomic-CAS/flush by contract, not actual
storage. Uncertainty denies; rotation rejects old labels; revoke denies before
callback and serializes with reads. Restored state always RESTART_LOCKED with
no unlock API. Durable anti-rollback/revocation recovery remains a hard blocker.
Boot/time authority, response-write authorization and real storage remain unproven.

Shared identity/credential/freshness/strict-payload fixture decisions match Windows.
Original v1 schema/D3.2 fixtures preserved. StrictJsonGuard additionally rejects
negative-zero integer and unpaired surrogate escapes; no new UI adapter or network
permission. No dashboard/navigation/demo/monitoring changes or runtime dependencies.

Windows validation incident is explicitly retained: an initial legacy unfiltered
test run generated in-memory certificates and real-secret samples beyond scope.
Subsequent Windows execution excludes those cases, and D3.3A CI is scoped.
Android tests invoke no TLS/Keystore/network/device APIs.

NO-GO for native proof until a separately reviewed D3.3B plan receives explicit
owner approval. No Option2, old IDs/resources, sockets, real pairing/storage,
phone installation, merge or release. See D3_3A_IMPLEMENTATION_REPORT.txt.
