package dev.peripheral.battery.foundations

enum class LedgerState { RESTART_LOCKED, READY, UNCERTAIN, CLOSED }
enum class CommitDisposition { COMMITTED_AND_FLUSHED, NOT_COMMITTED, UNCERTAIN }
data class CommitAcknowledgment(val disposition: CommitDisposition, val version: Long)
data class SyntheticCredentialEntry(val phone: String, val credentialLabel: String, val generation: Long,
    val scope: String, val issuedUtc: Long, val expiresUtc: Long, val expiresTick: Long, val revoked: Boolean)
/** Atomic compare-version commit + durable flush contract; no storage adapter. Synthetic labels, NOT tokens.
 * Rollback/conflict/corruption must not return committed. Real anti-rollback/restart proof is unresolved.
 */
fun interface AtomicSyntheticLedger { fun commitAndFlush(expectedVersion: Long, replacement: SyntheticCredentialEntry): CommitAcknowledgment }
class SyntheticCredentialLedger(private val store: AtomicSyntheticLedger, start: EpochSample, private val capacity: Int, newEmptySyntheticSession: Boolean) {
    private val clock = EpochClock(start)
    private val entries = mutableMapOf<String,SyntheticCredentialEntry>()
    private val usedLabels = mutableSetOf<String>()
    private var version = 0L
    // No restore/unlock method. Old/partial/corrupt/rolled-back persisted images cannot restore access.
    var state = if(newEmptySyntheticSession && capacity > 0 && clock.observe(start)) LedgerState.READY else LedgerState.RESTART_LOCKED; private set
    private fun ready(now: EpochSample): Boolean {
        if(state != LedgerState.READY) return false
        if(!clock.observe(now)) { state = LedgerState.UNCERTAIN; return false }
        return true
    }
    private fun label(s: String) = s.length in 1..128 && s.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
    private fun commit(entry: SyntheticCredentialEntry): Boolean {
        if(version == Long.MAX_VALUE) { state = LedgerState.UNCERTAIN; return false }
        val expected = version
        state = LedgerState.UNCERTAIN // deny reads/reentrant actions during possible side effects
        return try {
            val ack = store.commitAndFlush(expected,entry)
            if(state != LedgerState.UNCERTAIN || ack.disposition != CommitDisposition.COMMITTED_AND_FLUSHED || ack.version != expected + 1) false
            else {
                entries[entry.phone] = entry; usedLabels.add(entry.credentialLabel); version = ack.version
                state = LedgerState.READY; true
            }
        } catch (_: Exception) { false }
    }
    @Synchronized fun issueSynthetic(phone: String, id: String, expiresUtc: Long, expiresTick: Long, now: EpochSample): Boolean {
        if(!ready(now) || !label(phone) || !label(id) || entries.containsKey(phone) || usedLabels.contains(id) ||
            usedLabels.size >= capacity || expiresUtc <= now.time.utcSeconds || expiresTick <= now.time.ticks) return false
        return commit(SyntheticCredentialEntry(phone,id,1,"snapshot.read",now.time.utcSeconds,expiresUtc,expiresTick,false))
    }
    @Synchronized fun rotateSynthetic(phone: String, id: String, expiresUtc: Long, expiresTick: Long, now: EpochSample): Boolean {
        if(!ready(now)) return false
        val old = entries[phone] ?: return false
        if(!label(id) || old.revoked || old.generation == Long.MAX_VALUE || usedLabels.contains(id) || usedLabels.size >= capacity ||
            expiresUtc <= now.time.utcSeconds || expiresTick <= now.time.ticks) return false
        return commit(SyntheticCredentialEntry(phone,id,old.generation+1,"snapshot.read",now.time.utcSeconds,expiresUtc,expiresTick,false))
    }
    @Synchronized fun revokeSynthetic(phone: String, now: EpochSample): Boolean {
        if(!ready(now)) return false
        val old = entries[phone] ?: return false
        if(old.revoked) return true
        val revoked = old.copy(revoked=true); entries[phone] = revoked
        return commit(revoked)
    }
    @Synchronized fun readSyntheticDecision(phone: String, id: String, scope: String, now: EpochSample): Boolean {
        if(!ready(now)) return false
        val e = entries[phone] ?: return false
        return !e.revoked && e.credentialLabel == id && scope == "snapshot.read" && e.scope == scope &&
            e.issuedUtc <= now.time.utcSeconds && now.time.utcSeconds < e.expiresUtc && now.time.ticks < e.expiresTick
    }
    @Synchronized fun crashOrLostAcknowledgment() { state = LedgerState.CLOSED }
}
