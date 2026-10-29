package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.ui.shellPresentation
import io.github.grassified.wristprompt.storage.HostIdRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Fresh complete object graphs over the existing D3-B encrypted-disk fixture. */
class ProcessReconstructionTest : ReconstructionFixture() {
    private suspend fun send(r: Runtime, text: String = "Synthetic question") = withContext(main) { r.vm.draft(text); r.vm.send() }
    private suspend fun terminal(r: Runtime) = withTimeout(5000) { r.vm.chat.first { !it.busy && it.messages.isNotEmpty() } }
    private suspend fun empty(r: Runtime) = withContext(main) {
        assertEquals(ChatState(), r.vm.chat.value)
        assertEquals(ProofStage.READY, r.vm.state.value.stage)
        assertFalse(r.vm.state.value.busy); assertNull(r.vm.state.value.problem)
        assertEquals("", r.vm.state.value.output); assertFalse(r.vm.state.value.completedObserved)
        assertFalse(shellPresentation(r.p.state.value, r.k.state.value, r.vm.state.value, WatchAuthState.IDLE, false).showStop)
        assertEquals(0, r.catalog.generations.get()); assertTrue(r.catalog.inputs.isEmpty())
        // The account observer remains live, but there is no active operation child after discovery.
        assertEquals(1, r.vm.viewModelScope.coroutineContext[Job]!!.children.count { it.isActive })
    }
    private suspend fun api(api: Disk) {
        val k = ApiKeyRepository(api.record()) { time }; val req = request(); k.prepare("phone", req)
        k.accept("phone", ProfileProtocol.encode(ApiKeyTransfer(1, req.requestId, BridgeProtocol.newRequestId(), host, "synthetic-api-key")))
        k.selectMode(CredentialMode.OPENAI_API_KEY); k.selectModel(k.credential(), "api-model")
    }
    @Test fun idleReconstructionRestoresDurablePlanIdentityAndFreshCatalogWithoutGeneration(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val selected = WatchProfiles(oauth.record(), main) { time }; selected.load()
        selected.select(null); addPlan(selected, "client_two")
        selected.selectModel("client_two", selected.state.value.generation, terra)
        val old = runtime(oauth, api); old.start(); old.ready(terra)
        val oldLease = old.router.current(); val identity = old.p.state.value
        old.close()
        val fresh = runtime(oauth, api, Catalog().apply { order = listOf(terra, sol) })
        assertNotSame(old.p, fresh.p); assertNotSame(old.k, fresh.k); assertNotSame(old.router, fresh.router)
        assertNotSame(old.session, fresh.session); assertNotSame(old.vm, fresh.vm)
        assertTrue(fresh.vm.state.value.models.isEmpty())
        fresh.start(); fresh.ready(terra); empty(fresh)
        assertEquals(identity.activeClientId, fresh.p.state.value.activeClientId)
        assertEquals(identity.generation, fresh.p.state.value.generation)
        assertEquals(oldLease.accessToken, fresh.router.current().accessToken)
        assertNotSame(oldLease, fresh.catalog.leases.single())
        assertEquals(1, fresh.catalog.calls.get()); assertEquals(CredentialMode.CHATGPT_PLAN, fresh.router.state.value.mode)
        val root = Json.parseToJsonElement(oauth.snapshot().decodeToString()).jsonObject
        val profile = ProfileProtocol.json.decodeFromJsonElement<CredentialProfile>(root.getValue("profiles").jsonArray.first { it.jsonObject["identity"]!!.jsonObject["clientId"]!!.jsonPrimitive.content == "client_two" })
        assertEquals(host, profile.hostId); assertEquals("client_two", profile.identity.clientId)
        assertEquals(OpenAiAuth.ISSUER, profile.identity.issuer)
        assertTrue(profile.scopes.contains("chatgpt.tokens.use.direct"))
    }
    @Test fun completedReasoningAndExplicitReplayAreAbsentInFreshGraph(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val catalog = Catalog().apply { answer = { emit -> emit(ResponseEvent.Completed("Done", listOf(
            buildJsonObject { put("type", "reasoning"); put("encrypted_content", "synthetic-reasoning") },
            buildJsonObject { put("role", "assistant"); put("content", "Done") }))) } }
        val old = runtime(oauth, api, catalog); old.start(); old.ready(terra)
        send(old); terminal(old); send(old, "Follow-up"); terminal(old)
        assertTrue(catalog.inputs.last().any { it["type"]?.jsonPrimitive?.content == "reasoning" })
        old.close()
        val fresh = runtime(oauth, api); fresh.start(); fresh.ready(terra); empty(fresh)
        send(fresh, "New explicit request"); terminal(fresh)
        assertEquals(1, fresh.catalog.generations.get()); assertEquals(1, fresh.catalog.inputs.single().size)
        assertEquals("New explicit request", fresh.catalog.inputs.single().single()["content"]!!.jsonPrimitive.content)
    }
    @Test fun activePartialCancelledAndFailedExchangesNeverReconstructOrReplay(): Unit = runBlocking {
        for (stage in listOf("active", "partial", "cancelled", "failed")) {
            val oauth = Disk("oauth-$stage"); val api = Disk("api-$stage"); seed(oauth, api)
            val entered = CompletableDeferred<Unit>()
            val catalog = Catalog().apply { answer = { emit ->
                if (stage != "active") emit(ResponseEvent.Delta("Synthetic partial"))
                entered.complete(Unit)
                if (stage == "failed") throw ApiFailure(ApiProblem.NETWORK)
                awaitCancellation()
            } }
            val old = runtime(oauth, api, catalog); old.start(); old.ready(terra); send(old)
            withTimeout(5000) { entered.await() }
            if (stage == "cancelled") withContext(main) { old.vm.stop() }
            if (stage == "failed") terminal(old)
            old.close()
            val fresh = runtime(oauth, api); fresh.start(); fresh.ready(terra); empty(fresh)
            // No load/reload action is an instruction to retry an old prompt.
            withContext(main) { fresh.vm.loadModels() }; fresh.ready(terra); empty(fresh)
            send(fresh); terminal(fresh)
            assertEquals(1, fresh.catalog.inputs.single().size)
        }
    }
    @Test fun staleDeadOwnerCallbackCannotPublishIntoFreshOwner(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val finished = CompletableDeferred<Unit>()
        val old = runtime(oauth, api, Catalog().apply { answer = { emit ->
            emit(ResponseEvent.Delta("Old partial")); entered.complete(Unit)
            try { withContext(NonCancellable) { withTimeout(5000) { release.await() }; emit(ResponseEvent.Completed("Late")) } }
            finally { finished.complete(Unit) }
        } })
        old.start(); old.ready(terra); send(old); withTimeout(5000) { entered.await() }
        old.clearOwner()
        try {
            val fresh = runtime(oauth, api); fresh.start(); fresh.ready(terra); empty(fresh)
            release.complete(Unit); withTimeout(5000) { finished.await() }
            withContext(main) { assertEquals(ChatStage.CANCELLED, old.vm.chat.value.stage) }
            empty(fresh); send(fresh); terminal(fresh); assertEquals(1, fresh.catalog.inputs.single().size)
        } finally { release.complete(Unit) }
    }
    @Test fun apiBillingReconstructionNeverDispatchesDefaultPlanWhileLoadIsIncomplete(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api); api(api)
        val old = runtime(oauth, api); old.start(); old.ready("api-model")
        val durable = old.k.snapshot(); old.close()
        val fresh = runtime(oauth, api)
        fresh.p.load()
        withContext(main) { assertFalse(fresh.router.state.value.usable); assertEquals(0, fresh.catalog.calls.get()); fresh.vm.send() }
        fresh.k.load(); fresh.ready("api-model"); empty(fresh)
        assertEquals(durable, fresh.k.snapshot())
        send(fresh); terminal(fresh)
        assertTrue(fresh.catalog.leases.all { it.mode == CredentialMode.OPENAI_API_KEY && it.modeRevision == durable.modeRevision && it.generation == durable.keyRevision })
        assertEquals(1, fresh.catalog.generations.get())
    }
    @Test fun missingApiKeyNeverFallsBackToStoredPlanOnRestart(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api); api(api)
        ApiKeyRepository(api.record()) { time }.remove()
        val fresh = runtime(oauth, api); fresh.start()
        withTimeout(5000) { fresh.vm.state.first { it.problem == ApiProblem.API_KEY_MISSING } }
        withContext(main) { fresh.vm.draft("No fallback"); fresh.vm.send() }
        assertEquals(CredentialMode.OPENAI_API_KEY, fresh.router.state.value.mode)
        assertEquals(0, fresh.catalog.calls.get()); assertEquals(0, fresh.catalog.generations.get())
    }
    @Test fun reconstructedUncertainAndTerminalRefreshStatesBlockGrantReuseAndInference(): Unit = runBlocking {
        for (terminal in listOf(false, true)) {
            val oauth = Disk("oauth-$terminal"); val api = Disk("api-$terminal"); seed(oauth, api)
            time += 3_600_001
            var refreshes = 0
            val before = WatchProfiles(oauth.record(), main) { time }; before.load()
            try { before.credential { refreshes++; throw if (terminal) ApiFailure(ApiProblem.REAUTHORIZE, code = "invalid_grant") else ApiFailure(ApiProblem.NETWORK) }; fail("Expected rejection") }
            catch (_: ApiFailure) { }
            assertEquals(1, refreshes)
            val fresh = runtime(oauth, api, refresh = { refreshes++; error("Never reuse uncertain/terminal grant") })
            fresh.start(); withTimeout(5000) { fresh.vm.state.first { it.problem == ApiProblem.REAUTHORIZE } }
            assertTrue(fresh.p.state.value.reauthorizationRequired)
            try { fresh.router.current(); fail("Unsafe lease") } catch (f: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, f.problem) }
            assertEquals(1, refreshes); assertEquals(0, fresh.catalog.calls.get()); assertEquals(0, fresh.catalog.generations.get())
        }
    }
    @Test fun definitelyUnsentRefreshClearsIntentAndFreshGraphCanRenewSafely(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api); time += 3_600_001
        val prior = WatchProfiles(oauth.record(), main) { time }; prior.load()
        try { prior.credential { throw ApiFailure(ApiProblem.NETWORK, requestNotSent = true) }; fail("Expected unsent") } catch (_: ApiFailure) { }
        var refreshes = 0
        val fresh = runtime(oauth, api, refresh = { p -> refreshes++; RefreshProtocol.update(p,
            """{"access_token":"synthetic-new-access","refresh_token":"synthetic-new-refresh","token_type":"Bearer","expires_in":3600}""", time) })
        fresh.start(); fresh.ready(terra); empty(fresh)
        assertEquals(1, refreshes); assertFalse(fresh.p.state.value.reauthorizationRequired)
    }
    @Test fun interruptedSignOutRestoresOnlyClearedRegistrationAndUnconfirmedOutcome(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        // Snapshot the durable intent at the exact point before remote revocation returns.
        val before = WatchProfiles(oauth.record(), main) { time }; before.load()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val signOut = async { before.signOut { entered.complete(Unit); release.await(); RevocationResult.CONFIRMED } }
        withTimeout(5000) { entered.await() }
        val abandoned = oauth.snapshot()
        release.complete(Unit); signOut.await()
        oauth.record().write(abandoned); abandoned.fill(0)
        val fresh = runtime(oauth, api); fresh.start()
        withTimeout(5000) { fresh.vm.state.first { it.problem == ApiProblem.REAUTHORIZE } }
        assertTrue(fresh.p.state.value.signedOut); assertEquals(RevocationResult.UNCONFIRMED, fresh.p.state.value.revocation)
        assertEquals(0, fresh.catalog.calls.get()); assertEquals(0, fresh.catalog.generations.get())
        val req = request(); fresh.p.prepare("phone", req)
        val context = fresh.p.context("phone", ContextRequest(1, req.requestId, host))
        assertEquals("client_one", context.identity!!.clientId); assertNull(context.idTokenHint)
    }
    @Test fun unreadableEncryptedRecordsFailClosedWithoutRegeneratingOrFallingBack(): Unit = runBlocking {
        for (plan in listOf(false, true)) {
            val oauth = Disk("oauth-$plan"); val api = Disk("api-$plan"); seed(oauth, api)
            if (plan) oauth.corrupt() else api.corrupt()
            val fresh = runtime(oauth, api); fresh.start()
            withTimeout(5000) {
                if (plan) fresh.p.state.first { it.storageError } else fresh.k.state.first { it.storageError }
            }
            withContext(main) { assertFalse(fresh.router.state.value.usable) }
            assertEquals(0, fresh.catalog.calls.get()); assertEquals(0, fresh.catalog.generations.get())
            assertFalse(fresh.router.state.value.usable)
        }
    }
    @Test fun diskDataStoreHostAndEncryptedRegistrationSurviveIndependentGraphReconstruction(): Unit = runBlocking {
        val file = File(directory, "host.preferences_pb")
        var storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = HostIdRepository(PreferenceDataStoreFactory.create(scope = storeScope) { file }) { host }
            assertEquals(host, first.getOrCreate()); storeScope.coroutineContext[Job]!!.cancelAndJoin()
            storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val restored = HostIdRepository(PreferenceDataStoreFactory.create(scope = storeScope) { file }) { error("Must not regenerate") }
            assertEquals(host, restored.getOrCreate())
            val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
            val fresh = runtime(oauth, api); fresh.start(); fresh.ready(terra)
            val profile = ProfileProtocol.json.decodeFromJsonElement<CredentialProfile>(Json.parseToJsonElement(oauth.snapshot().decodeToString()).jsonObject.getValue("profiles").jsonArray.single())
            assertEquals(restored.getOrCreate(), profile.hostId)
            assertEquals("client_one", profile.identity.clientId)
        } finally { storeScope.coroutineContext[Job]!!.cancelAndJoin() }
    }
}
