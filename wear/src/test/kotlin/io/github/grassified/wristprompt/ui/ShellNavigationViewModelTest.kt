package io.github.grassified.wristprompt.ui

import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.*
import org.junit.Test

class ShellNavigationViewModelTest {
    private fun navigation(store: ViewModelStore) = ViewModelProvider.create(store,
        viewModelFactory { initializer { ShellNavigationViewModel() } })[ShellNavigationViewModel::class]
    @Test fun retainedStoreKeepsPageAndInterruptionMarker() {
        val retained = ViewModelStore()
        try {
            val old = navigation(retained); old.surface.value = Surface.COMPOSER; old.inputPending.value = true
            val recreated = navigation(retained)
            assertSame(old, recreated); assertEquals(Surface.COMPOSER, recreated.surface.value)
            assertTrue(recreated.inputPending.value)
        } finally { retained.clear() }
    }
    @Test fun freshStoreHasNoPageOrPendingInputFromDeadOwner() {
        val oldStore = ViewModelStore(); val freshStore = ViewModelStore()
        try {
            val old = navigation(oldStore); old.surface.value = Surface.DETAILS; old.inputPending.value = true
            oldStore.clear()
            val fresh = navigation(freshStore)
            assertNotSame(old, fresh); assertEquals(Surface.CHAT, fresh.surface.value); assertFalse(fresh.inputPending.value)
            old.surface.value = Surface.COMPOSER
            assertEquals(Surface.CHAT, fresh.surface.value)
        } finally { oldStore.clear(); freshStore.clear() }
    }
}
