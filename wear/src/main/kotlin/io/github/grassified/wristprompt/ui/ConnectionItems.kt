package io.github.grassified.wristprompt.ui

import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.bridge.WatchAuthViewModel
import io.github.grassified.wristprompt.openai.InferenceViewModel

internal fun TransformingLazyColumnScope.connectionItems(
    ui: ShellPresentation, accounts: WatchAccountState, key: ApiKeyState,
    setup: KeySetupStatus, changing: Boolean, auth: WatchAuthViewModel,
    settings: ApiKeySettingsViewModel, proof: InferenceViewModel, onUsage: () -> Unit,
) {
    item { ShellText("Connection", heading = true) }
    item { ShellText("Use for AI requests") }
    for (mode in CredentialMode.entries) action(
        (if (key.loaded && !key.storageError && key.mode == mode) "✓ " else "") +
            if (mode == CredentialMode.CHATGPT_PLAN) "ChatGPT plan" else "OpenAI API",
        enabled = key.loaded && !key.storageError && !changing,
        onClick = { proof.stop(); settings.selectMode(mode) },
    )
    item { ShellText("ChatGPT account", heading = true) }
    item { ShellText(when {
        accounts.storageError -> "Protected storage unavailable"
        accounts.signingOut -> "Signing out…"
        accounts.signedOut -> if (accounts.revocation == RevocationResult.CONFIRMED)
            "Signed out · revocation confirmed" else "Signed out · remote revocation unconfirmed"
        accounts.expired -> "Setup expired · retry from watch"
        accounts.failed -> "Phone authorization failed · retry from watch"
        accounts.authorizing -> "Finish sign-in on phone"
        accounts.reauthorizationRequired -> "Authentication required"
        accounts.connected -> if (accounts.planEnabled) "Connected" else "Plan usage disabled"
        else -> "Not connected"
    }) }
    for (account in accounts.accounts) action(
        (if (accounts.activeClientId == account.clientId) "✓ " else "") + account.label,
        enabled = !accounts.signingOut, onClick = { auth.select(account.clientId) },
    )
    if (ui.showContinue) continueAction(ui.canContinue, auth::start)
    if (accounts.accounts.isNotEmpty()) action("Add ChatGPT account", !accounts.signingOut) { auth.select(null) }
    if (ui.showManageUsage) action("Manage usage", onClick = onUsage)
    if (ui.showSignOut) action("Sign out of ChatGPT", !accounts.signingOut && !accounts.storageError) {
        if (key.mode == CredentialMode.CHATGPT_PLAN) proof.stop()
        auth.signOut()
    }
    item { ShellText("OpenAI API key", heading = true) }
    item { ShellText(if (key.configured) "Configured · separate API billing" else "Not configured · separate API billing") }
    action(if (key.configured) "Change key" else "Set up API key",
        enabled = setup != KeySetupStatus.REQUESTING && !changing, onClick = settings::request)
    if (setup != KeySetupStatus.IDLE) item { ShellText(keySetupText(setup)) }
    if (ui.showRemoveKey) action("Remove key", !changing) {
        if (key.mode == CredentialMode.OPENAI_API_KEY) proof.stop()
        settings.remove()
    }
}

internal fun keySetupText(setup: KeySetupStatus): String = when (setup) {
    KeySetupStatus.IDLE -> "Request API setup from this watch."
    KeySetupStatus.REQUESTING -> "Finding paired phone…"
    KeySetupStatus.PHONE_READY -> "Open WristPrompt on phone. Enter the key within two minutes."
    KeySetupStatus.UNAVAILABLE -> "Phone unavailable. No credential or billing change."
    KeySetupStatus.ERROR -> "API setup failed. No automatic billing fallback."
}
