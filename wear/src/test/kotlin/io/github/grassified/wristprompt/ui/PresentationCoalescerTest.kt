package io.github.grassified.wristprompt.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.grassified.wristprompt.openai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PresentationCoalescerTest {
    private val user = ChatMessage(1, ChatRole.USER, "Q")
    private fun stream(text: String, id: Long = 1) = ChatState(messages = listOf(user.copy(id = id)), stage = ChatStage.RECEIVING, partial = text)
    private fun text(p: ChatPresentation) = p.rows.filter { it.type == TranscriptType.ASSISTANT || it.type == TranscriptType.TAIL }.joinToString("") { it.text }
    private class Fixture(scope: CoroutineScope) {
        var canonical = ChatState()
        val publications = mutableListOf<ChatPresentation>()
        val c = PresentationCoalescer(scope, { canonical }, PresentationMetrics(), publications::add)
        fun accept(state: ChatState) { canonical = state; c.accept(state) }
    }
    @Test fun firstVisibleTextIsImmediate(): Unit = runTest {
        val f = Fixture(this); f.accept(ChatState()); f.accept(stream("first"))
        assertEquals("first",text(f.publications.last())); assertEquals(0, f.c.pendingCount)
    }
    @Test fun burstHasOnePendingFlushAndNewestSnapshotWins(): Unit = runTest {
        val f = Fixture(this); f.accept(stream("a")); val messages = f.canonical.messages
        repeat(1000) { f.accept(f.canonical.copy(messages = messages, partial = "a" + "b".repeat(it + 1))); assertEquals(1, f.c.pendingCount) }
        assertEquals(1, f.publications.size); advanceTimeBy(100); runCurrent()
        assertEquals(2, f.publications.size); assertEquals(1001, text(f.publications.last()).length); assertEquals(0, f.c.pendingCount)
    }
    @Test fun continuousStreamCannotStarveAndIdleDoesNotWake(): Unit = runTest {
        val f = Fixture(this); f.accept(stream("a"))
        repeat(1000) { f.accept(f.canonical.copy(partial = f.canonical.partial + "a")); advanceTimeBy(1); runCurrent() }
        assertEquals(11, f.publications.size); assertEquals(1001,text(f.publications.last()).length)
        advanceTimeBy(10000); runCurrent(); assertEquals(11, f.publications.size); assertEquals(0,f.c.pendingCount)
    }
    @Test fun completedPublishesImmediatelyOnceAndPendingCannotOverwrite(): Unit = runTest {
        val f = Fixture(this); f.accept(stream("partial")); f.accept(f.canonical.copy(partial="buffered")); runCurrent()
        f.accept(ChatState(messages=listOf(user, ChatMessage(2,ChatRole.ASSISTANT,"final authoritative")), stage=ChatStage.COMPLETED))
        assertEquals("final authoritative",text(f.publications.last())); assertEquals(0,f.c.pendingCount)
        val count=f.publications.size; advanceUntilIdle(); assertEquals(count,f.publications.size)
    }
    @Test fun stopAndEveryFailureBypassDelay(): Unit = runTest {
        for (terminal in listOf(ChatStage.CANCELLED,ChatStage.FAILED)) {
            val f=Fixture(this); f.accept(stream("old")); f.accept(f.canonical.copy(partial="latest partial")); runCurrent()
            f.accept(f.canonical.copy(stage=terminal, problem=ApiProblem.INTERRUPTED))
            assertEquals("latest partial",text(f.publications.last())); assertEquals(terminal,f.publications.last().stage)
            val count=f.publications.size; advanceUntilIdle(); assertEquals(count,f.publications.size)
        }
    }
    @Test fun newChatCancelsPendingAndReleasesCache(): Unit = runTest {
        val f=Fixture(this); f.accept(stream("old\ntext")); f.accept(f.canonical.copy(partial="old\ntext more")); runCurrent()
        f.accept(ChatState()); val count=f.publications.size; advanceUntilIdle()
        assertEquals(count,f.publications.size); assertTrue(f.publications.last().rows.isEmpty()); assertEquals(0,f.c.projection.cachedBytes)
    }
    @Test fun newAttemptRejectsOldScheduledOwnership(): Unit = runTest {
        val f=Fixture(this); f.accept(stream("old")); f.accept(f.canonical.copy(partial="old more")); runCurrent()
        f.accept(stream("new",3)); val count=f.publications.size; advanceUntilIdle()
        assertEquals(count,f.publications.size); assertEquals("new",text(f.publications.last()))
        assertTrue(f.publications.last().rows.first().key.startsWith("user-3"))
    }
    @Test fun resetForAccountOrModelNeverReplaysPendingOutput(): Unit = runTest {
        val f=Fixture(this); f.accept(stream("old")); f.accept(f.canonical.copy(partial="old pending")); runCurrent()
        f.accept(ChatState()); f.accept(stream("new account/model",5)); advanceUntilIdle()
        assertEquals("new account/model",text(f.publications.last()))
    }
    @Test fun canonicalTerminalWinsEvenIfFlushRunsBeforeObserver(): Unit = runTest {
        val f=Fixture(this); f.accept(stream("old")); f.accept(f.canonical.copy(partial="pending")); runCurrent()
        f.canonical=f.canonical.copy(stage=ChatStage.CANCELLED)
        advanceTimeBy(100); runCurrent(); assertEquals(1,f.publications.size)
        f.c.accept(f.canonical); assertEquals(ChatStage.CANCELLED,f.publications.last().stage)
    }
    @Test fun inactiveOwnerCancelsTimerAndRebuildsOnlyNewestOnResume(): Unit = runTest {
        val f=Fixture(this); f.accept(stream("first")); f.accept(f.canonical.copy(partial="pending")); runCurrent()
        f.c.pause(); val count=f.publications.size
        repeat(500) { f.canonical=f.canonical.copy(partial="missed $it") }
        advanceTimeBy(5000); runCurrent(); assertEquals(count,f.publications.size)
        f.c.resume(); assertEquals(count+1,f.publications.size); assertEquals("missed 499",text(f.publications.last()))
        assertEquals(0,f.c.pendingCount)
    }
    @Test fun retainedOwnerSurvivesRecreationAndNavigationWithoutDuplicateRows(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store=ViewModelStore()
        try {
            val canonical=MutableStateFlow(stream("one\n".repeat(300)))
            fun owner()=ViewModelProvider.create(store,viewModelFactory { initializer { ChatPresentationOwner(canonical, dispatcher = StandardTestDispatcher(testScheduler)) } })[ChatPresentationOwner::class]
            val original=owner(); original.visible(true); runCurrent(); val first=original.state.value
            original.visible(false); canonical.value=canonical.value.copy(partial=canonical.value.partial+"new"); runCurrent()
            val retained=owner(); assertSame(original,retained); assertEquals(first,retained.state.value)
            retained.visible(true); runCurrent(); assertEquals(canonical.value.partial,text(retained.state.value))
            assertEquals(retained.state.value.rows.size,retained.state.value.rows.map { it.key }.toSet().size)
            store.clear(); advanceUntilIdle(); assertEquals(0,retained.coalescer.pendingCount)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun fineGrainedNearLimitReportsBoundedProjectionWork(): Unit = runTest {
        val f=Fixture(this); f.accept(stream("a")); val start=System.nanoTime()
        repeat(65000) { index ->
            f.accept(f.canonical.copy(partial=f.canonical.partial+"a"))
            if (index % 10 == 9) { advanceTimeBy(1); runCurrent() }
        }
        advanceUntilIdle()
        assertTrue(f.publications.size <= 67)
        assertTrue(f.c.projection.projectedChars < 120000)
        assertEquals(65001,text(f.publications.last()).length)
        println("D5B_METRICS deltas=65000 virtualMs=$currentTime flushes=${f.c.flushes} segmentations=${f.c.projection.segmentations} projectedChars=${f.c.projection.projectedChars} elapsedMs=${(System.nanoTime()-start)/1000000}")
    }
    @Test fun terminalSnapshotDoesNotPublishAgainOnResumeOrDuplicateObservation(): Unit = runTest {
        val f = Fixture(this)
        f.accept(ChatState(listOf(user, ChatMessage(2, ChatRole.ASSISTANT, "final")), stage = ChatStage.COMPLETED))
        f.c.accept(f.canonical); f.c.pause(); f.c.resume()
        assertEquals(1, f.publications.size)
    }
    @Test fun hiddenResetReleasesCacheBeforeReturningToChat(): Unit = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val canonical = MutableStateFlow(ChatState(listOf(user, ChatMessage(2, ChatRole.ASSISTANT, "x".repeat(60000))), stage = ChatStage.COMPLETED))
            val owner = ViewModelProvider.create(store,viewModelFactory { initializer { ChatPresentationOwner(canonical, dispatcher = StandardTestDispatcher(testScheduler)) } })[ChatPresentationOwner::class]
            owner.visible(true); runCurrent(); assertTrue(owner.coalescer.projection.cachedBytes > 0)
            owner.visible(false); canonical.value = ChatState(); runCurrent()
            assertEquals(0, owner.coalescer.projection.cachedBytes); assertTrue(owner.state.value.rows.isEmpty())
            owner.visible(true); runCurrent(); assertTrue(owner.state.value.rows.isEmpty())
        } finally { store.clear(); Dispatchers.resetMain() }
    }

}
