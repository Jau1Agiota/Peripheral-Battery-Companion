package dev.peripheral.battery.debugharness.diagnostics

import dev.peripheral.battery.contract.*
import dev.peripheral.battery.data.DemoBatteryRepository
import dev.peripheral.battery.debugharness.*
import dev.peripheral.battery.domain.*
import dev.peripheral.battery.mocksession.*
import dev.peripheral.battery.simulation.*
import java.security.MessageDigest
import java.util.UUID

fun interface DiagnosticAssets { fun read(path: String): ByteArray }
interface DiagnosticPlatform {
    fun debugAssetsAccessible(): Boolean
    fun activityDeclarationsIsolated(): Boolean
    fun noInternetOrStoragePermissions(): Boolean
    fun deviceMetadataAvailable(): Boolean
    fun offMainThread(): Boolean
}

/** Fixed finite assertions against the actual parser/policies/models. No copied validator. */
class DiagnosticCatalog(private val assets: DiagnosticAssets, private val platform: DiagnosticPlatform) {
    companion object {
        val ACCEPTED = listOf("demo", "stale-last-known", "unavailable", "zero-and-unknown")
        val REJECTED = listOf("demo-time", "duplicate-device", "duplicate-key", "gateway-observation", "hyperx-confidence", "malformed", "missing-field", "null-available", "percent-range", "quoted-percent", "unknown-enum", "unknown-field", "version")
        val HASHES: Map<String, String> = linkedMapOf(
            "battery-snapshot-v1.schema.json" to "2d9ac5bf5e5263340bcd15736df7a9f6f6c201f5f4ad30b1fb7e858ec694bdc2",
            "battery-snapshot-v1.synthetic.json" to "0f6034ef10f56b7cb1b74367999ab1371c070822bbea9d62dc6c9acde286197d",
            "scenarios.json" to "ffcf9fbb3c656180cc48bfc300fea1d14ef75070ec797b7439d0057109c8363c",
            "golden/demo.json" to "0f6034ef10f56b7cb1b74367999ab1371c070822bbea9d62dc6c9acde286197d",
            "golden/stale-last-known.json" to "81d2e4ba0e3587339c754b703aeed2019bd76da8b81211496ceb0584234cf16b",
            "golden/unavailable.json" to "0397cd81c9236a956fd9b339e9623c91d331cebb335732a4d3179c1909f209bd",
            "golden/zero-and-unknown.json" to "5f82edd0b9fb304b489b7cbcb723db588bba523e47182f21376c59f913fbf4a0",
            "rejected/demo-time.json" to "7c669aa5c316ea695d07340e58c003196e7db671bd8b196ba4e996b63b35fb8b",
            "rejected/duplicate-device.json" to "999d9e0b24e808ed9976ffe87fecf4bf6b373959e553f8f0f2037354ec93b5a1",
            "rejected/duplicate-key.json" to "6f01c5adc87f66777e54d2b7043a288577f69d03a929bc9d846e5af2a14b1e1f",
            "rejected/gateway-observation.json" to "28a4952668a633acfb9c2c598cf626eb7c89be6f02ad076fe9c5b4727ca2450e",
            "rejected/hyperx-confidence.json" to "0424e55ab5a1f3a0e198ee8f05818a237b02c4b66adc9d13a16a34189445914a",
            "rejected/malformed.json" to "ae0b3f00638cc5d55bb5fb22deed8790c6b2b84080dd01ddadb22e8fcfab5557",
            "rejected/missing-field.json" to "17758b02b02f6ff683888e724761227ae26725bc57d715cc18a806c0ecac38ab",
            "rejected/null-available.json" to "ae8ebd271714409e17d7cd9e7e3ddc208a07e8d9b1a4a3fecb5028aa4823d854",
            "rejected/percent-range.json" to "c98a009b72facd937ba23acfc60a12f443bed50fc4b24fa3e7120fa6ef2ae63c",
            "rejected/quoted-percent.json" to "6a7646d3e593541e98d91c3438a43d2213e0fdeddde9475bb4ad288ebad57e04",
            "rejected/unknown-enum.json" to "01163ceba8cd257272059bb4840880a0913b31439652fc2e0e55c42eff5da927",
            "rejected/unknown-field.json" to "c1df2eca2f974521ea18dccf5ba5b04af1c960c9a7cd856466f85d95a00ca0f4",
            "rejected/version.json" to "c03b0dc4f18cdfbfbcf742700ada802e8a738ac1ac25a373f2ef56bab74029c5",
        )
    }
    private val peer = MockPeerIdentity("diagnostic-test-pc", 1)
    private val publisher = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private fun golden(name: String) = assets.read("contracts/golden/$name.json")
    private fun parse(name: String) = BatterySnapshotV1.parse(golden(name))
    private fun live(name: String = "demo", revision: Long = 1) = golden(name).toString(Charsets.UTF_8)
        .replace("\"synthetic\": true", "\"synthetic\": false").replace("\"revision\": 0", "\"revision\": $revision").toByteArray()
    private fun outcome(bytes: ByteArray): Observation = try { BatterySnapshotV1.parse(bytes); Observation.ACCEPTED } catch (_: IllegalArgumentException) { Observation.REJECTED }
    private fun inputs() = HarnessInputs(HarnessFixture.entries.associateWith { assets.read(it.assetPath) })
    private fun model() = DebugHarnessModel(inputs())
    private fun connect(m: DebugHarnessModel) {
        m.enterSession()
        listOf(HarnessAction.BEGIN, HarnessAction.AUTHENTICATE, HarnessAction.BIND, HarnessAction.ONE).forEach(m::act)
    }
    private fun policy() = MockSessionPolicy(object : MockAuthenticationTestSeam {
        override fun authorizes(identity: MockPeerIdentity, scope: String) = identity == peer && scope == "snapshot.read"
        override fun confirmsUncachedInstance(identity: MockPeerIdentity, publisher: UUID) = identity == peer && publisher == this@DiagnosticCatalog.publisher
    }, 10)
    private fun ready(p: MockSessionPolicy): MockSessionHandle {
        val g = requireNotNull(p.begin(peer, MockTick(0)))
        val h = requireNotNull(p.authenticate(g, peer, MockTick(1)))
        check(p.bindInstance(h, publisher, MockTick(1)) == MockOutcome.INSTANCE_BOUND)
        check(p.receive(h, live(), MockTick(2)) == MockOutcome.ACCEPTED)
        return h
    }
    fun cases(): List<DiagnosticCase> = buildList {
        fun invariant(id: String, category: DiagnosticCategory, check: () -> Boolean) {
            add(DiagnosticCase(id, category, Observation.ENFORCED) { if (check()) Observation.ENFORCED else Observation.NOT_ENFORCED })
        }
        ACCEPTED.forEach { name -> add(DiagnosticCase("parser.golden.$name", DiagnosticCategory.PARSER, Observation.ACCEPTED) { outcome(golden(name)) }) }
        REJECTED.forEach { name -> add(DiagnosticCase("parser.rejected.$name", DiagnosticCategory.PARSER, Observation.REJECTED) { outcome(assets.read("contracts/rejected/$name.json")) }) }
        HASHES.forEach { (file, digest) ->
            add(DiagnosticCase("hash.${file.replace('/', '.')}", DiagnosticCategory.HASH, Observation.MATCH) {
                val actual = MessageDigest.getInstance("SHA-256").digest(assets.read("contracts/$file")).joinToString("") { "%02x".format(it) }
                if (actual == digest) Observation.MATCH else Observation.NOT_ENFORCED
            })
        }
        invariant("evidence.identity-order", DiagnosticCategory.EVIDENCE) { parse("demo").devices.map { it.id } == DeviceIdentity.entries.toList() }
        invariant("evidence.zero-null", DiagnosticCategory.EVIDENCE) { val d = parse("zero-and-unknown").devices; d[0].current.batteryPercent == 0 && d[1].current.batteryPercent == null }
        invariant("evidence.current-lastknown", DiagnosticCategory.EVIDENCE) { val d = parse("stale-last-known").devices[0]; d.current.batteryPercent == null && d.lastKnown?.batteryPercent == 99 && d.availability == WireAvailability.STALE }
        invariant("evidence.source-confidence", DiagnosticCategory.EVIDENCE) {
            val d = parse("demo").devices
            d[0].current.source.kind == SourceKind.GHUB_GATEWAY && d[0].current.precision == Precision.PRECISE &&
                d[1].current.precision == Precision.UNKNOWN && d[1].current.batteryPercent == null &&
                d[2].current.source.kind == SourceKind.HYPERX_SUPERVISED && d[2].current.precision == Precision.ESTIMATE && d[2].current.source.ownership == Ownership.AMBIGUOUS
        }
        invariant("evidence.unavailable", DiagnosticCategory.EVIDENCE) { parse("unavailable").devices.all { it.availability == WireAvailability.UNAVAILABLE && it.current.batteryPercent == null } }
        invariant("evidence.synthetic-time-rule", DiagnosticCategory.EVIDENCE) {
            outcome(golden("demo").toString(Charsets.UTF_8).replace("\"readAt\": null", "\"readAt\": \"2026-10-31T12:00:00Z\"").toByteArray()) == Observation.REJECTED
        }
        invariant("evidence.gateway-observation-rule", DiagnosticCategory.EVIDENCE) {
            outcome(live().toString(Charsets.UTF_8).replace("\"observedAt\": null", "\"observedAt\": \"2026-10-31T12:00:00Z\"").toByteArray()) == Observation.REJECTED
        }
        invariant("parser.byte-bound", DiagnosticCategory.PARSER) { outcome(ByteArray(65537)) == Observation.REJECTED }
        invariant("parser.duplicate-key", DiagnosticCategory.PARSER) { outcome(golden("demo").toString(Charsets.UTF_8).replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1").toByteArray()) == Observation.REJECTED }
        invariant("parser.depth-bound", DiagnosticCategory.PARSER) { outcome(("[".repeat(17) + "0" + "]".repeat(17)).toByteArray()) == Observation.REJECTED }
        invariant("parser.invalid-utf8", DiagnosticCategory.PARSER) { outcome(byteArrayOf(0xc3.toByte(), 0x28)) == Observation.REJECTED }
        invariant("parser.wrong-device-order", DiagnosticCategory.PARSER) { outcome(golden("demo").toString(Charsets.UTF_8).replace("g915", "TEMP").replace("g305", "g915").replace("TEMP", "g305").toByteArray()) == Observation.REJECTED }
        invariant("simulator.order-rollback-reset", DiagnosticCategory.SIMULATOR) {
            val sim = OfflineSnapshotSimulation(listOf(
                SimulationStep(SimulationScenario.ZERO_VERSUS_NULL, golden("zero-and-unknown")),
                SimulationStep(SimulationScenario.INVALID_PAYLOAD, assets.read("contracts/rejected/version.json")),
            ))
            val good = sim.advance()
            val bad = sim.advance()
            val okay = good.stepIndex == 0 && good.latestAccepted?.snapshot?.devices?.get(0)?.batteryPercent == 0 && bad.outcome == SimulationOutcome.REJECTED && bad.latestAccepted === good.latestAccepted
            sim.reset(); okay && sim.state.value.outcome == SimulationOutcome.IDLE
        }
        invariant("session.begin-approve-bind-receive", DiagnosticCategory.MOCK_SESSION) { val p = policy(); ready(p); p.state.value.phase == MockPhase.CONNECTED && p.state.value.mockOnly }
        invariant("session.parser-not-authentication", DiagnosticCategory.MOCK_SESSION) { val p = policy(); BatterySnapshotV1.parse(live()); p.state.value.phase == MockPhase.DISCONNECTED }
        invariant("session.progression-duplicate-regression", DiagnosticCategory.MOCK_SESSION) {
            val p = policy(); val h = ready(p)
            p.receive(h, live(revision = 2), MockTick(3)) == MockOutcome.ACCEPTED &&
                p.receive(h, live(revision = 2), MockTick(4)) == MockOutcome.DUPLICATE &&
                p.receive(h, live(), MockTick(5)) == MockOutcome.DENIED && p.state.value.denial == MockDenial.REPLAY_REGRESSION
        }
        invariant("session.invalid-input", DiagnosticCategory.MOCK_SESSION) {
            val p = policy(); val h = ready(p); val old = p.state.value.lastAccepted
            p.receive(h, assets.read("contracts/rejected/version.json"), MockTick(3))
            p.state.value.denial == MockDenial.INVALID_PAYLOAD && p.state.value.lastAccepted === old && p.state.value.display.devices.all { it.batteryPercent == null }
        }
        invariant("session.synthetic-input", DiagnosticCategory.MOCK_SESSION) { val p = policy(); val h = ready(p); p.receive(h, golden("demo"), MockTick(3)); p.state.value.denial == MockDenial.SYNTHETIC_PAYLOAD }
        invariant("session.modified-equal-revision", DiagnosticCategory.MOCK_SESSION) {
            val p = policy(); val h = ready(p)
            p.receive(h, live().toString(Charsets.UTF_8).replace("\"batteryPercent\": 99", "\"batteryPercent\": 98").toByteArray(), MockTick(3))
            p.state.value.denial == MockDenial.MODIFIED_DUPLICATE
        }
        invariant("session.wrong-peer", DiagnosticCategory.MOCK_SESSION) {
            val p = policy(); val g = p.begin(peer, MockTick(0))!!
            p.authenticate(g, peer.copy(identityGeneration = 2), MockTick(1)) == null && p.state.value.denial == MockDenial.WRONG_IDENTITY
        }
        invariant("session.cancel-late-callback", DiagnosticCategory.MOCK_SESSION) { val p = policy(); val h = ready(p); p.cancel(); p.receive(h, live(revision = 2), MockTick(3)) == MockOutcome.IGNORED_CALLBACK && p.state.value.phase == MockPhase.DISCONNECTED }
        invariant("session.stop-terminal", DiagnosticCategory.MOCK_SESSION) { val p = policy(); val h = ready(p); p.stop(); p.begin(peer, MockTick(3)) == null && p.receive(h, live(), MockTick(3)) == MockOutcome.IGNORED_CALLBACK && p.state.value.phase == MockPhase.STOPPED }
        invariant("session.revoke-hides-current", DiagnosticCategory.MOCK_SESSION) { val p = policy(); ready(p); p.revokeTrust(); p.state.value.denial == MockDenial.TRUST_REVOKED && p.state.value.display.devices.all { it.batteryPercent == null } }
        invariant("session.deadline", DiagnosticCategory.MOCK_SESSION) { val p = policy(); ready(p); p.checkDeadline(MockTick(12)); p.state.value.denial == MockDenial.DEADLINE && !p.state.value.display.pcOnline }
        invariant("session.uncertain-clock", DiagnosticCategory.MOCK_SESSION) { val p = policy(); val h = ready(p); p.receive(h, live(), MockTick(3, false)); p.state.value.denial == MockDenial.CLOCK_UNCERTAIN }
        invariant("presentation.opt-in", DiagnosticCategory.PRESENTATION) { val m = model(); m.act(HarnessAction.BEGIN); m.state.value.route == HarnessRoute.LANDING }
        invariant("presentation.mock-not-real", DiagnosticCategory.PRESENTATION) { val m = model(); connect(m); m.state.value.status.contains("MOCK PC session · CONNECTED") && m.state.value.status.contains("No real PC connected") && m.state.value.warning.contains("MOCK / OFFLINE") }
        invariant("presentation.zero-null-accessibility", DiagnosticCategory.PRESENTATION) {
            val m = model(); connect(m); m.act(HarnessAction.ZERO)
            m.state.value.cards[0].current.contains("0%") && m.state.value.cards[1].current.contains("unknown") &&
                m.state.value.cards.all { it.accessibilityLabel.contains("MOCK OFFLINE") && it.accessibilityLabel.contains("Retained evidence only") }
        }
        invariant("presentation.stale-lastknown", DiagnosticCategory.PRESENTATION) { val m = model(); connect(m); m.act(HarnessAction.STALE); m.state.value.cards[0].current.contains("unknown") && m.state.value.cards[0].retained.contains("lastKnown=99") }
        invariant("presentation.reset-no-resurrection", DiagnosticCategory.PRESENTATION) { val m = model(); connect(m); m.reset(); m.act(HarnessAction.LATE); m.state.value.status.contains("DISCONNECTED") && m.state.value.outcome.contains("IGNORED_CALLBACK") }
        invariant("presentation.background-model", DiagnosticCategory.PRESENTATION) { val m = model(); connect(m); m.pause(); m.state.value.status.contains("DISCONNECTED") && m.state.value.cards.all { it.current.contains("unknown") } }
        invariant("presentation.exit-demo-isolation", DiagnosticCategory.PRESENTATION) { val m = model(); connect(m); m.exit(); m.state.value.route == HarnessRoute.LANDING && DemoBatteryRepository().snapshot().devices.map { it.batteryPercent } == listOf(99, 90, 76) }
        invariant("android.debug-asset-access", DiagnosticCategory.ANDROID_RUNTIME, platform::debugAssetsAccessible)
        invariant("android.activity-declarations", DiagnosticCategory.ANDROID_RUNTIME, platform::activityDeclarationsIsolated)
        invariant("android.permission-isolation", DiagnosticCategory.ANDROID_RUNTIME, platform::noInternetOrStoragePermissions)
        invariant("android.nonunique-device-metadata", DiagnosticCategory.ANDROID_RUNTIME, platform::deviceMetadataAvailable)
        invariant("android.worker-thread", DiagnosticCategory.ANDROID_RUNTIME, platform::offMainThread)
        listOf(
            "notrun.instrumentation" to DiagnosticCode.INSTRUMENTATION_NOT_RUN,
            "notrun.visual-accessibility-rotation" to DiagnosticCode.MANUAL_NOT_RUN,
            "notrun.real-windows-connection" to DiagnosticCode.WINDOWS_NOT_TESTED,
            "notrun.release-apk-isolation" to DiagnosticCode.RELEASE_NOT_INSPECTED,
        ).forEach { (id, reason) -> add(DiagnosticCase(id, DiagnosticCategory.NOT_EXECUTED, Observation.ENFORCED, reason) { Observation.NOT_RUN }) }
    }
}
