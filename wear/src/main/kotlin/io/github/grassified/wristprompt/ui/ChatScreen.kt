package io.github.grassified.wristprompt.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.wear.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import io.github.grassified.wristprompt.openai.*

/** Disposable native text projection is observed only in this transcript subtree. */
@Composable
internal fun ChatScreen(
    proof: InferenceViewModel, state: ProofState, ui: ShellPresentation,
    navigation: ShellNavigationViewModel, supplementalGuidance: String?, metrics: PresentationMetrics? = null,
) {
    val owner: ChatPresentationOwner = viewModel(factory = viewModelFactory {
        initializer { ChatPresentationOwner(proof.chat, metrics) }
    })
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(owner, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            owner.visible(true)
            try { awaitCancellation() } finally { owner.visible(false) }
        }
    }
    val presentation by owner.state.collectAsStateWithLifecycle()
    val readOnlyReason by remember(proof) { proof.chat.map { it.readOnlyReason }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(initialValue = null)
    var imageId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val image = presentation.rows.mapNotNull { it.generated }.firstOrNull { it.id == imageId }
    if (image != null) GeneratedImageViewer(proof, image) { imageId = null }
    var inspected by remember { mutableStateOf<List<MarkdownLink>?>(null) }
    // A reset invalidates displayed destinations along with the conversation.
    LaunchedEffect(presentation.rows.isEmpty()) { if (presentation.rows.isEmpty()) inspected = null }
    if (inspected != null) {
        androidx.activity.compose.BackHandler { inspected = null }
        LinkInspection(inspected.orEmpty()) { inspected = null }
        return
    }
    val setup = ui.recovery ?: if (state.selectedSlug == null) modelCatalogPresentation(state) else null
    val showSetup = setup != null && presentation.stage !in setOf(ChatStage.FAILED, ChatStage.CANCELLED)
    val largeFont = LocalDensity.current.fontScale > 1.2f
    val keys = buildList {
        if (largeFont) addAll(listOf("chat-title", "chat-menu", "chat-model", "chat-billing"))
        else add("chat-header")
        if (showSetup) add("chat-unavailable")
        if (readOnlyReason != null) add("chat-readonly")
        if (presentation.rows.isEmpty()) add("chat-empty") else addAll(presentation.rows.map { it.key })
    }
    val scroll = rememberChatScroll(navigation, keys)
    val latest by remember(presentation.rows, scroll) {
        derivedStateOf { latestAvailable(presentation.rows.isNotEmpty(), scroll.canScrollForward) }
    }
    LaunchedEffect(latest, navigation) { navigation.latestVisible.value = latest }
    DisposableEffect(navigation) { onDispose { navigation.latestVisible.value = false } }
    LaunchedEffect(navigation, scroll) {
        navigation.latestRequests.collect { scrollToLatest(scroll) }
    }
    ShellList(Modifier.fillMaxSize(), scroll, spacing = 0.dp) {
        if (largeFont) {
            item(key = "chat-title", contentType = "header") {
                // Keep the native scaled title size; split the brand at its word boundary.
                Text("Wrist\nPrompt", modifier = Modifier.fillMaxWidth().semantics { contentDescription = "WristPrompt" },
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            }
            item(key = "chat-menu", contentType = "header") {
                TextButton(onClick = { navigation.open(Surface.MENU) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Menu", maxLines = 1, softWrap = false) }
            }
            item(key = "chat-model", contentType = "header") { ModelAffordance(ui, navigation, false) }
            item(key = "chat-billing", contentType = "header") { BillingText(ui) }
        } else item(key = "chat-header", contentType = "header") {
            ChatHeader(ui, navigation)
        }
        if (showSetup) {
            item(key = "chat-unavailable", contentType = "status") { ShellText(setup.text) }
        }
        readOnlyReason?.let { reason -> item(key = "chat-readonly", contentType = "status") { ShellText(reason) } }
        if (presentation.rows.isEmpty()) {
            item(key = "chat-empty", contentType = "status") { ShellText("Ask anything") }
        } else chatItems(presentation, proof, { inspected = it }, { imageId = it.id })
        supplementalGuidance?.let { text ->
            item(key = "chat-guidance", contentType = "recovery") { ShellText(text) }
        }
    }
}

@Composable
private fun ChatHeader(ui: ShellPresentation, navigation: ShellNavigationViewModel) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("WristPrompt", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = { navigation.open(Surface.MENU) },
                modifier = Modifier.heightIn(min = 48.dp)) { Text("Menu") }
        }
        ModelAffordance(ui, navigation, true)
    }
}

