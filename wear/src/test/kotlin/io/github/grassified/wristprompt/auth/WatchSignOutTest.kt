package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.openai.*
import io.github.grassified.wristprompt.storage.SecretRecord
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchSignOutTest {
    private class Record : SecretRecord {
        var bytes: ByteArray? = null
        var failWrite = false
        override fun read() = bytes?.clone()
        override fun write(plaintext: ByteArray) { if (failWrite) error("Synthetic storage failure"); bytes = plaintext.clone() }
    }
    private var now = 1_000_000L
    private val host = BridgeProtocol.newHostId()
    private fun profile(client: String = "issued_client", seconds: Long = 3600) = CredentialProfile(
        RegistrationIdentity(client, OpenAiAuth.ISSUER, "subject_$client"), host, "Test", "same@example.test", true,
        "synthetic-id", "synthetic-access", "synthetic-refresh", "Bearer", seconds, now, OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES))
    private suspend fun seed(record: Record, dispatcher: CoroutineDispatcher = Dispatchers.IO, p: CredentialProfile = profile()): WatchProfiles {
        val vault = WatchProfiles(record, dispatcher) { now }
        val req = AuthStartRequest(1, BridgeProtocol.newRequestId(), host, now)
        vault.prepare("phone", req)
        vault.accept("phone", ProfileProtocol.encode(ProfileTransfer(1, req.requestId, BridgeProtocol.newRequestId(), p)))
        return vault
    }
    private fun saved(record: Record) = Json.parseToJsonElement(record.bytes!!.decodeToString()).jsonObject.getValue("profiles").jsonArray
        .map { Json.decodeFromJsonElement<CredentialProfile>(it) }
    private fun assertCleared(record: Record, expected: CredentialProfile) {
        val p = saved(record).single { it.identity == expected.identity }
        assertEquals(expected.hostId, p.hostId); assertEquals(expected.name, p.name); assertEquals(expected.email, p.email)
        assertNull(p.accessToken); assertNull(p.refreshToken); assertTrue(p.idToken.isEmpty())
        assertNull(p.expiresIn); assertNull(p.tokenType); assertTrue(p.reauthorizationRequired); assertFalse(p.planUsageEnabled)
        p.validate()
    }

    @Test fun confirmedSignOutRetainsRegistrationHostAndReauthorizationWithoutIdHint() = runBlocking {
        val record = Record(); val original = profile(); val vault = seed(record, p = original)
        val lease = vault.credential { error("Fresh") }
        assertEquals(RevocationResult.CONFIRMED, vault.signOut { credential ->
            assertEquals(original.identity.clientId, credential.clientId); assertEquals(original.refreshToken, credential.refreshToken)
            RevocationResult.CONFIRMED
        })
        assertCleared(record, original); assertFalse(vault.isCurrent(lease))
        assertFalse(vault.state.value.connected); assertFalse(vault.state.value.planEnabled); assertTrue(vault.state.value.signedOut)
        val restored = WatchProfiles(record) { now }; restored.load()
        assertTrue(restored.state.value.signedOut); assertEquals(RevocationResult.CONFIRMED, restored.state.value.revocation)
        val req = AuthStartRequest(1, BridgeProtocol.newRequestId(), host, now)
        restored.prepare("phone", req)
        val context = restored.context("phone", ContextRequest(1, req.requestId, host))
        assertEquals(original.identity, context.identity); assertNull(context.idTokenHint)
        restored.accept("phone", ProfileProtocol.encode(ProfileTransfer(1, req.requestId, BridgeProtocol.newRequestId(), original)))
        assertTrue(restored.state.value.connected); assertFalse(restored.state.value.signedOut)
        assertEquals(original.accessToken, restored.credential { error("New authorization is fresh") }.accessToken)
        assertEquals(1, saved(record).size)
    }
    @Test fun localOnlyAndRepeatedSignOutNeverRecreateSecretsOrInventConfirmation() = runBlocking {
        val record = Record(); val original = profile(); val vault = seed(record); var attempts = 0
        val revoke: suspend (RevocationCredential) -> RevocationResult = { attempts++; RevocationResult.UNCONFIRMED }
        assertEquals(RevocationResult.UNCONFIRMED, vault.signOut(revoke = revoke))
        assertEquals(RevocationResult.UNCONFIRMED, vault.signOut { error("No token remains to revoke") })
        assertEquals(1, attempts); assertCleared(record, original)
        try { vault.credential { error("Never renew after sign-out") }; fail("Expected unusable session") } catch (_: ApiFailure) { }
    }
    @Test fun noRefreshTokenOrNoAccountCannotFalselyConfirmRemoteSuccess() = runBlocking {
        val empty = WatchProfiles(Record()) { now }
        assertEquals(RevocationResult.UNCONFIRMED, empty.signOut { error("No account") })
        val record = Record(); val p = profile().copy(accessToken = null, refreshToken = null, tokenType = null, expiresIn = null, scopes = setOf("openid"))
        val vault = seed(record, p = p)
        assertEquals(RevocationResult.UNCONFIRMED, vault.signOut { error("No renewable token") })
        assertCleared(record, p)
    }
    @Test fun remoteTimeoutIsBoundedWithVirtualTimeAndLocalCleanupCompletes() = runTest {
        val record = Record(); val original = profile(); val vault = seed(record, StandardTestDispatcher(testScheduler))
        val started = CompletableDeferred<Unit>()
        val result = async { vault.signOut { started.complete(Unit); awaitCancellation() } }
        runCurrent(); assertTrue(started.isCompleted); assertTrue(vault.state.value.signingOut)
        assertFalse(vault.state.value.connected)
        advanceTimeBy(15_001); runCurrent()
        assertEquals(RevocationResult.UNCONFIRMED, result.await()); assertCleared(record, original)
    }
    @Test fun refreshRaceRevokesLatestRotatedTokenWithoutDeadlockOrResurrection() = runTest {
        val record = Record(); val original = profile(seconds = 120); val vault = seed(record, StandardTestDispatcher(testScheduler), original)
        now += 61_000
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val refresh = async { try { vault.credential { old -> started.complete(Unit); release.await(); old.copy(
            accessToken = "rotated-access", refreshToken = "rotated-refresh", savedAtEpochMs = now) }; null } catch (f: ApiFailure) { f.problem } }
        runCurrent(); assertTrue(started.isCompleted)
        var revoked: String? = null
        val signOut = async { vault.signOut { revoked = it.refreshToken; RevocationResult.CONFIRMED } }
        runCurrent(); assertTrue(vault.state.value.signingOut); assertNull(revoked)
        val blocked = async { try { vault.credential { error("Never start another refresh") }; false } catch (_: ApiFailure) { true } }
        release.complete(Unit); runCurrent()
        assertEquals(ApiProblem.STALE_ACCOUNT, refresh.await()); assertTrue(blocked.await())
        assertEquals(RevocationResult.CONFIRMED, signOut.await()); assertEquals("rotated-refresh", revoked)
        assertCleared(record, original)
    }
    @Test fun uncertainRotationCannotTurnOldToken200IntoSessionRevocationClaim() = runBlocking {
        val record = Record(); val original = profile(seconds = 120); val vault = seed(record, p = original); now += 61_000
        try { vault.credential { throw ApiFailure(ApiProblem.NETWORK) }; fail("Expected uncertainty") } catch (_: ApiFailure) { }
        assertEquals(RevocationResult.UNCONFIRMED, vault.signOut { RevocationResult.CONFIRMED })
        assertCleared(record, original)
    }
    @Test fun reconstructionDuringSignOutNeverPublishesUsableAccount() = runTest {
        val record = Record(); val original = profile(); val vault = seed(record, StandardTestDispatcher(testScheduler))
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val signOut = async { vault.signOut { started.complete(Unit); release.await(); RevocationResult.CONFIRMED } }
        runCurrent(); assertTrue(started.isCompleted)
        vault.load(); assertFalse(vault.state.value.connected); assertTrue(vault.state.value.signingOut)
        // A new process has no live revocation job; resume local clearing, without replaying network work.
        val copied = Record().apply { bytes = record.bytes!!.clone() }
        val restored = WatchProfiles(copied, StandardTestDispatcher(testScheduler)) { now }; restored.load()
        assertCleared(copied, original); assertTrue(restored.state.value.signedOut)
        assertEquals(RevocationResult.UNCONFIRMED, restored.state.value.revocation)
        release.complete(Unit); runCurrent(); signOut.await()
    }
    @Test fun storageFailureCannotReportLocalSuccessAndNewLeasesRemainBlocked() = runBlocking {
        val record = Record(); val original = profile(); val vault = seed(record)
        val oldLease = vault.credential { error("Fresh") }
        record.failWrite = true
        try { vault.signOut { error("Intent not durable; do not send token") }; fail("Expected storage failure") } catch (_: Exception) { }
        assertTrue(vault.state.value.storageError); assertFalse(vault.isCurrent(oldLease))
        try { vault.credential { error("Blocked after attempted sign-out") }; fail("Expected blocked credentials") } catch (_: ApiFailure) { }
        record.failWrite = false; vault.load()
        assertCleared(record, original); assertEquals(RevocationResult.UNCONFIRMED, vault.state.value.revocation)
    }
    @Test fun finalStorageFailureRetainsIntentAndRetriesCleanupOnRecreation() = runBlocking {
        val record = Record(); val original = profile(); val vault = seed(record)
        try { vault.signOut { record.failWrite = true; RevocationResult.CONFIRMED }; fail("Expected cleanup error") } catch (_: Exception) { }
        assertTrue(vault.state.value.storageError)
        record.failWrite = false
        val restored = WatchProfiles(record) { now }; restored.load()
        assertCleared(record, original); assertEquals(RevocationResult.UNCONFIRMED, restored.state.value.revocation)
    }
    @Test fun lateTransferAfterSignOutIsRejectedAndOtherRegistrationRemainsUntouched() = runBlocking {
        val record = Record(); val first = profile(); val vault = seed(record)
        vault.select(null)
        val second = profile("second_client"); val req = AuthStartRequest(1, BridgeProtocol.newRequestId(), host, now)
        vault.prepare("phone", req)
        val envelope = ProfileTransfer(1, req.requestId, BridgeProtocol.newRequestId(), second)
        vault.accept("phone", ProfileProtocol.encode(envelope))
        vault.signOut { RevocationResult.CONFIRMED }
        assertThrows(Exception::class.java) { runBlocking { vault.accept("phone", ProfileProtocol.encode(envelope)) } }
        assertEquals(first, saved(record).single { it.identity == first.identity }); assertCleared(record, second)
        assertEquals(second.identity.clientId, vault.state.value.activeClientId) // No implicit account/billing switch.
    }
    @Test fun signOutCancelsActiveDiscoveryAndStopsNewModelRequests() = runTest {
        val record = Record(); val vault = seed(record, StandardTestDispatcher(testScheduler))
        val started = CompletableDeferred<Unit>(); var calls = 0
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease): List<AiModel> { calls++; started.complete(Unit); awaitCancellation() }
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) = Unit
        }
        val session = InferenceSession({ vault.credential { error("Fresh token") } }, provider, vault::isCurrent)
        val discovery = async { session.catalog() }
        runCurrent(); assertTrue(started.isCompleted)
        vault.signOut(session::cancel) { RevocationResult.CONFIRMED }
        runCurrent(); assertTrue(discovery.isCancelled); discovery.join()
        try { session.catalog(); fail("No discovery after sign-out") } catch (_: ApiFailure) { }
        assertEquals(1, calls)
    }
    @Test fun signOutDuringInferenceRejectsEvenNoncancellableLateCompletion() = runTest {
        val record = Record(); val vault = seed(record, StandardTestDispatcher(testScheduler))
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var events = 0
        val provider = object : AiProvider {
            override suspend fun models(credential: CredentialLease) = listOf(AiModel("model", "Model"))
            override suspend fun stream(credential: CredentialLease, model: String, onEvent: suspend (ResponseEvent) -> Unit) {
                started.complete(Unit)
                withContext(NonCancellable) { release.await(); onEvent(ResponseEvent.Completed(ResponsesProtocol.EXPECTED_TEXT)) }
            }
        }
        val session = InferenceSession({ vault.credential { error("Fresh token") } }, provider, vault::isCurrent)
        val catalog = session.catalog()
        val inference = async { try { session.test(catalog, "model", {}, { events++ }); false } catch (_: Exception) { true } }
        runCurrent(); assertTrue(started.isCompleted)
        vault.signOut(session::cancel) { RevocationResult.CONFIRMED }
        release.complete(Unit); runCurrent(); inference.join()
        assertEquals(0, events); assertTrue(vault.state.value.signedOut)
    }
    @Test fun concurrentRepeatedAndCancelledSignOutFinishOneCleanup() = runTest {
        val record = Record(); val original = profile(); val vault = seed(record, StandardTestDispatcher(testScheduler))
        val started = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); var revocations = 0
        val revoke: suspend (RevocationCredential) -> RevocationResult = { revocations++; started.complete(Unit); release.await(); RevocationResult.CONFIRMED }
        val first = async { vault.signOut(revoke = revoke) }
        runCurrent(); assertTrue(started.isCompleted)
        val second = async { vault.signOut(revoke = revoke) }
        first.cancel(); release.complete(Unit); runCurrent()
        first.join(); assertEquals(RevocationResult.CONFIRMED, second.await()); assertEquals(1, revocations)
        assertCleared(record, original)
    }

}
