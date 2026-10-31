package dev.peripheral.battery.debugharness

import org.junit.Assert.*
import org.junit.Test

class DebugHarnessModelTest {
    private fun inputs() = HarnessInputs(HarnessFixture.entries.associateWith { f ->
        requireNotNull(javaClass.getResourceAsStream("/${f.assetPath}")).use { it.readBytes() }
    })
    private fun model() = DebugHarnessModel(inputs())
    private fun connect(m: DebugHarnessModel) {
        m.enterSession()
        listOf(HarnessAction.BEGIN, HarnessAction.AUTHENTICATE, HarnessAction.BIND, HarnessAction.ONE).forEach(m::act)
    }
    @Test fun viewModelStoreRetainsRotationStateAndClearFencesOldModel() {
        val store = androidx.lifecycle.ViewModelStore()
        val factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return model() as T
            }
        }
        val first = androidx.lifecycle.ViewModelProvider(store, factory)[DebugHarnessModel::class.java]
        connect(first)
        val before = first.state.value
        val recreatedOwner = androidx.lifecycle.ViewModelProvider(store, factory)[DebugHarnessModel::class.java]
        assertSame(first, recreatedOwner); assertEquals(before, recreatedOwner.state.value)
        store.clear()
        assertTrue(first.state.value.status.contains("STOPPED"))
        val cleared = first.state.value
        first.reset(); first.act(HarnessAction.BEGIN); first.act(HarnessAction.LATE)
        assertEquals(cleared, first.state.value)
        val fresh = androidx.lifecycle.ViewModelProvider(store, factory)[DebugHarnessModel::class.java]
        assertEquals(HarnessRoute.LANDING, fresh.state.value.route)
        store.clear()
    }
    @Test fun explicitLandingAndActionIsolation() {
        val m = model(); val before = m.state.value
        assertEquals(HarnessRoute.LANDING, before.route)
        m.act(HarnessAction.BEGIN); m.advanceOffline()
        assertEquals(before, m.state.value)
        assertTrue(before.warning.contains("No real PC"))
    }
    @Test fun offlineStepsAreOrderedAndInvalidPreservesEvidence() {
        val m = model(); m.enterOffline()
        m.advanceOffline(); assertTrue(m.state.value.cards[0].current.contains("99%"))
        m.advanceOffline(); assertTrue(m.state.value.cards[0].current.contains("0%")); assertTrue(m.state.value.cards[1].current.contains("unknown"))
        m.advanceOffline(); assertTrue(m.state.value.cards[0].current.contains("unknown"))
        assertTrue(m.state.value.cards[0].retained.contains("lastKnown=99"))
        m.advanceOffline(); val cards = m.state.value.cards
        m.advanceOffline(); assertEquals(cards, m.state.value.cards)
        assertTrue(m.state.value.outcome.contains("REJECTED"))
        assertTrue(m.state.value.outcome.contains("INVALID_PAYLOAD"))
    }
    @Test fun parserOrFakeApprovalAloneNeverShowsMockConnected() {
        val m = model(); m.enterSession()
        m.act(HarnessAction.ONE); assertFalse(m.state.value.status.contains("· CONNECTED"))
        m.act(HarnessAction.BEGIN); m.act(HarnessAction.AUTHENTICATE)
        assertTrue(m.state.value.status.contains("AWAITING_RESPONSE"))
        assertTrue(m.state.value.cards.all { it.current.contains("unknown") })
    }
    @Test fun connectedAlwaysHasMockAndOfflineWarnings() {
        val m = model(); connect(m)
        assertTrue(m.state.value.status.contains("MOCK PC session · CONNECTED"))
        assertTrue(m.state.value.status.contains("No real PC connected"))
        assertTrue(m.state.value.warning.contains("MOCK / OFFLINE"))
        assertTrue(m.state.value.cards[2].current.contains("Estimate 76%"))
        assertTrue(m.state.value.cards[2].source.contains("AMBIGUOUS"))
    }
    @Test fun rollbackRejectsAndHidesCurrentButLabelsRetainedEvidence() {
        val m = model(); connect(m); m.act(HarnessAction.TWO); m.act(HarnessAction.REPLAY)
        assertTrue(m.state.value.outcome.contains("REPLAY_REGRESSION"))
        assertTrue(m.state.value.cards.all { it.current.contains("unknown") })
        assertTrue(m.state.value.cards[0].retained.contains("Retained evidence only"))
    }
    @Test fun invalidAndSyntheticInputsAreRejected() {
        val m = model(); connect(m); m.act(HarnessAction.INVALID)
        assertTrue(m.state.value.outcome.contains("INVALID_PAYLOAD"))
        m.reset(); connect(m); m.act(HarnessAction.SYNTHETIC)
        assertTrue(m.state.value.outcome.contains("SYNTHETIC_PAYLOAD"))
    }
    @Test fun cancellationAndLateCallbackNeverResurrectSession() {
        val m = model(); connect(m); m.act(HarnessAction.CANCEL); m.act(HarnessAction.LATE)
        assertTrue(m.state.value.outcome.contains("IGNORED_CALLBACK"))
        assertTrue(m.state.value.status.contains("DISCONNECTED"))
        m.act(HarnessAction.BEGIN); m.act(HarnessAction.AUTHENTICATE); m.act(HarnessAction.BIND)
        m.act(HarnessAction.LATE)
        assertTrue(m.state.value.status.contains("AWAITING_RESPONSE"))
    }
    @Test fun resetCreatesFreshEnginesAndFencesOldHandles() {
        val m = model(); connect(m); m.act(HarnessAction.STOP); m.reset(); m.act(HarnessAction.LATE)
        assertTrue(m.state.value.status.contains("DISCONNECTED"))
        assertTrue(m.state.value.outcome.contains("IGNORED_CALLBACK"))
        connect(m); assertTrue(m.state.value.status.contains("· CONNECTED"))
    }
    @Test fun exitRestoresLandingAndNewInstanceHasNoPersistentOptIn() {
        val m = model(); connect(m); m.exit()
        assertEquals(HarnessRoute.LANDING, m.state.value.route)
        assertEquals(HarnessRoute.LANDING, model().state.value.route)
        m.act(HarnessAction.ONE); assertEquals(HarnessRoute.LANDING, m.state.value.route)
    }
    @Test fun backgroundPauseHidesDisplayAndNeverReconnectsImplicitly() {
        val m = model(); connect(m); m.pause()
        assertTrue(m.state.value.status.contains("DISCONNECTED"))
        assertTrue(m.state.value.cards.all { it.current.contains("unknown") })
        m.enterSession(); m.act(HarnessAction.ONE)
        assertTrue(m.state.value.outcome.contains("No fake handle"))
    }
    @Test fun switchingToOfflineInvalidatesMockSession() {
        val m = model(); connect(m); m.enterOffline(); m.enterSession()
        assertTrue(m.state.value.status.contains("DISCONNECTED"))
        m.act(HarnessAction.LATE); assertTrue(m.state.value.outcome.contains("IGNORED_CALLBACK"))
    }
    @Test fun zeroUnavailableUnsupportedAndStaleAreNotPcSessionStates() {
        val m = model(); connect(m)
        m.act(HarnessAction.ZERO)
        assertTrue(m.state.value.cards[0].current.contains("0%"))
        assertTrue(m.state.value.cards[1].current.contains("unknown"))
        m.act(HarnessAction.UNAVAILABLE)
        assertTrue(m.state.value.status.contains("· CONNECTED"))
        assertTrue(m.state.value.cards.all { it.current.contains("unknown") })
        m.act(HarnessAction.UNSUPPORTED)
        assertTrue(m.state.value.cards[0].availability.contains("NOT_SUPPORTED"))
        m.act(HarnessAction.STALE)
        assertTrue(m.state.value.cards[0].retained.contains("lastKnown=99"))
        assertTrue(m.state.value.cards[0].current.contains("unknown"))
    }
    @Test fun accessibilityStatesCurrentRetainedConfidenceAndMockBoundary() {
        val m = model(); connect(m)
        m.state.value.cards.forEach { card ->
            assertTrue(card.accessibilityLabel.contains("MOCK OFFLINE"))
            assertTrue(card.accessibilityLabel.contains("Displayed current"))
            assertTrue(card.accessibilityLabel.contains("Retained evidence only"))
            assertTrue(card.accessibilityLabel.contains("ownership="))
            assertTrue(card.accessibilityLabel.contains("Supplied times only"))
        }
    }
    @Test fun duplicateDoesNotAdvanceRevisionOrExtendExplicitDeadline() {
        val m = model(); connect(m); m.act(HarnessAction.TWO)
        val publication = m.state.value.publication
        m.act(HarnessAction.DUPLICATE)
        assertTrue(m.state.value.outcome.contains("DUPLICATE"))
        assertEquals(publication, m.state.value.publication)
        m.act(HarnessAction.EXPIRE)
        assertTrue(m.state.value.status.contains("DISCONNECTED"))
    }
}
