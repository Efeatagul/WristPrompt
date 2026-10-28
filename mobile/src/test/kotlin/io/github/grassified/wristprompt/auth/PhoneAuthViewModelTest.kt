package io.github.grassified.wristprompt.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import io.github.grassified.wristprompt.bridge.*
import io.github.grassified.wristprompt.storage.SecretRecord
import java.io.File
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneAuthViewModelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vm: PhoneAuthViewModel? = null
    @Before fun setup() { Dispatchers.setMain(Dispatchers.Unconfined) }
    @After fun cleanup(): Unit = runBlocking {
        vm?.viewModelScope?.cancel()
        storeScope.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
    }
    private class Record : SecretRecord {
        var bytes: ByteArray? = null
        override fun read() = bytes?.clone()
        override fun write(plaintext: ByteArray) { bytes = plaintext.clone() }
    }
    private suspend fun inbox(request: AuthStartRequest): AuthRequestInbox {
        val store = PreferenceDataStoreFactory.create(scope = storeScope) { File(temporary.root, "request.preferences_pb") }
        return AuthRequestInbox(store).also { it.accept("watch", request) }
    }
    private fun profile(hostId: String, plan: Boolean) = CredentialProfile(
        RegistrationIdentity("oaiapp_test", OpenAiAuth.ISSUER, "subject"), hostId, null, null, false,
        "synthetic-id", "synthetic-access", "synthetic-refresh", "Bearer", 3600, System.currentTimeMillis(),
        if (plan) OpenAiAuth.scopes(OpenAiAuth.REQUESTED_SCOPES) else setOf("openid"))
    private fun CoroutineScope.browser(): (String) -> Unit = { url ->
        val params = URI(url).rawQuery.split('&').associate { part ->
            val pair = part.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
        val port = URI(params.getValue("redirect_uri")).port
        launch(Dispatchers.IO) {
            Socket("127.0.0.1", port).use { socket ->
                socket.soTimeout = 3000
                val target = "/auth/callback?state=${params.getValue("state")}&code=synthetic-code&client_id=oaiapp_test"
                socket.getOutputStream().write("GET $target HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n".encodeToByteArray())
                socket.getInputStream().readBytes()
            }
        }
    }
    @Test fun lostAckIsNotSuccessAndExplicitRetryReusesOneValidatedTransfer(): Unit = runBlocking {
        val request = AuthStartRequest(1, BridgeProtocol.newRequestId(), BridgeProtocol.newHostId(), System.currentTimeMillis())
        var allowAck = false; var exchanges = 0
        val sent = mutableListOf<ProfileTransfer>()
        val endpoint: suspend (String, String, ByteArray) -> ByteArray = { node, path, bytes ->
            assertEquals("watch", node)
            if (path == ProfileProtocol.CONTEXT_PATH) ProfileProtocol.encode(LoginContext(1, request.requestId, null, null))
            else {
                val value = ProfileProtocol.decodeTransfer(bytes); sent += value
                if (!allowAck) throw java.io.IOException("Synthetic lost ACK")
                ProfileProtocol.encode(ProfileAck(1, value.requestId, value.transferId))
            }
        }
        val model = PhoneAuthViewModel(inbox(request), endpoint, IssuedRegistrationStore(Record())) { _, _ ->
            exchanges++; profile(request.hostId, true)
        }.also { vm = it }
        withTimeout(5000) { model.state.first { it.stage == PhoneStage.READY } }
        model.start(browser())
        withTimeout(5000) { model.state.first { it.stage == PhoneStage.RETRY_TRANSFER } }
        assertEquals(1, exchanges)
        allowAck = true
        // The completed job is idle before an explicit retry, just as after the UI becomes clickable.
        yield(); delay(20)
        model.retryTransfer()
        withTimeout(5000) { model.state.first { it.stage == PhoneStage.CONNECTED } }
        assertEquals(1, exchanges); assertEquals(2, sent.size); assertEquals(sent[0], sent[1])
    }
    @Test fun missingPlanScopeRemainsIdentityOnlyAfterAck(): Unit = runBlocking {
        val request = AuthStartRequest(1, BridgeProtocol.newRequestId(), BridgeProtocol.newHostId(), System.currentTimeMillis())
        val endpoint: suspend (String, String, ByteArray) -> ByteArray = { _, path, bytes ->
            if (path == ProfileProtocol.CONTEXT_PATH) ProfileProtocol.encode(LoginContext(1, request.requestId, null, null))
            else ProfileProtocol.decodeTransfer(bytes).let { ProfileProtocol.encode(ProfileAck(1, it.requestId, it.transferId)) }
        }
        val model = PhoneAuthViewModel(inbox(request), endpoint, IssuedRegistrationStore(Record())) { _, _ -> profile(request.hostId, false) }.also { vm = it }
        withTimeout(5000) { model.state.first { it.stage == PhoneStage.READY } }
        model.start(browser())
        withTimeout(5000) { model.state.first { it.stage == PhoneStage.IDENTITY_ONLY } }
    }
    @Test fun browserFailureNeverExchangesOrTransfersAndNotifiesOnlyBoundWatch(): Unit = runBlocking {
        val request = AuthStartRequest(1, BridgeProtocol.newRequestId(), BridgeProtocol.newHostId(), System.currentTimeMillis())
        val paths = mutableListOf<String>()
        val endpoint: suspend (String, String, ByteArray) -> ByteArray = { node, path, _ ->
            assertEquals("watch", node); paths += path
            if (path == ProfileProtocol.CONTEXT_PATH) ProfileProtocol.encode(LoginContext(1, request.requestId, null, null)) else byteArrayOf(1)
        }
        val model = PhoneAuthViewModel(inbox(request), endpoint, IssuedRegistrationStore(Record())) { _, _ -> error("Must not exchange") }.also { vm = it }
        withTimeout(5000) { model.state.first { it.stage == PhoneStage.READY } }
        model.start { throw IllegalStateException("Synthetic browser unavailable") }
        withTimeout(5000) { model.state.first { it.stage == PhoneStage.FAILED } }
        assertEquals(AuthProblem.BROWSER, model.state.value.problem)
        withTimeout(5000) { while (ProfileProtocol.FAILURE_PATH !in paths) delay(10) }
        assertFalse(ProfileProtocol.TRANSFER_PATH in paths)
    }
}
