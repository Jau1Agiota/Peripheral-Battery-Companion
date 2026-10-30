# D3.3B Android native proof design — no execution authorization

[Full shared report, experiment matrix and test inventory](D3_3B_NATIVE_PROOF_DESIGN_REPORT.txt)

Cross-repository design review:

- [Windows canonical documentation](https://github.com/Jau1Agiota/G-Hub-battery-tray-icon-and-smartphone-app-/tree/feature/d3-3b-native-proof-design/docs)
- [Android documentation](https://github.com/Jau1Agiota/Peripheral-Battery-Companion/tree/feature/d3-3b-native-proof-design/docs)

The complete shared decision matrix is present here, not only an external link.
Base Android D3.3A `38351f32e5941d4adb053746521fdb73e94431ae` and Windows D3.3A
`60dc9da8c55a8cdbb3046849badb8881d60ea706` were verified locally and remotely.

Documentation only: no source/tests/CI/configuration/prior report changes, builds,
scripts, native crypto, JSSE/Keystore instantiation, installation or network proof.
Documentation commits use `[skip ci]`; no workflow is dispatched or rerun.

The report covers Android API26/35 proof scope as a proposal, exact narrow-anchor/
IP-SAN/SPKI/chain/EKU/time checks before any application secret, unproven self-signed
leaf anchoring, the HttpsURLConnection pre-header pinning integration issue,
Keystore invalidation/restore/lock considerations and separate physical approval.
It preserves the prior test-scope incident and conservative call-graph inventory.

Next native stage: **REVISE / NO-GO to execution**. Windows key/name policy conflicts,
Android platform enforcement and durable anti-rollback must not be bypassed.
Restored credential state remains `RESTART_LOCKED`. Each proposed experiment needs
separate exact owner approval, with no automatic retry or next-stage progression.

**NATIVE NOT RUN. LAN NOT RUN. PRODUCTION SECURITY NOT PROVEN.**
