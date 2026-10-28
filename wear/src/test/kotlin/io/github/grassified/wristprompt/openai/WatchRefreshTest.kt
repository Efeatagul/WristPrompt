package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.SecretRecord
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class WatchRefreshTest {
    private class Record : SecretRecord {
        var data: ByteArray? = null
        var failWrite = false
        override fun read() = data?.clone()
        override fun write(plaintext: ByteArray) { if (failWrite) error("Synthetic storage failure"); data = plaintext.clone() }
    }
    private var now = 1_000_000L
    private suspend fun seed(record: Record, seconds: Long = 3600): Pair<WatchProfiles, ProfileTransfer> {
        val vault = WatchProfiles(record) { now }; val p = testProfile(now).copy(expiresIn = seconds)
        val request = AuthStartRequest(1, BridgeProtocol.newRequestId(), p.hostId, now)
        vault.prepare("phone", request)
        val transfer = ProfileTransfer(1, request.requestId, BridgeProtocol.newRequestId(), p)
        vault.accept("phone", ProfileProtocol.encode(transfer))
        return vault to transfer
    }
    private fun persisted(record: Record): CredentialProfile = Json.decodeFromJsonElement(
        Json.parseToJsonElement(record.data!!.decodeToString()).jsonObject.getValue("profiles").jsonArray.single())
    private fun renewed(old: CredentialProfile) = old.copy(accessToken = "new-access", refreshToken = "new-refresh", savedAtEpochMs = now)

    @Test fun validTokenIsLoadedWithoutRefreshing() = runBlocking {
        val (vault, _) = seed(Record())
        assertEquals("synthetic-access", vault.credential { error("Must not refresh") }.accessToken)
    }
    @Test fun concurrentRequestsRefreshOnceAndReadDurableRotatedState() = runBlocking {
        val record = Record(); val (vault, _) = seed(record); now = 4_550_000
        var calls = 0
        val leases = (1..10).map { async(Dispatchers.Default) {
            vault.credential { old -> calls++; delay(30); renewed(old) }
        } }.awaitAll()
        assertEquals(1, calls); assertTrue(leases.all { it.accessToken == "new-access" })
        assertEquals("new-refresh", persisted(record).refreshToken)
        val reopened = WatchProfiles(record) { now }
        assertEquals("new-access", reopened.credential { error("Must not rotate again") }.accessToken)
    }
    @Test fun cancellationDoesNotDiscardSuccessfulRotationBeforePersistence() = runBlocking {
        val record = Record(); val (vault, _) = seed(record); now = 4_550_000
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val caller = launch(Dispatchers.Default) { vault.credential { old -> started.complete(Unit); release.await(); renewed(old) } }
        started.await(); caller.cancel(); release.complete(Unit); caller.join()
        assertEquals("new-refresh", persisted(record).refreshToken)
        assertEquals("new-access", vault.credential { error("Must not reuse old refresh") }.accessToken)
    }
    @Test fun definitiveTransientFailurePreservesTokensAndRequiresBoundedRetry() = runBlocking {
        val record = Record(); val (vault, _) = seed(record); now = 4_550_000
        try { vault.credential { throw ApiFailure(ApiProblem.TEMPORARY, 503) }; fail("Expected transient error") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.TEMPORARY, failure.problem) }
        assertEquals("synthetic-refresh", persisted(record).refreshToken)
        var retried = false
        try { vault.credential { retried = true; renewed(it) }; fail("Expected cooldown") } catch (_: ApiFailure) { }
        assertFalse(retried)
        now += 5001
        assertEquals("new-access", vault.credential { renewed(it) }.accessToken)
    }
    @Test fun terminalRefreshFailureClearsUnusableTokensButKeepsRegistrationAndHint() = runBlocking {
        val record = Record(); val (vault, original) = seed(record); now = 4_550_000
        try { vault.credential { throw ApiFailure(ApiProblem.REAUTHORIZE, 400, "invalid_grant") }; fail("Expected reauth") } catch (_: ApiFailure) { }
        val profile = persisted(record)
        assertNull(profile.accessToken); assertNull(profile.refreshToken); assertTrue(profile.reauthorizationRequired)
        assertEquals(original.profile.identity, profile.identity); assertEquals(original.profile.idToken, profile.idToken)
        assertTrue(vault.state.value.reauthorizationRequired)
    }
    @Test fun interruptedGrantAndProcessRestartNeverReuseUncertainRefreshToken() = runBlocking {
        val record = Record(); val (vault, _) = seed(record); now = 4_550_000
        try { vault.credential { throw ApiFailure(ApiProblem.NETWORK) }; fail("Expected uncertainty") } catch (_: ApiFailure) { }
        // Keep useful encrypted state after network loss, but never submit the old rotating token twice.
        assertEquals("synthetic-refresh", persisted(record).refreshToken)
        val reopened = WatchProfiles(record) { now }; reopened.load()
        assertTrue(reopened.state.value.reauthorizationRequired)
        try { reopened.credential { error("Must not retry uncertain grant") }; fail("Expected reauth") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, failure.problem) }
    }
    @Test fun failedPreGrantStorageWriteSendsNothing() = runBlocking {
        val record = Record(); val (vault, _) = seed(record); now = 4_550_000; record.failWrite = true
        var sent = false
        try { vault.credential { sent = true; renewed(it) }; fail("Expected storage failure") } catch (_: Exception) { }
        assertFalse(sent); assertTrue(vault.state.value.storageError)
    }
    @Test fun postGrantStorageFailureCannotReuseTheOldRotatingToken() = runBlocking {
        val record = Record(); val (vault, _) = seed(record); now = 4_550_000
        try {
            vault.credential { old -> record.failWrite = true; renewed(old) }
            fail("Expected persistence failure")
        } catch (_: Exception) { }
        record.failWrite = false
        val reopened = WatchProfiles(record) { now }
        try { reopened.credential { error("Must not replay consumed token") }; fail("Expected reauth") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, failure.problem) }
        assertEquals("synthetic-refresh", persisted(record).refreshToken)
    }
    @Test fun authAttemptBlocksRefreshAndInvalidatesAnOlderLease() = runBlocking {
        val record = Record(); val (vault, transfer) = seed(record)
        val lease = vault.credential { error("Must not refresh") }
        vault.prepare("phone", AuthStartRequest(1, BridgeProtocol.newRequestId(), transfer.profile.hostId, now))
        assertFalse(vault.isCurrent(lease))
        try { vault.credential { error("Must not refresh during reauth") }; fail("Expected pause") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZING, failure.problem) }
    }
    @Test fun duplicatePhoneTransferCannotRollBackRotatedTokens() = runBlocking {
        val record = Record(); val (vault, transfer) = seed(record, 120)
        now += 61_000
        vault.credential { renewed(it) }
        vault.accept("phone", ProfileProtocol.encode(transfer))
        assertEquals("new-refresh", persisted(record).refreshToken)
    }
}
