# Battery snapshot v1 — proposal only

There is no endpoint or network implementation in A1. This transport-neutral proposal
requires Windows API review before any path, pairing flow or security scheme is finalized.

A future read-only snapshot carries `schemaVersion: 1`, `synthetic: false`,
`pcOnline: boolean`, and `devices: DeviceReading[]`. Device fields are stable `id`,
`name`, nullable integer `batteryPercent` (0–100; null is unknown),
`precision` (PRECISE, APPROXIMATE, ESTIMATE), `source` attribution,
`sourceConfidence` (VERIFIED, UNVALIDATED, UNKNOWN), nullable UTC RFC3339
`observedAt`, `availability` (AVAILABLE, STALE, DEVICE_DISCONNECTED, PC_OFFLINE),
and optional `caution` and `unavailableReason`.

The A1 domain model intentionally represents only the subset needed by the demo;
future wire DTOs must be independently validated before mapping to domain objects.
Reject unsupported schema versions and out-of-range percentages. Never map null to
zero. Missing, stale, disconnected and offline readings must remain distinguishable.
A transport timeout, authentication error, malformed payload, unsupported version,
and unavailable PC are distinct failures; cached readings must be labeled stale.
Do not infer PC health merely from a cached response. Freshness thresholds and clock
skew policy remain open decisions.

Threats include LAN interception, impersonation, replay, stolen mobile credentials,
unauthorized VPN peers, and overexposure of PC inventory. Before implementation,
review mutual authentication, explicit owner-approved pairing, encrypted transport,
credential storage in Android Keystore, revocation, expiry/replay protection,
rate limits and minimal data disclosure. VPN membership alone is not authorization.
Only read-only snapshots are in scope; no device control. No chosen authentication
scheme or HTTP route is promised by this document.
