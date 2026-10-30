package io.github.grassified.wristprompt.openai

import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.ui.shellPresentation
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

/** Real fresh encrypted-disk graph; the companion is installed beside it, never in the inference route. */
class PhoneIndependenceTest : ReconstructionFixture() {
    private class AbsentPhone : AuthBridgeTransport {
        val lookups = AtomicInteger()
        val requests = AtomicInteger()
        var allowLookup = false
        override suspend fun reachablePeers(): List<WearPeer> {
            lookups.incrementAndGet()
            check(allowLookup) { "Normal watch operation touched companion discovery" }
            return emptyList()
        }
        override suspend fun requestStart(nodeId: String, payload: ByteArray): ByteArray {
            requests.incrementAndGet(); error("Absent companion received request")
        }
        fun untouched() { assertEquals(0, lookups.get()); assertEquals(0, requests.get()) }
    }
    private suspend fun auth(r: Runtime, phone: AbsentPhone, revoke: suspend (RevocationCredential) -> RevocationResult = { RevocationResult.CONFIRMED }) =
        withContext(main) { WatchAuthViewModel(WatchAuthBridge({ host }, phone, r.p::prepare, { time }), r.p, revoke, r.session::cancel) }
    private suspend fun send(r: Runtime, prompt: String) = withContext(main) { r.vm.draft(prompt); r.vm.send() }
    private suspend fun done(r: Runtime, messages: Int) = withTimeout(5000) {
        r.vm.chat.first { !it.busy && it.messages.size == messages }
    }
    private suspend fun api(disk: Disk) {
        val keys = ApiKeyRepository(disk.record()) { time }; val req = request()
        keys.prepare("phone", req)
        keys.accept("phone", ProfileProtocol.encode(ApiKeyTransfer(1, req.requestId, BridgeProtocol.newRequestId(), host, "synthetic-api-key")))
        keys.selectMode(CredentialMode.OPENAI_API_KEY)
        keys.selectModel(keys.credential(), "api-model")
    }
    private fun http(reply: (Request) -> Pair<Int, String>) = OpenAiHttp(OkHttpClient.Builder().addInterceptor { chain ->
        val (status, body) = reply(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Synthetic")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }.build())

    @Test fun freshPlanAndApiGraphsDiscoverReloadChatFollowUpStopAndNewChatWithoutPhone(): Unit = runBlocking {
        for (apiMode in listOf(false, true)) {
            val oauth = Disk("oauth-$apiMode"); val key = Disk("api-$apiMode"); seed(oauth, key)
            if (apiMode) api(key)
            val phone = AbsentPhone(); val r = runtime(oauth, key); val auth = auth(r, phone)
            try {
                r.start(); val model = if (apiMode) "api-model" else terra; r.ready(model)
                assertTrue(r.router.state.value.usable)
                assertEquals(if (apiMode) CredentialMode.OPENAI_API_KEY else CredentialMode.CHATGPT_PLAN, r.router.current().mode)
                val shell = shellPresentation(r.p.state.value, r.k.state.value, r.vm.state.value, WatchAuthState.COMPANION_UNAVAILABLE, false)
                assertTrue(shell.proofAvailable); assertFalse(shell.status.contains("Phone"))
                withContext(main) { r.vm.loadModels() }; r.ready(model)
                assertEquals(2, r.catalog.calls.get()); assertEquals(model, r.router.selectedModel(r.router.current()))
                send(r, "Compare two arbitrary ideas"); assertEquals(ChatStage.COMPLETED, done(r, 2).stage)
                send(r, "Explain the second one"); assertEquals(ChatStage.COMPLETED, done(r, 4).stage)
                assertEquals(listOf(1, 3), r.catalog.inputs.map { it.size })
                val entered = CompletableDeferred<Unit>(); val cancelled = CompletableDeferred<Unit>()
                r.catalog.answer = { emit ->
                    try { emit(ResponseEvent.Delta("Synthetic partial")); entered.complete(Unit); awaitCancellation() }
                    finally { cancelled.complete(Unit) }
                }
                send(r, "Stop locally"); withTimeout(5000) { entered.await() }
                withContext(main) { r.vm.stop() }; withTimeout(5000) { cancelled.await() }
                assertEquals(ChatStage.CANCELLED, done(r, 5).stage)
                withContext(main) { r.vm.newChat() }; assertEquals(ChatState(), r.vm.chat.value)
                r.catalog.answer = { it(ResponseEvent.Completed("Fresh answer")) }
                send(r, "New context"); done(r, 2); assertEquals(1, r.catalog.inputs.last().size)
                phone.untouched(); assertEquals(WatchAuthState.IDLE, auth.state.value)
                r.close()
                val fresh = runtime(oauth, key); fresh.start(); fresh.ready(model)
                assertEquals(ChatState(), fresh.vm.chat.value); assertEquals(1, fresh.catalog.calls.get())
                phone.untouched()
            } finally { withContext(main) { auth.viewModelScope.cancel() } }
        }
    }

    @Test fun directRefreshCommitsReplacementAndPostRefreshChatNeverCallsCompanion(): Unit = runBlocking {
        val oauth = Disk("oauth"); val key = Disk("api"); seed(oauth, key)
        val phone = AbsentPhone(); var refreshes = 0
        val refresh = OpenAiRefresh(http { request ->
            assertEquals(OpenAiAuth.TOKEN, request.url.toString()); assertEquals("POST", request.method)
            refreshes++
            val vault = Json.parseToJsonElement(oauth.snapshot().decodeToString()).jsonObject
            assertTrue(vault.getValue("refreshPending").jsonArray.any { it.jsonPrimitive.content == "client_one" })
            200 to """{"access_token":"synthetic-renewed","refresh_token":"synthetic-rotated","token_type":"Bearer","expires_in":3600}"""
        }, { time })
        val r = runtime(oauth, key, refresh = refresh::refresh); val auth = auth(r, phone)
        try {
            r.start(); r.ready(terra); time += 3_600_001
            send(r, "Renew and answer"); assertEquals(ChatStage.COMPLETED, done(r, 2).stage)
            assertEquals(1, refreshes); assertEquals("synthetic-renewed", r.router.current().accessToken)
            assertEquals(2, r.catalog.calls.get()) // Changed bearer forces direct rediscovery before Send.
            assertEquals("synthetic-renewed", r.catalog.leases.last().accessToken)
            val vault = Json.parseToJsonElement(oauth.snapshot().decodeToString()).jsonObject
            assertTrue(vault.getValue("refreshPending").jsonArray.isEmpty())
            val profile = ProfileProtocol.json.decodeFromJsonElement<CredentialProfile>(vault.getValue("profiles").jsonArray.single())
            assertEquals("synthetic-rotated", profile.refreshToken)
            phone.untouched(); r.close()
            val fresh = runtime(oauth, key); fresh.start(); fresh.ready(terra)
            assertEquals("synthetic-renewed", fresh.router.current().accessToken); phone.untouched()
        } finally { withContext(main) { auth.viewModelScope.cancel() } }
    }

    @Test fun terminalOrUncertainRefreshRequiresExplicitPhoneRecoveryWithoutFallback(): Unit = runBlocking {
        for (uncertain in listOf(false, true)) {
            val oauth = Disk("oauth-$uncertain"); val key = Disk("api-$uncertain"); seed(oauth, key)
            api(key) // A real synthetic alternative exists, but explicit plan selection must not fall back.
            ApiKeyRepository(key.record()) { time }.selectMode(CredentialMode.CHATGPT_PLAN)
            var refreshes = 0
            val refresh = OpenAiRefresh(http {
                refreshes++
                if (uncertain) 200 to "{}" else 400 to """{"error":"invalid_grant"}"""
            }, { time })
            time += 3_600_001
            val r = runtime(oauth, key, refresh = refresh::refresh); val phone = AbsentPhone(); val auth = auth(r, phone)
            try {
                r.start(); withTimeout(5000) { r.vm.state.first { it.problem == ApiProblem.REAUTHORIZE } }
                assertTrue(r.p.state.value.reauthorizationRequired)
                send(r, "Must not dispatch"); withContext(main) { }
                assertEquals(0, r.catalog.calls.get()); assertEquals(0, r.catalog.generations.get())
                assertEquals(CredentialMode.CHATGPT_PLAN, r.k.snapshot().mode); phone.untouched()
                val before = oauth.snapshot()
                phone.allowLookup = true
                withContext(main) { auth.start() }
                withTimeout(5000) { auth.state.first { it == WatchAuthState.COMPANION_UNAVAILABLE } }
                assertEquals(1, phone.lookups.get()); assertEquals(0, phone.requests.get())
                assertArrayEquals(before, oauth.snapshot()); assertEquals(1, refreshes)
                val shell = shellPresentation(r.p.state.value, r.k.state.value, r.vm.state.value, auth.state.value, false)
                assertEquals("Sign in again to continue", shell.status); assertTrue(shell.showContinue); assertFalse(shell.proofAvailable)
                try { r.router.current(); fail("Invalid grant reused") } catch (f: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, f.problem) }
                assertEquals(1, refreshes)
            } finally { withContext(main) { auth.viewModelScope.cancel() } }
        }
    }

    @Test fun apiFailureCannotUseAvailablePlanOrContactCompanion(): Unit = runBlocking {
        val oauth = Disk("oauth"); val key = Disk("api"); seed(oauth, key); api(key)
        val r = runtime(oauth, key); val phone = AbsentPhone(); val auth = auth(r, phone)
        try {
            r.start(); r.ready("api-model")
            r.catalog.answer = { throw ApiFailure(ApiProblem.INVALID_API_KEY) }
            send(r, "Synthetic rejected key"); assertEquals(ApiProblem.INVALID_API_KEY, done(r, 1).problem)
            assertTrue(r.catalog.leases.all { it.mode == CredentialMode.OPENAI_API_KEY })
            assertEquals(CredentialMode.OPENAI_API_KEY, r.router.current().mode); phone.untouched()
        } finally { withContext(main) { auth.viewModelScope.cancel() } }
    }

    @Test fun signOutUsesDirectRevocationAndFreshGraphCannotResurrectSecrets(): Unit = runBlocking {
        for (confirmed in listOf(false, true)) {
            val oauth = Disk("oauth-$confirmed"); val key = Disk("api-$confirmed"); seed(oauth, key)
            var discoveries = 0; var posts = 0
            val remote = OpenAiRevocation(http { request ->
                if (request.url.toString() == OpenAiAuth.DISCOVERY) {
                    discoveries++; 200 to """{"issuer":"https://auth.openai.com","revocation_endpoint":"https://auth.openai.com/revoke"}"""
                } else { posts++; assertEquals("https://auth.openai.com/revoke", request.url.toString()); (if (confirmed) 200 else 400) to "" }
            })
            val phone = AbsentPhone(); val r = runtime(oauth, key); val auth = auth(r, phone, remote::revoke)
            try {
                r.start(); r.ready(terra); send(r, "Before sign-out"); done(r, 2)
                withContext(main) { auth.signOut() }
                withTimeout(5000) { r.p.state.first { it.signedOut && !it.signingOut } }
                assertEquals(if (confirmed) RevocationResult.CONFIRMED else RevocationResult.UNCONFIRMED, r.p.state.value.revocation)
                assertEquals(1, discoveries); assertEquals(1, posts); phone.untouched()
                r.close(); val fresh = runtime(oauth, key); fresh.start()
                withTimeout(5000) { fresh.vm.state.first { it.problem == ApiProblem.REAUTHORIZE } }
                assertTrue(fresh.p.state.value.signedOut); assertEquals(0, fresh.catalog.calls.get())
                try { fresh.router.current(); fail("Signed-out secret resurrected") } catch (f: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, f.problem) }
                phone.untouched()
            } finally { withContext(main) { auth.viewModelScope.cancel() } }
        }
    }

    @Test fun initialSetupRequiresCompanionBeforePreparingOrAcceptingHandoff(): Unit = runBlocking {
        val oauth = Disk("oauth"); val key = Disk("api"); val r = runtime(oauth, key)
        val phone = AbsentPhone().apply { allowLookup = true }; val auth = auth(r, phone)
        try {
            r.start(); withContext(main) { auth.start() }
            withTimeout(5000) { auth.state.first { it == WatchAuthState.COMPANION_UNAVAILABLE } }
            assertEquals(1, phone.lookups.get()); assertEquals(0, phone.requests.get()); assertFalse(r.p.state.value.connected)
            val req = request()
            try {
                r.p.accept("phone", ProfileProtocol.encode(ProfileTransfer(1, req.requestId, BridgeProtocol.newRequestId(),
                    CredentialProfile(RegistrationIdentity("client_one", OpenAiAuth.ISSUER, "subject"), host, "Synthetic", null,
                        false, "synthetic-id", "synthetic-access", "synthetic-refresh", "Bearer", 3600, time,
                        OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES)))))
                fail("Unsolicited handoff accepted")
            } catch (_: IllegalArgumentException) { }
            assertFalse(r.p.state.value.connected); assertEquals(0, r.catalog.generations.get())
        } finally { withContext(main) { auth.viewModelScope.cancel() } }
    }
}
