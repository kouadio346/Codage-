package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOut
import com.example.model.StudioDestination
import com.example.ui.ProtoSnapViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtoSnapApp(
    viewModel: ProtoSnapViewModel,
    userEmail: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val savedPrototypesState by viewModel.savedPrototypesState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSaveAsNewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(studioState.snackbarMessage) {
        val msg = studioState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    if (studioState.destination != StudioDestination.IMAGE_AI) {
        BackHandler {
            viewModel.setDestination(StudioDestination.IMAGE_AI)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = studioState.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (studioState.activePrototypeId == null) {
                                    "Brouillon local · Appuyez sur Sauvegarder"
                                } else if (studioState.hasUnsavedChanges) {
                                    "Modifications non sauvegardées"
                                } else {
                                    "Synchronisé dans Firestore"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(
                            onClick = { showRenameDialog = true },
                            modifier = Modifier.testTag("rename_prototype_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Renommer le prototype",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                actions = {
                    if (studioState.activePrototypeId != null) {
                        IconButton(
                            onClick = { showSaveAsNewDialog = true },
                            enabled = !studioState.isSaving,
                            modifier = Modifier.testTag("save_as_new_prototype_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LibraryAdd,
                                contentDescription = "Enregistrer une nouvelle copie",
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { viewModel.savePrototype(saveAsNew = false) },
                        enabled = !studioState.isSaving,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_prototype_button"),
                    ) {
                        if (studioState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = if (studioState.activePrototypeId != null && !studioState.hasUnsavedChanges) {
                                    Icons.Rounded.CloudDone
                                } else {
                                    Icons.Rounded.Save
                                },
                                contentDescription = stringResource(R.string.action_save_project),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (studioState.activePrototypeId != null && !studioState.hasUnsavedChanges) {
                                    "Sauvé"
                                } else {
                                    "Sauver"
                                }
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            BoxWithConstraints {
                if (maxWidth < 840.dp) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        StudioDestination.entries.forEach { dest ->
                            val selected = studioState.destination == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.setDestination(dest) },
                                icon = {
                                    Icon(
                                        imageVector = destinationIcon(dest, selected),
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_${dest.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val isWide = maxWidth >= 840.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isWide) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Spacer(Modifier.size(8.dp))
                        StudioDestination.entries.forEach { dest ->
                            val selected = studioState.destination == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.setDestination(dest) },
                                icon = {
                                    Icon(
                                        imageVector = destinationIcon(dest, selected),
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.name.lowercase()}"),
                            )
                        }
                    }
                }

                // Adaptive Content Area: On wide screens, pair Image/Code with Live Preview side-by-side!
                if (isWide && (studioState.destination == StudioDestination.IMAGE_AI || studioState.destination == StudioDestination.CODE)) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            if (studioState.destination == StudioDestination.IMAGE_AI) {
                                ImageAndAiPane(
                                    state = studioState,
                                    onImagesPicked = { uris -> viewModel.loadImagesFromUris(context, uris) },
                                    onSelectGalleryImage = viewModel::selectGalleryImage,
                                    onRemoveGalleryImage = viewModel::removeGalleryImage,
                                    onSelectTranscriptionMode = viewModel::setTranscriptionMode,
                                    onLoadSample = { viewModel.loadSampleMockup(context) },
                                    onAddHotspot = viewModel::addHotspot,
                                    onRemoveHotspot = viewModel::removeHotspot,
                                    onClearHotspots = viewModel::clearHotspots,
                                    onDeveloperPromptChange = viewModel::updateDeveloperPrompt,
                                    onTranscribeImage = viewModel::transcribeImage,
                                    onOptimizeFrontend = viewModel::optimizeFrontendIntegration,
                                    onApplySuggestion = viewModel::applyJsSuggestion,
                                    onCustomJsPromptChange = viewModel::updateCustomJsPrompt,
                                    onSubmitCustomJs = viewModel::submitCustomJsPrompt,
                                    onToggleAutoSave = viewModel::toggleAutoSave,
                                    onOpenLivePreview = { viewModel.setDestination(StudioDestination.PREVIEW) },
                                    onOpenProjects = { viewModel.setDestination(StudioDestination.PROJECTS) },
                                )
                            } else {
                                CodeEditorPane(
                                    state = studioState,
                                    onSelectCodeTab = viewModel::setCodeTab,
                                    onCodeChanged = viewModel::updateCodeForActiveTab,
                                    onSaveSnippet = { viewModel.savePrototype(saveAsNew = false) },
                                    standaloneHtmlProvider = viewModel::buildStandaloneHtmlDocument,
                                    onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            LivePreviewPane(
                                state = studioState,
                                onSelectViewport = viewModel::setViewportMode,
                                onToggleConsole = viewModel::toggleConsoleVisibility,
                                onClearConsole = viewModel::clearConsoleLogs,
                                onConsoleLog = viewModel::appendConsoleLog,
                                standaloneHtmlProvider = viewModel::buildStandaloneHtmlDocument,
                                onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                            )
                        }
                    }
                } else {
                    when (studioState.destination) {
                        StudioDestination.IMAGE_AI -> ImageAndAiPane(
                            state = studioState,
                            onImagesPicked = { uris -> viewModel.loadImagesFromUris(context, uris) },
                            onSelectGalleryImage = viewModel::selectGalleryImage,
                            onRemoveGalleryImage = viewModel::removeGalleryImage,
                            onSelectTranscriptionMode = viewModel::setTranscriptionMode,
                            onLoadSample = { viewModel.loadSampleMockup(context) },
                            onAddHotspot = viewModel::addHotspot,
                            onRemoveHotspot = viewModel::removeHotspot,
                            onClearHotspots = viewModel::clearHotspots,
                            onDeveloperPromptChange = viewModel::updateDeveloperPrompt,
                            onTranscribeImage = viewModel::transcribeImage,
                            onOptimizeFrontend = viewModel::optimizeFrontendIntegration,
                            onApplySuggestion = viewModel::applyJsSuggestion,
                            onCustomJsPromptChange = viewModel::updateCustomJsPrompt,
                            onSubmitCustomJs = viewModel::submitCustomJsPrompt,
                            onToggleAutoSave = viewModel::toggleAutoSave,
                            onOpenLivePreview = { viewModel.setDestination(StudioDestination.PREVIEW) },
                            onOpenProjects = { viewModel.setDestination(StudioDestination.PROJECTS) },
                        )
                        StudioDestination.CODE -> CodeEditorPane(
                            state = studioState,
                            onSelectCodeTab = viewModel::setCodeTab,
                            onCodeChanged = viewModel::updateCodeForActiveTab,
                            onSaveSnippet = { viewModel.savePrototype(saveAsNew = false) },
                            standaloneHtmlProvider = viewModel::buildStandaloneHtmlDocument,
                            onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        )
                        StudioDestination.PREVIEW -> LivePreviewPane(
                            state = studioState,
                            onSelectViewport = viewModel::setViewportMode,
                            onToggleConsole = viewModel::toggleConsoleVisibility,
                            onClearConsole = viewModel::clearConsoleLogs,
                            onConsoleLog = viewModel::appendConsoleLog,
                            standaloneHtmlProvider = viewModel::buildStandaloneHtmlDocument,
                            onNotify = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                        )
                        StudioDestination.PROJECTS -> ProjectsLibraryPane(
                            prototypesState = savedPrototypesState,
                            activePrototypeId = studioState.activePrototypeId,
                            userEmail = userEmail,
                            onOpenProject = viewModel::openProject,
                            onDuplicateProject = viewModel::duplicateProject,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onDeleteProject = viewModel::deleteProject,
                            onStartBlank = viewModel::startBlankStarter,
                            onSignOut = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        var draftTitle by remember(studioState.title) { mutableStateOf(studioState.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Nom du prototype") },
            text = {
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it.take(100) },
                    label = { Text("Titre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTitle(draftTitle.trim().ifBlank { "Prototype sans titre" })
                        showRenameDialog = false
                    },
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Annuler")
                }
            },
        )
    }

    if (showSaveAsNewDialog) {
        var snapshotTitle by remember(studioState.title) { mutableStateOf("${studioState.title} v2".take(100)) }
        AlertDialog(
            onDismissRequest = { showSaveAsNewDialog = false },
            title = { Text("Enregistrer comme nouveau prototype") },
            text = {
                OutlinedTextField(
                    value = snapshotTitle,
                    onValueChange = { snapshotTitle = it.take(100) },
                    label = { Text("Nom du snapshot Firestore") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.savePrototypeWithTitle(snapshotTitle, saveAsNew = true)
                        showSaveAsNewDialog = false
                    },
                    modifier = Modifier.testTag("confirm_save_as_new_button"),
                ) {
                    Text("Enregistrer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveAsNewDialog = false }) {
                    Text("Annuler")
                }
            },
        )
    }
}

private fun destinationIcon(dest: StudioDestination, selected: Boolean) = when (dest) {
    StudioDestination.IMAGE_AI -> if (selected) Icons.Rounded.AutoAwesome else Icons.Outlined.AutoAwesome
    StudioDestination.CODE -> if (selected) Icons.Rounded.Code else Icons.Outlined.Code
    StudioDestination.PREVIEW -> if (selected) Icons.Rounded.PlayCircle else Icons.Outlined.PlayCircle
    StudioDestination.PROJECTS -> if (selected) Icons.Rounded.Folder else Icons.Outlined.Folder
}
