package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AiCodeRepository
import com.example.data.GeneratedPrototypeResult
import com.example.data.PrototypeRepository
import com.example.model.AiTaskState
import com.example.model.CodeTab
import com.example.model.ConsoleEntry
import com.example.model.GalleryImageItem
import com.example.model.ImageHotspot
import com.example.model.JsSuggestion
import com.example.model.PrototypeProject
import com.example.model.StudioDestination
import com.example.model.TranscriptionMode
import com.example.model.UiState
import com.example.model.ViewportMode
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "ProtoSnapVM"
private const val STOP_TIMEOUT_MILLIS = 5000L

data class StudioUiState(
    val destination: StudioDestination = StudioDestination.IMAGE_AI,
    val activeCodeTab: CodeTab = CodeTab.HTML,
    val viewportMode: ViewportMode = ViewportMode.FULL,
    val activePrototypeId: String? = null,
    val title: String = "Carte Tarifaire Interactive",
    val sourceLabel: String = "Prototype de démarrage",
    val htmlCode: String = DEFAULT_HTML,
    val cssCode: String = DEFAULT_CSS,
    val jsCode: String = DEFAULT_JS,
    val aiNotes: String = "Starter interactif prêt : bascule mensuel/annuel avec calcul dynamique en JavaScript.",
    val interactiveFeatures: List<String> = listOf(
        "Bascule Mensuel / Annuel",
        "Sélection de plan active",
        "Logs console temps réel",
    ),
    val suggestions: List<JsSuggestion> = DEFAULT_SUGGESTIONS,
    val hotspots: List<ImageHotspot> = emptyList(),
    val galleryImages: List<GalleryImageItem> = emptyList(),
    val activeImageId: String? = null,
    val selectedBitmap: Bitmap? = null,
    val transcriptionMode: TranscriptionMode = TranscriptionMode.REBUILD_UI,
    val developerPrompt: String = "",
    val customJsPrompt: String = "",
    val aiTaskState: AiTaskState = AiTaskState.Idle,
    val autoSaveAiResults: Boolean = true,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val consoleLogs: List<ConsoleEntry> = emptyList(),
    val isConsoleVisible: Boolean = false,
    val snackbarMessage: String? = null,
)

