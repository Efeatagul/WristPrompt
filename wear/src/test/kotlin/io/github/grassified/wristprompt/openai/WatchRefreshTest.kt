package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.SecretRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchRefreshTest {
    private class Record : SecretRecord {
        var data: ByteArray? = null
        var failWrite = false
        var failAfterWrite = false
        val history = mutableListOf<ByteArray>()
        override fun read() = data?.clone()
        override fun write(plaintext: ByteArray) { if (failWrite) error("Synthetic storage failure"); data = plaintext.clone(); history.add(plaintext.clone()); if (failAfterWrite) error("Synthetic write uncertainty") }
    }
    private var now = 1_000_000L
    private suspend fun seed(record: Record, seconds: Long = 3600, dispatcher: CoroutineDispatcher = Dispatchers.IO): Pair<WatchProfiles, ProfileTransfer> {
        val vault = WatchProfiles(record, dispatcher) { now }; val p = testProfile(now).copy(expiresIn = seconds)
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
    @Test fun definitelyUnsentNetworkFailurePreservesSessionAndRetriesOnlyAfterCooldown() = runBlocking {
        val record = Record(); val (vault, original) = seed(record); now = 4_550_000
        try { vault.credential { throw ApiFailure(ApiProblem.NETWORK, requestNotSent = true) }; fail("Expected network failure") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.NETWORK, failure.problem) }
        assertEquals(original.profile, persisted(record)); assertFalse(vault.state.value.reauthorizationRequired)
        try { vault.credential { error("Cooldown must suppress request") }; fail("Expected cooldown") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.TEMPORARY, failure.problem) }
        now += 5001
        assertEquals("new-access", vault.credential { renewed(it) }.accessToken)
    }
    @Test fun terminalCodesPreserveExactRegistrationAndNeverChangeBilling() = runBlocking {
        for (code in listOf("invalid_grant", "invalid_refresh_token", "refresh_token_expired", "refresh_token_invalidated", "refresh_token_reused", "token_expired")) {
            now = 1_000_000; val record = Record(); val (vault, original) = seed(record); now = 4_550_000
            try { vault.credential { throw ApiErrors.parse(400, "{\"error\":\"$code\"}", refresh = true) }; fail("Expected reauthorization") }
            catch (failure: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, failure.problem) }
            val saved = persisted(record)
            assertEquals(original.profile.identity, saved.identity); assertEquals(original.profile.hostId, saved.hostId)
            assertEquals(original.profile.idToken, saved.idToken); assertEquals(original.profile.scopes, saved.scopes)
            assertNull(saved.accessToken); assertNull(saved.refreshToken); assertTrue(saved.reauthorizationRequired)
            assertTrue(vault.state.value.connected); assertFalse(vault.state.value.planEnabled)
            try { vault.credential { error("Never retry terminal grant") }; fail("Expected reauthorization") } catch (_: ApiFailure) { }
        }
    }
    @Test fun invalidClientIsConfigurationFailureWithoutTokenDeletion() = runBlocking {
        val record = Record(); val (vault, original) = seed(record); now = 4_550_000
        try { vault.credential { throw ApiErrors.parse(401, """{"error":"invalid_client"}""", refresh = true) }; fail("Expected configuration error") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.CONFIGURATION, failure.problem) }
        assertEquals(original.profile, persisted(record)); assertTrue(vault.state.value.connected)
        assertFalse(vault.state.value.reauthorizationRequired)
    }
    @Test fun malformedSuccessAndWrongAssociationNeverReplayOldGrant() = runBlocking {
        for (wrongIdentity in listOf(false, true)) {
            now = 1_000_000; val record = Record(); val (vault, original) = seed(record); now = 4_550_000
            try {
                vault.credential { old -> if (wrongIdentity) renewed(old).copy(identity = old.identity.copy(clientId = "other_client"))
                    else RefreshProtocol.update(old, "not-json", now) }
                fail("Expected uncertain rotation")
            } catch (failure: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, failure.problem) }
            assertEquals(original.profile, persisted(record)); assertTrue(vault.state.value.reauthorizationRequired)
            assertTrue(vault.state.value.connected)
            try { vault.credential { error("Never retry uncertain grant") }; fail("Expected reauthorization") } catch (_: ApiFailure) { }
        }
    }
    @Test fun timeoutIsDeterministicAndPreservesDurableUncertainty() = runTest {
        val record = Record(); val (vault, original) = seed(record, dispatcher = StandardTestDispatcher(testScheduler)); now = 4_550_000
        val started = CompletableDeferred<Unit>(); val never = CompletableDeferred<CredentialProfile>()
        val caller = async { try { vault.credential { started.complete(Unit); never.await() }; null } catch (f: ApiFailure) { f.problem } }
        runCurrent(); assertTrue(started.isCompleted); assertTrue(vault.state.value.refreshing)
        assertFalse(vault.state.value.reauthorizationRequired)
        advanceTimeBy(35_001); runCurrent()
        assertEquals(ApiProblem.REAUTHORIZE, caller.await()); assertEquals(original.profile, persisted(record))
        assertTrue(vault.state.value.reauthorizationRequired); assertFalse(vault.state.value.refreshing)
    }
    @Test fun waitingCallersReceiveOneDurableRotationWithVirtualScheduling() = runTest {
        val record = Record(); val (vault, _) = seed(record, dispatcher = StandardTestDispatcher(testScheduler)); now = 4_550_000
        val release = CompletableDeferred<Unit>(); var calls = 0
        val callers = (1..8).map { async { vault.credential { calls++; release.await(); renewed(it) } } }
        runCurrent(); assertEquals(1, calls)
        release.complete(Unit); runCurrent()
        val leases = callers.awaitAll()
        assertEquals(1, calls); assertTrue(leases.all { it.accessToken == "new-access" })
        assertTrue(vault.state.value.sessionRenewed)
        assertEquals("new-refresh", persisted(record).refreshToken)
    }
    @Test fun anotherRegistrationCanRefreshWhileFirstIsWaitingWithoutLostVaultUpdates() = runTest {
        val record = Record(); val dispatcher = StandardTestDispatcher(testScheduler)
        val (vault, first) = seed(record, dispatcher = dispatcher)
        vault.select(null)
        val second = first.profile.copy(identity = first.profile.identity.copy(clientId = "second_client", subject = "second_subject"))
        val req = AuthStartRequest(1, BridgeProtocol.newRequestId(), second.hostId, now)
        vault.prepare("phone", req)
        vault.accept("phone", ProfileProtocol.encode(ProfileTransfer(1, req.requestId, BridgeProtocol.newRequestId(), second)))
        vault.select(first.profile.identity.clientId); now = 4_550_000
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val firstCaller = async { try { vault.credential { started.complete(Unit); release.await(); renewed(it) }; null } catch (f: ApiFailure) { f.problem } }
        runCurrent(); assertTrue(started.isCompleted)
        vault.select(second.identity.clientId)
        assertEquals("second-access", vault.credential { it.copy(accessToken = "second-access", refreshToken = "second-refresh", savedAtEpochMs = now) }.accessToken)
        release.complete(Unit); runCurrent(); assertEquals(ApiProblem.STALE_ACCOUNT, firstCaller.await())
        val profiles = Json.parseToJsonElement(record.data!!.decodeToString()).jsonObject.getValue("profiles").jsonArray.map { Json.decodeFromJsonElement<CredentialProfile>(it) }
        assertEquals("new-refresh", profiles.single { it.identity == first.profile.identity }.refreshToken)
        assertEquals("second-refresh", profiles.single { it.identity == second.identity }.refreshToken)
        assertEquals(second.identity.clientId, vault.state.value.activeClientId)
    }
    @Test fun cancelledTransportAndUncertainCompletedWriteStayAtomic() = runBlocking {
        val record = Record(); val (vault, _) = seed(record); now = 4_550_000
        try { vault.credential { throw CancellationException("Synthetic cancellation") }; fail("Expected uncertainty") }
        catch (failure: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, failure.problem) }
        assertTrue(WatchProfiles(record) { now }.run { load(); state.value.reauthorizationRequired })
        now = 1_000_000; val other = Record(); val (next, _) = seed(other); now = 4_550_000
        try { next.credential { other.failAfterWrite = true; renewed(it) }; fail("Expected storage uncertainty") } catch (_: Exception) { }
        other.failAfterWrite = false
        val saved = persisted(other)
        assertEquals("new-access", saved.accessToken); assertEquals("new-refresh", saved.refreshToken)
        assertEquals(now, saved.savedAtEpochMs)
        assertEquals("new-access", WatchProfiles(other) { now }.credential { error("Atomic new tuple must be reused") }.accessToken)
    }

    @Test fun everyPersistedCredentialTupleIsEntirelyOldOrEntirelyNew() = runBlocking {
        val record = Record(); val (vault, original) = seed(record); now = 4_550_000
        vault.credential { renewed(it) }
        val tuples = record.history.flatMap { bytes ->
            Json.parseToJsonElement(bytes.decodeToString()).jsonObject["profiles"]?.jsonArray.orEmpty()
                .map { Json.decodeFromJsonElement<CredentialProfile>(it) }
        }
        assertTrue(tuples.isNotEmpty())
        assertTrue(tuples.all { it == original.profile || it == renewed(original.profile) })
    }
    @Test fun unavailableServiceRetainsWholeSessionAndHasBoundedManualRetry() = runBlocking {
        for (status in listOf(429, 500, 503)) {
            now = 1_000_000; val record = Record(); val (vault, original) = seed(record); now = 4_550_000
            try { vault.credential { throw ApiErrors.parse(status, "{}", refresh = true) }; fail("Expected temporary failure") }
            catch (failure: ApiFailure) { assertEquals(ApiProblem.TEMPORARY, failure.problem) }
            assertEquals(original.profile, persisted(record)); assertTrue(vault.state.value.connected)
            assertFalse(vault.state.value.reauthorizationRequired)
            try { vault.credential { error("No retry before cooldown") }; fail("Expected cooldown") } catch (_: ApiFailure) { }
        }
    }
    @Test fun earliestUnknownConstraintStopsBeforeIntentWithoutDiscardingCredentials() = runBlocking {
        val record = Record(); val vault = WatchProfiles(record) { now }
        val p = testProfile(now).copy(earliestRefreshAt = JsonObject(emptyMap()))
        val req = AuthStartRequest(1, BridgeProtocol.newRequestId(), p.hostId, now)
        vault.prepare("phone", req)
        vault.accept("phone", ProfileProtocol.encode(ProfileTransfer(1, req.requestId, BridgeProtocol.newRequestId(), p)))
        now = 4_600_000
        try { vault.credential { error("Undocumented constraint cannot authorize refresh") }; fail("Expected configuration failure") }
        catch (failure: ApiFailure) { assertEquals(ProtocolDetail.REFRESH_TIMING_UNDOCUMENTED, failure.protocolDetail) }
        assertEquals(p, persisted(record)); assertFalse(vault.state.value.reauthorizationRequired)
    }

}
