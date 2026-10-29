package io.github.grassified.wristprompt.openai

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.ui.shellPresentation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class SignOutPresentationTest : ReconstructionFixture() {
    private fun present(r: Runtime, proof: ProofState = r.vm.state.value) =
        shellPresentation(r.p.state.value, r.k.state.value, proof, WatchAuthState.IDLE, false)

    private fun signedOut(r: Runtime, oldCatalog: ProofState) {
        val ui = present(r, oldCatalog)
        assertNull(ui.account)
        assertTrue(r.p.state.value.accounts.none { "Synthetic" in it.label || "nt_one" in it.label })
        assertTrue(ui.showContinue); assertTrue(ui.canContinue)
        assertFalse(ui.proofAvailable); assertFalse(ui.canTest)
        assertEquals("Not selected", ui.model)
        assertFalse(r.router.state.value.usable)
        assertEquals(CredentialMode.CHATGPT_PLAN, r.k.state.value.mode)
    }

    @Test fun pendingSignOutHidesOnlyTargetRegistrationAndKeepsOtherAccountSelectable(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        val r = runtime(oauth, api); r.start(); r.ready(terra)
        r.p.select(null); addPlan(r.p, "client_two"); r.ready(sol)
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val job = async { r.p.signOut { entered.complete(Unit); release.await(); RevocationResult.CONFIRMED } }
        try {
            withTimeout(5000) { entered.await() }
            assertNull(present(r).account)
            val labels = r.p.state.value.accounts.associate { it.clientId to it.label }
            assertEquals("Signed-out ChatGPT account", labels["client_two"])
            assertEquals("Synthetic · nt_one", labels["client_one"])
        } finally { release.complete(Unit); job.await() }
        r.p.select("client_one"); r.ready(terra)
        assertEquals("Synthetic · nt_one", present(r).account)
        assertTrue(present(r).canTest)
    }

    @Test fun signOutHidesIdentityAcrossReconstructionAndReauthenticationRestoresIt(): Unit = runBlocking {
        val oauth = Disk("oauth"); val api = Disk("api"); seed(oauth, api)
        // A configured API key must not become an implicit fallback after plan sign-out.
        val k = ApiKeyRepository(api.record()) { time }; val req = request(); k.prepare("phone", req)
        k.accept("phone", ProfileProtocol.encode(ApiKeyTransfer(1, req.requestId, BridgeProtocol.newRequestId(), host, "synthetic-api-key")))
        val r = runtime(oauth, api); r.start(); r.ready(terra)
        val old = r.vm.state.value; val lease = r.router.current()
        assertEquals("Synthetic · nt_one", present(r).account)
        assertTrue(r.p.state.value.accounts.single().label.contains("Synthetic"))
        assertTrue(r.p.state.value.accounts.single().label.contains("nt_one"))
        r.p.signOut { RevocationResult.CONFIRMED }
        withTimeout(5000) { r.vm.state.first { it.problem == ApiProblem.REAUTHORIZE } }
        signedOut(r, old)
        assertTrue(r.vm.state.value.models.isEmpty()); assertNull(r.vm.state.value.selectedSlug)
        assertFalse(r.router.isCurrent(lease))
        try { r.router.current(); fail("Signed-out lease") } catch (f: ApiFailure) { assertEquals(ApiProblem.REAUTHORIZE, f.problem) }
        try { r.router.selectModel(lease, sol); fail("Old generation") } catch (f: ApiFailure) { assertEquals(ApiProblem.STALE_ACCOUNT, f.problem) }
        val calls = r.catalog.calls.get()
        withContext(main) { r.vm.loadModels(); r.vm.draft("Blocked"); r.vm.send() }
        withTimeout(5000) { r.vm.state.first { !it.busy } }
        assertEquals(calls, r.catalog.calls.get()); assertEquals(0, r.catalog.generations.get())
        val generation = r.p.state.value.generation
        r.p.signOut { error("Repeated sign-out must not revoke again") }
        assertEquals(generation, r.p.state.value.generation)
        signedOut(r, old); r.close()
        val fresh = runtime(oauth, api); fresh.start()
        withTimeout(5000) { fresh.vm.state.first { it.problem == ApiProblem.REAUTHORIZE } }
        signedOut(fresh, old)
        assertEquals(0, fresh.catalog.calls.get())
        // Registration/host survive and the signed-out setup has no ID-token hint.
        val setup = request(); fresh.p.prepare("phone", setup)
        val context = fresh.p.context("phone", ContextRequest(1, setup.requestId, host))
        assertEquals("client_one", context.identity!!.clientId); assertNull(context.idTokenHint)
        addPlan(fresh.p); fresh.ready(terra)
        assertEquals("Synthetic · nt_one", present(fresh).account)
        assertTrue(present(fresh).canTest); assertFalse(present(fresh).showContinue)
        assertTrue(fresh.p.state.value.generation > generation)
        assertFalse(fresh.router.isCurrent(lease))
        assertTrue(fresh.catalog.calls.get() > 0)
        withContext(main) { fresh.vm.draft("Reauthenticated"); fresh.vm.send() }
        withTimeout(5000) { fresh.vm.chat.first { !it.busy && it.messages.isNotEmpty() } }
        assertEquals(1, fresh.catalog.generations.get())
        assertEquals(ChatStage.COMPLETED, fresh.vm.chat.value.stage)
    }
}
