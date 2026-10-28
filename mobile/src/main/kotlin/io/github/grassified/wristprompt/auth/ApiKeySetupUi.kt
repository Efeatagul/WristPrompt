package io.github.grassified.wristprompt.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun ApiKeySetupUi(model: PhoneApiKeyViewModel) {
    val key by model.key.collectAsStateWithLifecycle()
    val stage by model.stage.collectAsStateWithLifecycle()
    Column {
        Text("OpenAI API key / separate API billing", style = MaterialTheme.typography.titleMedium)
        Text("Advanced: your key is transferred to protected watch storage. A client-held key is not isolated on a server. Requests use separate OpenAI API billing. Select the mode on your watch.")
        Text(when (stage) {
            PhoneKeyStage.WAITING -> "On the watch, request API-key setup on this phone."
            PhoneKeyStage.READY -> "Enter your own API key. Local format checks do not prove validity."
            PhoneKeyStage.SENDING -> "Transferring to the requesting watch…"
            PhoneKeyStage.RETRY -> "Storage acknowledgement was not received. Retry the same transfer before it expires."
            PhoneKeyStage.ACKNOWLEDGED -> "Secure watch storage acknowledged. Entry cleared. Explicitly select API billing on the watch, then Test API key."
            PhoneKeyStage.EXPIRED -> "Setup request expired. Start again from the watch."
            PhoneKeyStage.FORMAT_ERROR -> "Use 8–8192 printable non-space characters. Format does not prove validity."
        })
        if (stage in setOf(PhoneKeyStage.READY, PhoneKeyStage.FORMAT_ERROR)) {
            OutlinedTextField(value = key, onValueChange = model::edit, label = { Text("Your OpenAI API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false))
            Button(onClick = model::transfer) { Text("Transfer to requesting watch") }
        }
        if (stage == PhoneKeyStage.RETRY) Button(onClick = model::transfer) { Text("Retry secure transfer") }
    }
}
