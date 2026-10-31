package io.github.grassified.wristprompt.ui

import io.github.grassified.wristprompt.auth.*
import io.github.grassified.wristprompt.openai.InferenceViewModel

internal fun ShellListScope.connectionItems(
    ui: ShellPresentation, accounts: WatchAccountState, key: ApiKeyState,
    setup: KeySetupStatus, changing: Boolean, actions: ConnectionActions, proof: InferenceViewModel, onDiagnostics: () -> Unit, onUsage: () -> Unit,
) {
    item { ShellText("Account & Connection", heading = true) }
    item { ShellText(billingDisclosure(ui.billing)) }
    if (ui.recovery?.action == RecoveryAction.DIAGNOSTICS) {
        item { ShellText(ui.recovery.explanation.orEmpty()) }
        action("Diagnostics") { onDiagnostics() }
    }
    item { ShellText("Billing route", heading = true) }
    for (mode in CredentialMode.entries) action(
        (if (key.loaded && !key.storageError && key.mode == mode) "✓ " else "") +
            if (mode == CredentialMode.CHATGPT_PLAN) "ChatGPT plan" else "OpenAI API · API billing",
        enabled = key.loaded && !key.storageError && !changing,
        onClick = { proof.stop(); actions.mode(mode) },
    )
    item { ShellText("ChatGPT account", heading = true) }
    item { ShellText(when {
        accounts.storageError -> "Secure storage unavailable"
        accounts.signingOut -> "Signing out…"
        accounts.signedOut -> if (accounts.revocation == RevocationResult.CONFIRMED)
            "Signed out · revocation confirmed" else "Signed out · remote revocation unconfirmed"
        accounts.expired -> "Setup expired · retry from watch"
        accounts.failed -> "Phone authorization failed · retry from watch"
        accounts.authorizing -> "Finish sign-in on phone"
        accounts.reauthorizationRequired -> "Sign in again to continue"
        accounts.connected -> if (accounts.planEnabled) "Connected" else "Plan usage disabled"
        else -> "Not connected"
    }) }
    for (account in accounts.accounts) action(
        (if (accounts.activeClientId == account.clientId) "✓ " else "") + accountRowName(accounts, account),
        enabled = !accounts.signingOut, onClick = { actions.select(account.clientId) },
    )
    if (ui.showContinue) continueAction(ui.canContinue, actions.start)
    if (accounts.accounts.isNotEmpty()) action("Add ChatGPT account", !accounts.signingOut) { actions.select(null) }
    if (ui.showManageUsage) action("Manage usage", onClick = onUsage)
    if (ui.showSignOut) action("Sign out of ChatGPT", !accounts.signingOut && !accounts.storageError) {
        if (key.mode == CredentialMode.CHATGPT_PLAN) proof.stop()
        actions.signOut()
    }
    item { ShellText("OpenAI API key", heading = true) }
    item { ShellText(if (key.configured) "Configured · separate API billing" else "Not configured · separate API billing") }
    action(if (key.configured) "Change API key" else "Set up API key",
        enabled = setup != KeySetupStatus.REQUESTING && !changing, onClick = actions.request)
    if (setup != KeySetupStatus.IDLE) item { ShellText(keySetupText(setup)) }
    if (ui.showRemoveKey) action("Remove API key", !changing) {
        if (key.mode == CredentialMode.OPENAI_API_KEY) proof.stop()
        actions.remove()
    }
}

internal fun keySetupText(setup: KeySetupStatus): String = when (setup) {
    KeySetupStatus.IDLE -> "Request API setup from this watch."
    KeySetupStatus.REQUESTING -> "Finding paired phone…"
    KeySetupStatus.PHONE_READY -> "Open WristPrompt on phone. Enter the key within two minutes."
    KeySetupStatus.UNAVAILABLE -> "Phone unavailable. No credential or billing change."
    KeySetupStatus.ERROR -> "API setup failed. No automatic billing fallback."
}

internal fun accountRowName(accounts: WatchAccountState, account: AccountLabel): String =
    if (accounts.activeClientId == account.clientId && (accounts.reauthorizationRequired || accounts.storageError ||
            accounts.signingOut || accounts.signedOut)) "Sign-in required" else account.label
