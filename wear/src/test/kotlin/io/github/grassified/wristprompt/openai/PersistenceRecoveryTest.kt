package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.*
import javax.crypto.KeyGenerator
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** D4-E: synthetic durable faults, real repositories and fresh owners. No Android Keystore claim. */
class PersistenceRecoveryTest : ReconstructionFixture() {
    private class Record : SecretRecord {
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        private val aad = "synthetic-d4-e".encodeToByteArray()
        var encrypted: ByteArray? = null
        var failBefore = false
        var failAfter = false
        var writes = 0
        override fun read() = encrypted?.let { GcmRecord.decrypt(it, key, aad) }
        override fun write(plaintext: ByteArray) {
            if (failBefore) throw SecureStorageException()
            encrypted = GcmRecord.encrypt(plaintext, key, aad); writes++
            if (failAfter) throw SecureStorageException()
        }
        fun json() = Json.parseToJsonElement(read()!!.decodeToString()).jsonObject
        fun snapshot() = Record().also { copy -> copy.write(read()!!) }
    }
    private suspend fun plan(record: Record, seconds: Long = 3600) = WatchProfiles(record, main) { time }.also {
        addPlan(it, seconds = seconds)
        it.selectModel("client_one", it.state.value.generation, terra)
        it.confirmPlanNotice()
    }
    private suspend fun api(record: Record) = ApiKeyRepository(record) { time }.also {
        val r = request(); it.prepare("phone", r)
        it.accept("phone", ProfileProtocol.encode(ApiKeyTransfer(1, r.requestId, BridgeProtocol.newRequestId(), host, "synthetic-api-key")))
        it.selectMode(CredentialMode.OPENAI_API_KEY); it.selectModel(it.credential(), "api-model")
    }
    private fun storageFailure(block: suspend () -> Unit) = assertThrows(SecureStorageException::class.java) { runBlocking { block() } }
    private fun profile(record: Record) = Json.decodeFromJsonElement<CredentialProfile>(record.json().getValue("profiles").jsonArray.single())

