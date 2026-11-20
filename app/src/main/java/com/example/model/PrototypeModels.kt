package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class PrototypeProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val sourceLabel: String = "",
    val htmlCode: String = "",
    val cssCode: String = "",
    val jsCode: String = "",
    val aiNotes: String = "",
    val interactiveFeatures: List<String> = emptyList(),
    val favorite: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class ImageHotspot(
    val id: String,
    val xPercent: Float,
    val yPercent: Float,
    val label: String,
    val actionType: String = "Infobulle & Clic",
)

data class JsSuggestion(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val promptInstruction: String,
)

data class ConsoleEntry(
    val level: String,
    val message: String,
    val timestamp: String,
)

enum class StudioDestination(val label: String) {
    IMAGE_AI("Image & IA"),
    CODE("Code"),
    PREVIEW("Aperçu Live"),
    PROJECTS("Projets"),
}

enum class CodeTab(val label: String, val extension: String) {
    HTML("HTML", "index.html"),
    CSS("CSS", "styles.css"),
    JS("JS", "app.js"),
}

enum class ViewportMode(val label: String, val widthPx: Int?) {
    MOBILE("Mobile", 375),
    TABLET("Tablette", 768),
    FULL("Fluide", null),
}

data class GalleryImageItem(
    val id: String,
    val label: String,
    val bitmap: android.graphics.Bitmap,
    val dataUri: String,
)

enum class TranscriptionMode(val label: String, val subtitle: String) {
    REBUILD_UI(
        label = "UI → HTML/CSS/JS",
        subtitle = "Extrait et reconstruit toute l'interface en code pur",
    ),
    INTERACTIVE_IMAGE(
        label = "Image interactive JS",
        subtitle = "Intègre l'image avec zones cliquables et infobulles JS",
    ),
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data class Working(val statusMessage: String) : AiTaskState
    data class Success(val summary: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}
