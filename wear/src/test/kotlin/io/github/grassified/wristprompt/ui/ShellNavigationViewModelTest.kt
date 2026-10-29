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
            old.rememberPosition(Surface.CHAT, 10, 20)
            oldStore.clear()
            val fresh = navigation(freshStore)
            assertNotSame(old, fresh); assertNull(fresh.position(Surface.CHAT)); assertEquals(Surface.CHAT, fresh.surface.value); assertFalse(fresh.inputPending.value)
            old.surface.value = Surface.COMPOSER
            assertEquals(Surface.CHAT, fresh.surface.value)
        } finally { oldStore.clear(); freshStore.clear() }
    }
    @Test fun defaultRouteIsChatAndModelBackRestoresIt() {
        val nav = ShellNavigationViewModel()
        assertEquals(Surface.CHAT, nav.surface.value)
        nav.open(Surface.MODELS); assertEquals(Surface.MODELS, nav.surface.value)
        nav.back(); assertEquals(Surface.CHAT, nav.surface.value)
    }
    @Test fun menuAccountAndSettingsDiagnosticsHaveBoundedReturnPaths() {
        val nav = ShellNavigationViewModel()
        nav.open(Surface.MENU); nav.open(Surface.CONNECTION)
        nav.back(); assertEquals(Surface.MENU, nav.surface.value)
        nav.open(Surface.SETTINGS); nav.open(Surface.DETAILS); nav.open(Surface.PROOF)
        nav.back(); assertEquals(Surface.DETAILS, nav.surface.value)
        nav.back(); assertEquals(Surface.SETTINGS, nav.surface.value)
        nav.back(); assertEquals(Surface.MENU, nav.surface.value)
        nav.back(); assertEquals(Surface.CHAT, nav.surface.value)
    }
    @Test fun revisitingAncestorDoesNotGrowBackStackAndOutputIsInlineProof() {
        val nav = ShellNavigationViewModel()
        repeat(50) {
            nav.open(Surface.MENU); nav.open(Surface.SETTINGS); nav.open(Surface.DETAILS)
            nav.open(Surface.MENU)
        }
        nav.back(); assertEquals(Surface.CHAT, nav.surface.value)
        nav.open(Surface.OUTPUT); assertEquals(Surface.PROOF, nav.surface.value)
        nav.back(); assertEquals(Surface.CHAT, nav.surface.value)
    }
    @Test fun chatReadingStateRetainedAcrossNavigationAndOwnerRecreation() {
        val retained = ViewModelStore()
        try {
            val nav = navigation(retained); nav.rememberPosition(Surface.CHAT, 12, -18)
            val position = nav.position(Surface.CHAT)
            nav.open(Surface.MODELS); nav.back()
            assertEquals(position, nav.position(Surface.CHAT))
            assertEquals(position, navigation(retained).position(Surface.CHAT))
        } finally { retained.clear() }
    }

    @Test fun retainedOwnerKeepsBackPathAsWellAsDestination() {
        val store = ViewModelStore()
        try {
            val nav = navigation(store)
            nav.open(Surface.MENU); nav.open(Surface.SETTINGS); nav.open(Surface.DETAILS)
            val restored = navigation(store)
            restored.back(); assertEquals(Surface.SETTINGS, restored.surface.value)
            restored.back(); assertEquals(Surface.MENU, restored.surface.value)
            restored.back(); assertEquals(Surface.CHAT, restored.surface.value)
        } finally { store.clear() }
    }
    @Test fun directRecoveryRouteReturnsToChatWithoutChangingItsReadingState() {
        val nav = ShellNavigationViewModel(); nav.rememberPosition(Surface.CHAT, 8, 25)
        val position = nav.position(Surface.CHAT)
        nav.open(Surface.CONNECTION); nav.back()
        assertEquals(Surface.CHAT, nav.surface.value)
        assertEquals(position, nav.position(Surface.CHAT)); assertNull(nav.position(Surface.CHAT, welcome = true))
    }

}