@Composable
private fun ModelAffordance(ui: ShellPresentation, navigation: ShellNavigationViewModel, showBilling: Boolean) {
    Button(onClick = { navigation.open(Surface.MODELS) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .semantics { contentDescription = "Current model: ${ui.model}. Choose model" },
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground),
        label = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${ui.model} ▾", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                if (showBilling) BillingText(ui)
            }
        })
}

@Composable
private fun BillingText(ui: ShellPresentation) {
    Text(billingDisclosure(ui.billing), style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
internal fun ComposerScreen(
    proof: InferenceViewModel, state: ProofState, ui: ShellPresentation,
    navigation: ShellNavigationViewModel, interruptedInput: Boolean, inputUnavailable: Boolean,
    write: () -> Unit, photos: io.github.grassified.wristprompt.images.WatchImagesViewModel? = null,
) {
    val chat by proof.chat.collectAsStateWithLifecycle()
    val selectedPhotos by proof.imageSelection.selected.collectAsStateWithLifecycle()
    val availablePhotos = photos?.store?.images?.collectAsStateWithLifecycle()?.value.orEmpty()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    ShellList(Modifier.fillMaxSize(), rememberShellScroll(navigation, Surface.COMPOSER)) {
        item { ShellText("Review message", heading = true) }
        chat.readOnlyReason?.let { reason -> item { ShellText(reason) } }
        chatError(chat)?.let { error -> item { ShellText(error.text) } }
        item { ModelAffordance(ui, navigation, false) }
        if (!ui.proofAvailable || state.selectedSlug == null) item { ShellText(ui.status) }
        photos?.let { library ->
            action("Photos (${selectedPhotos.size}/6)", !chat.busy && !state.busy) {
                if (library.begin(proof)) navigation.open(Surface.IMAGES)
            }
        }
        if (selectedPhotos.isNotEmpty()) item { ShellText("Selected photos accompany this message when you press Send. Text is required.") }
        if (selectedPhotos.isNotEmpty() && chat.draft.isBlank())
            item { ShellText("Text required. Add a question before Send; your photos stay selected.") }
        photos?.let { library ->
            selectedPhotos.forEachIndexed { index, id ->
                item(key = "draft-photo-$id") {
                    val image = availablePhotos.firstOrNull { it.id == id }
                    if (image == null) ShellText("Photo ${index + 1} unavailable. Remove it or restore it before Send.")
                    else io.github.grassified.wristprompt.images.LibraryThumbnail(library.store, image,
                        Modifier.fillMaxWidth().height(80.dp))
                }
                action("Remove photo ${index + 1}", !chat.busy && !state.busy) { proof.removeDraftImage(id) }
            }
        }
        action("Prompts", !chat.busy && !state.busy) { navigation.open(Surface.PROMPTS) }
        action(if (chat.draft.isEmpty()) "Write message" else "Replace draft", !chat.busy && !state.busy, write)
        if (interruptedInput) item { ShellText("Text input was interrupted. Write your message again.") }
        if (inputUnavailable) item { ShellText("System text input unavailable. Return and try again.") }
        if (chat.draft.isEmpty()) item { ShellText("Enter text, review it here, then Send.") }
        item { ShellText("Edit draft", heading = true) }
        item {
            androidx.compose.foundation.text.BasicTextField(
                value = chat.draft, onValueChange = { proof.draft(it) },
                enabled = !chat.busy && !state.busy, maxLines = 6,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = androidx.wear.compose.material3.MaterialTheme.typography.bodyMedium.copy(color = androidx.wear.compose.material3.MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .semantics { contentDescription = "Edit message draft" },
            )
        }
        if (chat.draft.length > ChatConversation.MAX_PROMPT_CHARS)
            item { ShellText("Message too long. Limit: ${ChatConversation.MAX_PROMPT_CHARS} characters. Replace the draft with a shorter message.") }
        if (chat.limitReached) {
            item { ShellText("Start a new chat to continue") }
            action("New Chat") { proof.newChat(); navigation.open(Surface.CHAT) }
        }
        val generationSupported = state.models.firstOrNull { it.slug == state.selectedSlug }?.let {
            it.imageGeneration != null && it.imageGeneration == OpenAiGeneration.capability(it.slug, state.mode)
        } == true
        if (!generationSupported) item { ShellText("Image generation unavailable for this route/model. Requires a reviewed OpenAI API model.") }
        else {
            item { ShellText("Generate a new image from this text. Uses separate OpenAI API billing. Input photos are not supported.") }
            action("Generate image", chat.canSend && !state.busy && selectedPhotos.isEmpty()) {
                proof.generateImage { navigation.open(Surface.CHAT) }
            }
        }
        action("Back to chat") { navigation.open(Surface.CHAT) }
        item { ShellText("Unsent drafts are discarded when the app process ends.") }
        action("Discard draft and photos") { proof.discardDraft(); navigation.open(Surface.CHAT) }
    }
}
