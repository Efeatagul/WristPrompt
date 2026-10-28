package io.github.grassified.wristprompt.auth

import io.github.grassified.wristprompt.bridge.BridgeProtocol
import java.net.Socket
import java.net.URI
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LoopbackListenerTest {
    @Test(timeout = 4000) fun partialHeadersCannotOutliveAttempt(): Unit = runBlocking {
        val listener = LoopbackListener.bind()
        val transaction = transaction(listener)
        Socket("127.0.0.1", URI(listener.redirectUri).port).use { socket ->
            socket.getOutputStream().write("GET /auth/callback?".encodeToByteArray())
            val started = System.nanoTime()
            try {
                listener.use { it.await(transaction, 150) }
                fail("Expected timeout")
            } catch (failure: AuthException) {
                assertEquals(AuthProblem.TIMEOUT, failure.problem)
            }
            assertTrue("Partial headers must respect the attempt deadline",
                (System.nanoTime() - started) / 1_000_000 < 1500)
        }
    }
    private fun transaction(listener: LoopbackListener) = OAuthTransaction(BridgeProtocol.newHostId(), listener.redirectUri, null, null)
    private fun send(port: Int, target: String): String = Socket("127.0.0.1", port).use {
        it.soTimeout = 3000
        it.getOutputStream().write("GET $target HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n".encodeToByteArray())
        it.getInputStream().bufferedReader().readText()
    }
    @Test fun listenerBindsBeforeBrowserAndReturnsOnlyValidatedCode(): Unit = runBlocking {
        val listener = LoopbackListener.bind(); val port = URI(listener.redirectUri).port
        val transaction = transaction(listener)
        val callback = async(Dispatchers.IO) { send(port, "/auth/callback?state=${transaction.state}&code=synthetic-code&client_id=oaiapp_new") }
        val code = listener.use { it.await(transaction, 3000) }
        assertEquals("synthetic-code", code.code)
        val body = callback.await()
        assertTrue(body.startsWith("HTTP/1.1 200")); assertFalse(body.contains("synthetic-code"))
        assertThrows(Exception::class.java) { Socket("127.0.0.1", port).close() }
    }
    @Test fun mismatchedStateClosesAttemptAndNeverEchoesCallback(): Unit = runBlocking {
        val listener = LoopbackListener.bind(); val port = URI(listener.redirectUri).port
        val transaction = transaction(listener)
        val callback = async(Dispatchers.IO) { send(port, "/auth/callback?state=wrong&code=synthetic-code&client_id=oaiapp_new") }
        try { listener.use { it.await(transaction, 3000) }; fail("Expected rejection") }
        catch (failure: AuthException) { assertEquals(AuthProblem.STATE, failure.problem) }
        assertFalse(callback.await().contains("synthetic-code"))
    }
    @Test fun timeoutAndCancellationCloseListener(): Unit = runBlocking {
        val listener = LoopbackListener.bind(); val port = URI(listener.redirectUri).port
        try { listener.use { it.await(transaction(it), 1) }; fail("Expected timeout") }
        catch (failure: AuthException) { assertEquals(AuthProblem.TIMEOUT, failure.problem) }
        assertThrows(Exception::class.java) { Socket("127.0.0.1", port).close() }
        val cancelled = LoopbackListener.bind(); val cancelledPort = URI(cancelled.redirectUri).port
        val job = launch(Dispatchers.IO) { cancelled.use { it.await(transaction(it)) } }
        delay(50); job.cancelAndJoin()
        assertThrows(Exception::class.java) { Socket("127.0.0.1", cancelledPort).close() }
    }
}
