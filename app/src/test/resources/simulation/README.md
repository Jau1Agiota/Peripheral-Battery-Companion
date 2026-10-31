# Noncanonical simulation-only inputs

These JSON files are deterministic test data, not Windows fixtures or real telemetry.
Canonical files and SHA256SUMS remain unchanged under ../contracts/.

revision-one.json and revision-two.json are independent copies of the canonical
demo shape with synthetic=false, revisions 1/2, and the same fixed sample instance.
instance-change.json uses a second fixed sample UUID and revision 0. All timestamps
remain null. These inputs exercise nonsynthetic parsing without asserting live data.

not-supported.json copies the canonical unavailable shape and explicitly changes
G915 to NOT_SUPPORTED with reason UNSUPPORTED and a null current percentage.

They are JVM test resources; the application does not load or bundle them at runtime.