    @Test fun emptyTruncatedTamperedAndUnknownCiphertextFailClosedInBothRepositories(): Unit = runBlocking {
        for (apiMode in listOf(false, true)) for (fault in 0..4) {
            val record = Record()
            if (apiMode) api(record) else plan(record)
            val original = record.encrypted!!
            record.encrypted = when (fault) {
                0 -> byteArrayOf()
                1 -> original.copyOf(12)
                2 -> original.copyOf(original.size - 1)
                3 -> original.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                else -> original.clone().also { it[0] = 2 }
            }
            val writes = record.writes
            if (apiMode) {
                val restored = ApiKeyRepository(record); restored.load()
                assertTrue(restored.state.value.storageError); assertFalse(restored.state.value.configured)
                storageFailure { restored.credential() }
            } else {
                val restored = WatchProfiles(record, main) { time }; restored.load()
                assertTrue(restored.state.value.storageError); assertFalse(restored.state.value.connected)
                storageFailure { restored.credential { error("No refresh") } }
            }
            assertEquals(writes, record.writes)
        }
    }
    @Test fun authenticatedButMalformedPayloadIsNotFirstRun(): Unit = runBlocking {
        for (body in listOf("", "{", "{\"version\":2}", "{\"selectedClientId\":\"absent\"}")) {
            val record = Record(); record.write(body.encodeToByteArray()); val before = record.encrypted!!.clone()
            val restored = WatchProfiles(record, main) { time }; restored.load()
            assertTrue(restored.state.value.storageError); assertFalse(restored.state.value.connected)
            storageFailure { restored.prepare("phone", request()) }
            assertArrayEquals(before, record.encrypted)
        }
        for (body in listOf("", "{", "{\"version\":2}", "{\"modeRevision\":-1}")) {
            val record = Record(); record.write(body.encodeToByteArray())
            val restored = ApiKeyRepository(record); restored.load()
            assertTrue(restored.state.value.storageError); storageFailure { restored.snapshot() }
        }
    }
    @Test fun missingRecordsAreUnconfiguredFirstRunWithoutInventedCredentials(): Unit = runBlocking {
        val record = Record(); val p = WatchProfiles(record, main) { time }; val k = ApiKeyRepository(Record())
        p.load(); k.load()
        assertFalse(p.state.value.storageError); assertFalse(p.state.value.connected)
        assertTrue(k.state.value.loaded); assertFalse(k.state.value.configured)
        assertEquals(CredentialMode.CHATGPT_PLAN, k.state.value.mode); assertEquals(0, record.writes)
        assertThrows(ApiFailure::class.java) { runBlocking { p.credential { error("No refresh") } } }
    }
    @Test fun damagedSelectedRecordCannotUseOtherAvailableCredential(): Unit = runBlocking {
        for (apiMode in listOf(false, true)) {
            val oauth = Disk("oauth-$apiMode"); val keys = Disk("keys-$apiMode"); seed(oauth, keys)
            val k = ApiKeyRepository(keys.record()) { time }; val r = request(); k.prepare("phone", r)
            k.accept("phone", ProfileProtocol.encode(ApiKeyTransfer(1, r.requestId, BridgeProtocol.newRequestId(), host, "synthetic-api-key")))
            if (apiMode) { k.selectMode(CredentialMode.OPENAI_API_KEY); keys.corrupt() } else oauth.corrupt()
            val runtime = runtime(oauth, keys); runtime.start()
            withTimeout(5000) { runtime.router.state.first { !it.usable && (it.problem == ApiProblem.STORAGE || apiMode && !runtime.k.state.value.loaded) } }
            storageFailure { runtime.router.current() }
            assertEquals(0, runtime.catalog.calls.get()); assertEquals(0, runtime.catalog.generations.get())
        }
    }
    @Test fun failedModeModelAndKeyWritesRestoreOnlyPriorDurableTuple(): Unit = runBlocking {
        val record = Record(); val k = api(record); val old = k.credential(); val before = record.encrypted!!.clone()
        record.failBefore = true
        storageFailure { k.selectMode(CredentialMode.CHATGPT_PLAN) }
        storageFailure { k.selectModel(old, "unsaved-api-model") }
        storageFailure { k.remove() }
        assertArrayEquals(before, record.encrypted)
        record.failBefore = false
        val restored = ApiKeyRepository(record) { time }; restored.load()
        assertEquals(CredentialMode.OPENAI_API_KEY, restored.state.value.mode)
        assertEquals(old.modeRevision, restored.state.value.modeRevision)
        assertEquals(old.generation, restored.state.value.keyRevision)
        assertEquals(old.accessToken, restored.credential().accessToken)
        assertEquals("api-model", restored.selectedModel())
    }
    @Test fun failedPlanPreferenceWriteShowsStorageFailureAndRestartUsesDurableSelection(): Unit = runBlocking {
        val record = Record(); val p = plan(record); val k = ApiKeyRepository(Record()); k.load()
        val scope = CoroutineScope(SupervisorJob() + main)
        val router = CredentialRouter(p, k, { error("No refresh") }, scope)
        val catalog = Catalog(); val vm = InferenceViewModel(router.state, InferenceSession(router, catalog.transport, router::isCurrent), router::selectedModel, router::selectModel)
        try {
            withTimeout(5000) { vm.state.first { it.stage == ProofStage.READY } }
            record.failBefore = true
            withContext(main) { vm.select(sol) }
            withTimeout(5000) { vm.state.first { it.problem == ApiProblem.STORAGE } }
            assertNotEquals(sol, vm.state.value.selectedSlug)
            record.failBefore = false
            val restored = WatchProfiles(record, main) { time }; restored.load()
            assertEquals(terra, restored.selectedModel("client_one")); assertFalse(restored.state.value.planWelcome)
        } finally {
            withContext(main) { vm.viewModelScope.cancel() }; scope.cancel()
        }
    }
    @Test fun durableIntentSnapshotBeforeDispatchRequiresReauthOnFreshOwner(): Unit = runBlocking {
        val record = Record(); val p = plan(record, 120); time += 61_000
        p.credential { old ->
            assertTrue("client_one" in record.json().getValue("refreshPending").jsonArray.map { it.jsonPrimitive.content })
            val snapshot = record.snapshot(); val restored = WatchProfiles(snapshot, main) { time }; restored.load()
            assertTrue(restored.state.value.reauthorizationRequired)
            try { restored.credential { error("Never dispatch unknown grant") }; fail("Expected reauthorization") }
            catch (failure: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, failure.problem) }
            old.copy(accessToken = "replacement-access", refreshToken = "replacement-refresh", savedAtEpochMs = time)
        }
    }
    @Test fun failedIntentAcknowledgementNeverDispatchesAndRestartIsConservative(): Unit = runBlocking {
        for (committed in listOf(false, true)) {
            val record = Record(); val p = plan(record, 120); time += 61_000
            record.failBefore = !committed; record.failAfter = committed
            storageFailure { p.credential { error("Intent not acknowledged: no dispatch") } }
            record.failBefore = false; record.failAfter = false
            val restored = WatchProfiles(record, main) { time }; restored.load()
            assertEquals(committed, restored.state.value.reauthorizationRequired)
            assertEquals("synthetic-refresh", profile(record).refreshToken)
            assertEquals(terra, restored.selectedModel("client_one"))
        }
    }
    @Test fun replacementFailureReconstructsEitherInertOldTupleOrCompleteNewTuple(): Unit = runBlocking {
        for (committed in listOf(false, true)) {
            val record = Record(); val p = plan(record, 120); val identity = profile(record).identity; time += 61_000
            storageFailure { p.credential { old ->
                record.failBefore = !committed; record.failAfter = committed
                old.copy(accessToken = "replacement-access", refreshToken = "replacement-refresh", expiresIn = 3600, savedAtEpochMs = time)
            } }
            record.failBefore = false; record.failAfter = false
            val restored = WatchProfiles(record, main) { time }; restored.load()
            assertEquals(identity, profile(record).identity); assertEquals(host, profile(record).hostId)
            assertEquals(terra, restored.selectedModel("client_one")); assertFalse(restored.state.value.planWelcome)
            assertEquals(!committed, restored.state.value.reauthorizationRequired)
            if (committed) {
                assertEquals("replacement-access", restored.credential { error("Already committed") }.accessToken)
                assertFalse(record.json().getValue("refreshPending").jsonArray.any())
            } else assertThrows(ApiFailure::class.java) { runBlocking { restored.credential { error("Never reuse consumed token") } } }
        }
    }
    @Test fun signOutCleanupUncertaintyNeverResurrectsTokensAndRetainsIdentityPreference(): Unit = runBlocking {
        for (committed in listOf(false, true)) {
            val record = Record(); val p = plan(record); val identity = profile(record).identity
            storageFailure { p.signOut {
                record.failBefore = !committed; record.failAfter = committed; RevocationResult.CONFIRMED
            } }
            record.failBefore = false; record.failAfter = false
            val restored = WatchProfiles(record, main) { time }; restored.load()
            assertTrue(restored.state.value.signedOut); assertFalse(restored.state.value.connected)
            assertEquals(if (committed) RevocationResult.CONFIRMED else RevocationResult.UNCONFIRMED, restored.state.value.revocation)
            assertEquals(identity, profile(record).identity); assertEquals(host, profile(record).hostId)
            assertNull(profile(record).accessToken); assertNull(profile(record).refreshToken); assertEquals("", profile(record).idToken)
            assertEquals(terra, restored.selectedModel("client_one"))
        }
    }
    @Test fun dormantModelSurvivesRestartAccountSwitchAndReappearsWithoutWriteback(): Unit = runBlocking {
        val oauth = Disk("oauth"); val keys = Disk("api"); seed(oauth, keys)
        val first = runtime(oauth, keys, Catalog().apply { order = listOf(sol) }); first.start(); first.ready(sol)
        assertEquals(terra, first.p.selectedModel("client_one")); first.close()
        val second = runtime(oauth, keys, Catalog().apply { order = listOf(sol) }); second.start(); second.ready(sol)
        second.p.select(null); addPlan(second.p, "client_two"); second.ready(sol)
        second.p.selectModel("client_two", second.p.state.value.generation, sol)
        second.p.select("client_one"); second.ready(sol)
        val writes = oauth.writes.get(); second.catalog.order = listOf(sol, terra)
        withContext(main) { second.vm.loadModels() }; second.ready(terra)
        assertEquals(writes, oauth.writes.get()); assertEquals(sol, second.p.selectedModel("client_two"))
        second.close(); val third = runtime(oauth, keys); third.start(); third.ready(terra)
    }
    @Test fun reconstructedReceiptsAcknowledgeExactRetryAndRejectWrongExpiredOrChangedDelivery(): Unit = runBlocking {
        for (apiMode in listOf(false, true)) {
            val record = Record(); val r = request(); val transferId = BridgeProtocol.newRequestId()
            val p = WatchProfiles(record, main) { time }; val k = ApiKeyRepository(record) { time }
            val bytes = if (apiMode) {
                k.prepare("phone", r)
                ProfileProtocol.encode(ApiKeyTransfer(1, r.requestId, transferId, host, "synthetic-api-key"))
            } else {
                p.prepare("phone", r)
                ProfileProtocol.encode(ProfileTransfer(1, r.requestId, transferId, CredentialProfile(
                    RegistrationIdentity("client_one", OpenAiAuth.ISSUER, "subject"), host, "Synthetic", null, false,
                    "synthetic-id", "synthetic-access", "synthetic-refresh", "Bearer", 3600, time,
                    OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES))))
            }
            if (apiMode) k.accept("phone", bytes) else p.accept("phone", bytes)
            val restoredP = WatchProfiles(record, main) { time }; val restoredK = ApiKeyRepository(record) { time }
            suspend fun deliver(node: String, payload: ByteArray) = if (apiMode) restoredK.accept(node, payload) else restoredP.accept(node, payload)
            val writes = record.writes
            assertEquals(ProfileAck(1, r.requestId, transferId), deliver("phone", bytes))
            assertEquals(writes, record.writes)
            try { deliver("other-phone", bytes); fail("Wrong node accepted") } catch (_: Exception) { }
            val changed = if (apiMode) ProfileProtocol.encode(ApiKeyProtocol.transfer(bytes).copy(hostId = BridgeProtocol.newHostId()))
                else ProfileProtocol.encode(ProfileProtocol.decodeTransfer(bytes).let { it.copy(profile = it.profile.copy(hostId = BridgeProtocol.newHostId())) })
            try { deliver("phone", changed); fail("Changed body accepted") } catch (_: Exception) { }
            time += OpenAiAuth.ATTEMPT_MS + 1
            try { deliver("phone", bytes); fail("Expired receipt accepted") } catch (_: Exception) { }
            assertEquals(writes, record.writes)
        }
    }
    @Test fun billingSwitchesAndIndependentReconstructionPreserveBothModelPreferencesAndRejectOldLease(): Unit = runBlocking {
        val oauth = Record(); val keys = Record(); val p = plan(oauth); val k = api(keys)
        val apiLease = k.credential(); val planGeneration = p.state.value.generation
        k.selectMode(CredentialMode.CHATGPT_PLAN); k.selectMode(CredentialMode.OPENAI_API_KEY)
        assertFalse(k.current(apiLease)); assertEquals(terra, p.selectedModel("client_one"))
        val restoredP = WatchProfiles(oauth, main) { time }; val restoredK = ApiKeyRepository(keys) { time }
        restoredP.load(); restoredK.load()
        assertEquals(planGeneration, restoredP.state.value.generation)
        assertEquals(CredentialMode.OPENAI_API_KEY, restoredK.state.value.mode)
        assertEquals(apiLease.modeRevision + 2, restoredK.state.value.modeRevision)
        assertEquals(apiLease.generation, restoredK.state.value.keyRevision)
        assertEquals("api-model", restoredK.selectedModel()); assertEquals(terra, restoredP.selectedModel("client_one"))
        restoredK.selectMode(CredentialMode.CHATGPT_PLAN)
        assertEquals("synthetic-access", restoredP.credential { error("No refresh") }.accessToken)
        assertEquals("api-model", restoredK.selectedModel())
    }

}