class ProtoSnapViewModel(
    private val prototypeRepository: PrototypeRepository,
    private val aiCodeRepository: AiCodeRepository,
    private val currentUserId: String,
) : ViewModel() {

    val savedPrototypesState: StateFlow<UiState<List<PrototypeProject>>> =
        prototypeRepository.observePrototypes(currentUserId)
            .map<List<PrototypeProject>, UiState<List<PrototypeProject>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing prototypes", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Impossible de charger les prototypes."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading,
            )

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    fun setDestination(destination: StudioDestination) {
        _studioState.update { it.copy(destination = destination) }
    }

    fun setCodeTab(tab: CodeTab) {
        _studioState.update { it.copy(activeCodeTab = tab) }
    }

    fun setViewportMode(mode: ViewportMode) {
        _studioState.update { it.copy(viewportMode = mode) }
    }

    fun toggleConsoleVisibility() {
        _studioState.update { it.copy(isConsoleVisible = !it.isConsoleVisible) }
    }

    fun appendConsoleLog(level: String, message: String) {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val entry = ConsoleEntry(
            level = level,
            message = message,
            timestamp = formatter.format(Date()),
        )
        _studioState.update { state ->
            val updated = (state.consoleLogs + entry).takeLast(60)
            state.copy(consoleLogs = updated)
        }
    }

    fun clearConsoleLogs() {
        _studioState.update { it.copy(consoleLogs = emptyList()) }
    }

    fun updateTitle(newTitle: String) {
        _studioState.update { it.copy(title = newTitle, hasUnsavedChanges = true) }
    }

    fun updateDeveloperPrompt(value: String) {
        _studioState.update { it.copy(developerPrompt = value) }
    }

    fun updateCustomJsPrompt(value: String) {
        _studioState.update { it.copy(customJsPrompt = value) }
    }

    fun updateCodeForActiveTab(newCode: String) {
        _studioState.update { state ->
            when (state.activeCodeTab) {
                CodeTab.HTML -> state.copy(htmlCode = newCode, hasUnsavedChanges = true)
                CodeTab.CSS -> state.copy(cssCode = newCode, hasUnsavedChanges = true)
                CodeTab.JS -> state.copy(jsCode = newCode, hasUnsavedChanges = true)
            }
        }
    }

    fun clearSnackbar() {
        _studioState.update { it.copy(snackbarMessage = null) }
    }

    fun resetAiTaskState() {
        _studioState.update { it.copy(aiTaskState = AiTaskState.Idle) }
    }

    fun setTranscriptionMode(mode: TranscriptionMode) {
        _studioState.update { it.copy(transcriptionMode = mode) }
    }

    fun selectGalleryImage(imageId: String) {
        _studioState.update { state ->
            val found = state.galleryImages.firstOrNull { it.id == imageId } ?: return@update state
            state.copy(
                activeImageId = found.id,
                selectedBitmap = found.bitmap,
                sourceLabel = found.label,
            )
        }
    }

    fun removeGalleryImage(imageId: String) {
        _studioState.update { state ->
            val remaining = state.galleryImages.filterNot { it.id == imageId }
            val nextActive = if (state.activeImageId == imageId) remaining.firstOrNull() else remaining.firstOrNull { it.id == state.activeImageId } ?: remaining.firstOrNull()
            state.copy(
                galleryImages = remaining,
                activeImageId = nextActive?.id,
                selectedBitmap = nextActive?.bitmap,
                sourceLabel = nextActive?.label ?: "Aucune image",
                hotspots = if (remaining.isEmpty()) emptyList() else state.hotspots,
            )
        }
    }

    fun loadImagesFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val decodedItems = withContext(Dispatchers.IO) {
                    uris.take(4).mapIndexedNotNull { idx, uri ->
                        val bitmap = decodeScaledBitmap(context, uri, maxDimension = 1440) ?: return@mapIndexedNotNull null
                        val dataUri = encodeCompactDataUri(bitmap)
                        val label = resolveDisplayName(context, uri) ?: "Galerie #${idx + 1}"
                        GalleryImageItem(
                            id = UUID.randomUUID().toString(),
                            label = label,
                            bitmap = bitmap,
                            dataUri = dataUri,
                        )
                    }
                }
                if (decodedItems.isNotEmpty()) {
                    _studioState.update { state ->
                        val combined = (state.galleryImages + decodedItems).takeLast(4)
                        val active = decodedItems.first()
                        state.copy(
                            galleryImages = combined,
                            activeImageId = active.id,
                            selectedBitmap = active.bitmap,
                            sourceLabel = if (combined.size == 1) {
                                active.label
                            } else {
                                "${combined.size} images de la galerie"
                            },
                            hotspots = emptyList(),
                            aiTaskState = AiTaskState.Idle,
                            snackbarMessage = if (decodedItems.size == 1) {
                                "Image « ${active.label} » importée depuis votre galerie."
                            } else {
                                "${decodedItems.size} images importées depuis votre galerie."
                            },
                        )
                    }
                } else {
                    _studioState.update {
                        it.copy(snackbarMessage = "Impossible de décoder les images sélectionnées.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load gallery images", e)
                _studioState.update {
                    it.copy(snackbarMessage = e.localizedMessage ?: "Erreur lors de l'importation depuis la galerie.")
                }
            }
        }
    }

    fun loadSampleMockup(context: Context) {
        viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) {
                    val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.img_sample_mockup)
                        ?: return@withContext null
                    GalleryImageItem(
                        id = "sample_mockup",
                        label = "Maquette SaaS démo",
                        bitmap = bitmap,
                        dataUri = encodeCompactDataUri(bitmap),
                    )
                }
                if (item != null) {
                    val sampleHotspots = listOf(
                        ImageHotspot(
                            id = "spot_1",
                            xPercent = 0.72f,
                            yPercent = 0.62f,
                            label = "Bascule Tarifaire",
                            actionType = "Toggle Mensuel / Annuel",
                        ),
                        ImageHotspot(
                            id = "spot_2",
                            xPercent = 0.50f,
                            yPercent = 0.80f,
                            label = "Bouton Démarrer",
                            actionType = "Ouvrir modale de confirmation",
                        ),
                    )
                    _studioState.update {
                        it.copy(
                            galleryImages = listOf(item),
                            activeImageId = item.id,
                            selectedBitmap = item.bitmap,
                            sourceLabel = item.label,
                            hotspots = sampleHotspots,
                            aiTaskState = AiTaskState.Idle,
                            snackbarMessage = "Maquette démo chargée avec 2 zones interactives épinglées.",
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load sample mockup", e)
            }
        }
    }

    fun addHotspot(xPercent: Float, yPercent: Float, label: String, actionType: String) {
        val cleanLabel = label.trim().ifBlank { "Zone ${_studioState.value.hotspots.size + 1}" }
        val spot = ImageHotspot(
            id = UUID.randomUUID().toString(),
            xPercent = xPercent.coerceIn(0.05f, 0.95f),
            yPercent = yPercent.coerceIn(0.05f, 0.95f),
            label = cleanLabel,
            actionType = actionType.ifBlank { "Action au clic" },
        )
        _studioState.update { it.copy(hotspots = it.hotspots + spot) }
    }

    fun removeHotspot(id: String) {
        _studioState.update { state ->
            state.copy(hotspots = state.hotspots.filterNot { it.id == id })
        }
    }

    fun clearHotspots() {
        _studioState.update { it.copy(hotspots = emptyList()) }
    }

    fun transcribeImage() {
        val current = _studioState.value
        val bitmaps = if (current.galleryImages.isNotEmpty()) {
            current.galleryImages.map { it.bitmap }
        } else {
            listOfNotNull(current.selectedBitmap)
        }
        if (bitmaps.isEmpty()) {
            _studioState.update {
                it.copy(snackbarMessage = "Sélectionnez d'abord une image depuis votre galerie ou chargez la démo.")
            }
            return
        }

        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    aiTaskState = AiTaskState.Working(
                        if (bitmaps.size > 1) {
                            "Analyse de ${bitmaps.size} images & transcription HTML/CSS/JS..."
                        } else {
                            "Analyse visuelle & transcription HTML/CSS/JS..."
                        }
                    )
                )
            }
            try {
                val result = aiCodeRepository.transcribeImagesToPrototype(
                    bitmaps = bitmaps,
                    hotspots = current.hotspots,
                    transcriptionMode = current.transcriptionMode,
                    developerPrompt = current.developerPrompt,
                )
                applyGeneratedResult(result, statusMessage = "Code HTML, CSS et JS généré depuis votre galerie !")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image transcription failed", e)
                _studioState.update {
                    it.copy(
                        aiTaskState = AiTaskState.Error(
                            e.localizedMessage ?: "Échec de la transcription IA."
                        )
                    )
                }
            }
        }
    }

    fun applyJsSuggestion(suggestion: JsSuggestion) {
        generateInteractiveJs(
            instruction = "${suggestion.title} : ${suggestion.promptInstruction}",
            statusLabel = "Génération JS : ${suggestion.title}...",
        )
    }

    fun submitCustomJsPrompt() {
        val prompt = _studioState.value.customJsPrompt.trim()
        if (prompt.isEmpty()) {
            _studioState.update {
                it.copy(snackbarMessage = "Décrivez l'interaction JS souhaitée.")
            }
            return
        }
        generateInteractiveJs(
            instruction = prompt,
            statusLabel = "Génération du script JS interactif...",
            clearPrompt = true,
        )
    }

    private fun generateInteractiveJs(
        instruction: String,
        statusLabel: String,
        clearPrompt: Boolean = false,
    ) {
        val current = _studioState.value
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    aiTaskState = AiTaskState.Working(statusLabel),
                    customJsPrompt = if (clearPrompt) "" else it.customJsPrompt,
                )
            }
            try {
                val result = aiCodeRepository.generateInteractiveImageJs(
                    bitmap = current.selectedBitmap,
                    hotspots = current.hotspots,
                    currentHtml = current.htmlCode,
                    currentCss = current.cssCode,
                    currentJs = current.jsCode,
                    instruction = instruction,
                )
                applyGeneratedResult(result, statusMessage = "Interactivité JS intégrée ! Ouvrez Aperçu Live pour tester.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Interactive JS generation failed", e)
                _studioState.update {
                    it.copy(
                        aiTaskState = AiTaskState.Error(
                            e.localizedMessage ?: "Impossible de générer le code JS interactif."
                        )
                    )
                }
            }
        }
    }

    fun optimizeFrontendIntegration() {
        val current = _studioState.value
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    aiTaskState = AiTaskState.Working("Optimisation sémantique, responsive & JS...")
                )
            }
            try {
                val result = aiCodeRepository.optimizeFrontendIntegration(
                    currentHtml = current.htmlCode,
                    currentCss = current.cssCode,
                    currentJs = current.jsCode,
                )
                applyGeneratedResult(result, statusMessage = "Intégration front-end optimisée (WCAG, CSS & JS) !")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Frontend optimization failed", e)
                _studioState.update {
                    it.copy(
                        aiTaskState = AiTaskState.Error(
                            e.localizedMessage ?: "Échec de l'optimisation front-end."
                        )
                    )
                }
            }
        }
    }

    fun toggleAutoSave() {
        _studioState.update { state ->
            val next = !state.autoSaveAiResults
            state.copy(
                autoSaveAiResults = next,
                snackbarMessage = if (next) {
                    "Sauvegarde automatique Firestore activée pour les générations IA."
                } else {
                    "Sauvegarde manuelle uniquement."
                },
            )
        }
    }

    private suspend fun applyGeneratedResult(result: GeneratedPrototypeResult, statusMessage: String) {
        _studioState.update { state ->
            val resolvedHtml = resolveImagePlaceholders(result.htmlCode, state.galleryImages)
            val resolvedCss = resolveImagePlaceholders(result.cssCode, state.galleryImages)
            val resolvedJs = resolveImagePlaceholders(result.jsCode, state.galleryImages)
            state.copy(
                title = result.title.ifBlank { state.title },
                htmlCode = resolvedHtml.ifBlank { state.htmlCode },
                cssCode = resolvedCss.ifBlank { state.cssCode },
                jsCode = resolvedJs.ifBlank { state.jsCode },
                aiNotes = result.aiNotes.ifBlank { state.aiNotes },
                interactiveFeatures = if (result.interactiveFeatures.isNotEmpty()) {
                    result.interactiveFeatures
                } else {
                    state.interactiveFeatures
                },
                suggestions = if (result.jsSuggestions.isNotEmpty()) {
                    result.jsSuggestions
                } else {
                    state.suggestions
                },
                hasUnsavedChanges = true,
                consoleLogs = emptyList(),
                aiTaskState = AiTaskState.Success(statusMessage),
                snackbarMessage = statusMessage,
            )
        }
        if (_studioState.value.autoSaveAiResults) {
            persistCurrentState(saveAsNew = false, customMessage = "$statusMessage (Sauvegardé dans Firestore)")
        }
    }

    private fun resolveImagePlaceholders(code: String, images: List<GalleryImageItem>): String {
        if (code.isEmpty() || images.isEmpty()) return code
        var output = code
        images.forEachIndexed { idx, item ->
            output = output.replace("__PROTOSNAP_IMAGE_${idx}__", item.dataUri)
        }
        return output
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
                val maxSide = max(info.size.width, info.size.height)
                if (maxSide > maxDimension && maxSide > 0) {
                    val scale = maxDimension.toFloat() / maxSide.toFloat()
                    decoder.setTargetSize(
                        (info.size.width * scale).roundToInt().coerceAtLeast(1),
                        (info.size.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val maxSide = max(bounds.outWidth, bounds.outHeight)
            var sampleSize = 1
            while (maxSide / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    }

    private fun encodeCompactDataUri(source: Bitmap, maxDimension: Int = 640, quality: Int = 70): String {
        val maxSide = max(source.width, source.height)
        val scaled = if (maxSide > maxDimension && maxSide > 0) {
            val scale = maxDimension.toFloat() / maxSide.toFloat()
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.take(40) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun savePrototype(saveAsNew: Boolean = false) {
        viewModelScope.launch {
            persistCurrentState(saveAsNew = saveAsNew, customMessage = null)
        }
    }

    fun savePrototypeWithTitle(customTitle: String, saveAsNew: Boolean) {
        val cleanTitle = customTitle.trim().ifBlank { "Prototype sans titre" }.take(100)
        _studioState.update { it.copy(title = cleanTitle) }
        viewModelScope.launch {
            persistCurrentState(saveAsNew = saveAsNew, customMessage = "Prototype « $cleanTitle » enregistré dans Firestore.")
        }
    }

    private suspend fun persistCurrentState(saveAsNew: Boolean, customMessage: String?) {
        val current = _studioState.value
        _studioState.update { it.copy(isSaving = true) }
        val existingId = if (saveAsNew) null else current.activePrototypeId
        if (existingId == null) {
            val res = prototypeRepository.createPrototype(
                title = current.title,
                sourceLabel = current.sourceLabel,
                htmlCode = current.htmlCode,
                cssCode = current.cssCode,
                jsCode = current.jsCode,
                aiNotes = current.aiNotes,
                interactiveFeatures = current.interactiveFeatures,
            )
            res.fold(
                onSuccess = { newId ->
                    val msg = customMessage ?: "Snippet sauvegardé dans Firestore."
                    _studioState.update {
                        it.copy(
                            activePrototypeId = newId,
                            isSaving = false,
                            hasUnsavedChanges = false,
                            aiTaskState = if (it.aiTaskState is AiTaskState.Success) {
                                AiTaskState.Success(msg)
                            } else {
                                it.aiTaskState
                            },
                            snackbarMessage = msg,
                        )
                    }
                },
                onFailure = { err ->
                    _studioState.update {
                        it.copy(
                            isSaving = false,
                            snackbarMessage = err.localizedMessage ?: "Erreur lors de la sauvegarde Firestore.",
                        )
                    }
                }
            )
        } else {
            val res = prototypeRepository.updatePrototype(
                prototypeId = existingId,
                title = current.title,
                sourceLabel = current.sourceLabel,
                htmlCode = current.htmlCode,
                cssCode = current.cssCode,
                jsCode = current.jsCode,
                aiNotes = current.aiNotes,
                interactiveFeatures = current.interactiveFeatures,
            )
            res.fold(
                onSuccess = {
                    val msg = customMessage ?: "Prototype mis à jour dans Firestore."
                    _studioState.update {
                        it.copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                            aiTaskState = if (it.aiTaskState is AiTaskState.Success) {
                                AiTaskState.Success(msg)
                            } else {
                                it.aiTaskState
                            },
                            snackbarMessage = msg,
                        )
                    }
                },
                onFailure = { err ->
                    _studioState.update {
                        it.copy(
                            isSaving = false,
                            snackbarMessage = err.localizedMessage ?: "Erreur lors de la mise à jour Firestore.",
                        )
                    }
                }
            )
        }
    }

    fun duplicateProject(project: PrototypeProject) {
        viewModelScope.launch {
            val copyTitle = "${project.title} (copie)".take(100)
            val res = prototypeRepository.createPrototype(
                title = copyTitle,
                sourceLabel = project.sourceLabel,
                htmlCode = project.htmlCode,
                cssCode = project.cssCode,
                jsCode = project.jsCode,
                aiNotes = project.aiNotes,
                interactiveFeatures = project.interactiveFeatures,
                favorite = false,
            )
            res.fold(
                onSuccess = {
                    _studioState.update {
                        it.copy(snackbarMessage = "Copie « $copyTitle » créée dans Firestore.")
                    }
                },
                onFailure = { err ->
                    _studioState.update {
                        it.copy(snackbarMessage = err.localizedMessage ?: "Impossible de dupliquer le projet.")
                    }
                }
            )
        }
    }

    fun openProject(project: PrototypeProject, destination: StudioDestination = StudioDestination.PREVIEW) {
        _studioState.update { state ->
            state.copy(
                activePrototypeId = project.id,
                title = project.title,
                sourceLabel = project.sourceLabel,
                htmlCode = project.htmlCode,
                cssCode = project.cssCode,
                jsCode = project.jsCode,
                aiNotes = project.aiNotes,
                interactiveFeatures = project.interactiveFeatures,
                hasUnsavedChanges = false,
                consoleLogs = emptyList(),
                destination = destination,
                snackbarMessage = "Prototype « ${project.title} » restauré.",
            )
        }
    }

    fun startBlankStarter() {
        _studioState.update {
            StudioUiState(
                destination = StudioDestination.IMAGE_AI,
                snackbarMessage = "Nouveau brouillon prêt.",
            )
        }
    }

    fun toggleFavorite(project: PrototypeProject) {
        viewModelScope.launch {
            val res = prototypeRepository.toggleFavorite(project.id, !project.favorite)
            res.onFailure { err ->
                _studioState.update {
                    it.copy(snackbarMessage = err.localizedMessage ?: "Impossible de modifier le favori.")
                }
            }
        }
    }

    fun deleteProject(project: PrototypeProject) {
        viewModelScope.launch {
            val res = prototypeRepository.deletePrototype(project.id)
            res.fold(
                onSuccess = {
                    _studioState.update { state ->
                        val resetId = if (state.activePrototypeId == project.id) null else state.activePrototypeId
                        state.copy(
                            activePrototypeId = resetId,
                            snackbarMessage = "Projet supprimé.",
                        )
                    }
                },
                onFailure = { err ->
                    _studioState.update {
                        it.copy(snackbarMessage = err.localizedMessage ?: "Impossible de supprimer le projet.")
                    }
                }
            )
        }
    }

    fun buildStandaloneHtmlDocument(): String {
        val s = _studioState.value
        return buildStandaloneDocument(s.title, s.htmlCode, s.cssCode, s.jsCode)
    }

    companion object {
        fun buildStandaloneDocument(
            title: String,
            htmlBody: String,
            css: String,
            js: String,
        ): String {
            return """
<!DOCTYPE html>
<html lang="fr">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${title.replace("<", "&lt;").replace(">", "&gt;")}</title>
  <style>
$css
  </style>
</head>
<body>
$htmlBody
  <script>
try {
$js
} catch (err) {
  console.error('ProtoSnap JS Error:', err.message);
}
  </script>
</body>
</html>
            """.trimIndent()
        }
    }
}

private val DEFAULT_HTML = """
<main class="studio-page">
  <header class="hero-card">
    <span class="badge">ProtoSnap Live</span>
    <h1>Offre Studio Cloud</h1>
    <p>Modifiez le code HTML, CSS ou JS pour voir le rendu WebView se mettre à jour instantanément.</p>
    <div class="billing-toggle" role="group" aria-label="Période de facturation">
      <button type="button" class="toggle-btn is-active" data-period="monthly">Mensuel</button>
      <button type="button" class="toggle-btn" data-period="yearly">Annuel (-20%)</button>
    </div>
  </header>

  <section class="cards-grid">
    <article class="plan-card" data-plan="Starter">
      <h2>Starter</h2>
      <p class="price"><span data-price-monthly="19" data-price-yearly="15">19</span> €<small>/mois</small></p>
      <ul>
        <li>10 prototypes actifs</li>
        <li>Export HTML/CSS/JS</li>
      </ul>
      <button type="button" class="cta-btn">Choisir Starter</button>
    </article>

    <article class="plan-card is-featured" data-plan="Pro IA">
      <h2>Pro IA</h2>
      <p class="price"><span data-price-monthly="49" data-price-yearly="39">49</span> €<small>/mois</small></p>
      <ul>
        <li>Transcription illimitée</li>
        <li>Optimisation front-end IA</li>
      </ul>
      <button type="button" class="cta-btn primary">Démarrer Pro</button>
    </article>
  </section>

  <div id="toast" class="toast" aria-live="polite"></div>
</main>
""".trimIndent()

private val DEFAULT_CSS = """
:root {
  --bg: #0f172a;
  --surface: #1e293b;
  --border: #334155;
  --cyan: #22d3ee;
  --mint: #34d399;
  --text: #f8fafc;
  --muted: #94a3b8;
}

* { box-sizing: border-box; }

body {
  margin: 0;
  font-family: system-ui, -apple-system, sans-serif;
  background: radial-gradient(circle at top right, #164e63 0%, var(--bg) 55%);
  color: var(--text);
  padding: 20px;
  min-height: 100vh;
}

.studio-page {
  max-width: 780px;
  margin: 0 auto;
  display: grid;
  gap: 18px;
}

.hero-card {
  background: rgba(30, 41, 59, 0.85);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 20px;
}

.badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  background: rgba(34, 211, 238, 0.16);
  color: var(--cyan);
}

h1 { margin: 10px 0 6px; font-size: 1.5rem; }
p { margin: 0 0 14px; color: var(--muted); line-height: 1.5; }

.billing-toggle {
  display: inline-flex;
  background: var(--bg);
  padding: 4px;
  border-radius: 12px;
  border: 1px solid var(--border);
  gap: 4px;
}

.toggle-btn {
  border: 0;
  background: transparent;
  color: var(--muted);
  padding: 8px 14px;
  border-radius: 9px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
}

.toggle-btn.is-active {
  background: var(--cyan);
  color: #082f49;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.plan-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 18px;
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.plan-card.is-featured {
  border-color: var(--cyan);
  box-shadow: 0 10px 30px rgba(34, 211, 238, 0.12);
}

.plan-card.is-selected {
  border-color: var(--mint);
  transform: translateY(-2px);
}

.price {
  font-size: 1.8rem;
  font-weight: 800;
  color: var(--text);
  margin: 8px 0 12px;
}

.price small { font-size: 0.85rem; color: var(--muted); font-weight: 500; }

ul { padding-left: 18px; margin: 0 0 16px; color: var(--muted); line-height: 1.7; }

.cta-btn {
  width: 100%;
  padding: 11px 14px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: #0f172a;
  color: var(--text);
  font-weight: 700;
  cursor: pointer;
}

.cta-btn.primary {
  background: linear-gradient(135deg, var(--cyan), var(--mint));
  color: #042f2e;
  border: 0;
}

.toast {
  position: fixed;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%) translateY(16px);
  background: #064e3b;
  color: #d1fae5;
  border: 1px solid var(--mint);
  padding: 10px 16px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  opacity: 0;
  pointer-events: none;
  transition: all 0.25s ease;
}

.toast.visible {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}
""".trimIndent()

private val DEFAULT_JS = """
const toggleButtons = document.querySelectorAll('.toggle-btn');
const priceNodes = document.querySelectorAll('[data-price-monthly]');
const toast = document.getElementById('toast');

function showToast(message) {
  toast.textContent = message;
  toast.classList.add('visible');
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(() => toast.classList.remove('visible'), 2200);
}

toggleButtons.forEach((btn) => {
  btn.addEventListener('click', () => {
    toggleButtons.forEach((b) => b.classList.remove('is-active'));
    btn.classList.add('is-active');
    const period = btn.dataset.period;
    priceNodes.forEach((node) => {
      node.textContent = period === 'yearly'
        ? node.dataset.priceYearly
        : node.dataset.priceMonthly;
    });
    console.log('Période sélectionnée :', period);
    showToast(period === 'yearly' ? 'Tarif annuel (-20%) appliqué' : 'Tarif mensuel affiché');
  });
});

document.querySelectorAll('.plan-card .cta-btn').forEach((button) => {
  button.addEventListener('click', () => {
    document.querySelectorAll('.plan-card').forEach((c) => c.classList.remove('is-selected'));
    const card = button.closest('.plan-card');
    card.classList.add('is-selected');
    const plan = card.dataset.plan;
    console.log('Plan choisi :', plan);
    showToast('Plan ' + plan + ' sélectionné !');
  });
});
""".trimIndent()

private val DEFAULT_SUGGESTIONS = listOf(
    JsSuggestion(
        id = "sug_hotspot_overlay",
        title = "Carte interactive avec infobulles",
        description = "Transforme les zones repérées en points cliquables avec fiches animées.",
        category = "Image interactive",
        promptInstruction = "Ajoute un système de marqueurs interactifs avec infobulles animées au clic/survol et navigation clavier.",
    ),
    JsSuggestion(
        id = "sug_filter_search",
        title = "Filtre & recherche instantanée",
        description = "Ajoute une barre de recherche en direct et des filtres JS sans rechargement.",
        category = "Interactivité",
        promptInstruction = "Ajoute une barre de filtrage dynamique en temps réel qui filtre et anime les éléments affichés.",
    ),
    JsSuggestion(
        id = "sug_modal_form",
        title = "Modale de contact & validation JS",
        description = "Ouvre une modale accessible avec validation de formulaire en direct.",
        category = "Conversion",
        promptInstruction = "Ajoute une modale accessible déclenchée par le bouton principal avec validation JavaScript en direct et confirmation.",
    ),
)
