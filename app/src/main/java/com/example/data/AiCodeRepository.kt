package com.example.data

import android.graphics.Bitmap
import android.util.Log
import com.example.model.ImageHotspot
import com.example.model.JsSuggestion
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "AiCodeRepository"
private const val AI_TIMEOUT_MS = 60_000L

data class GeneratedPrototypeResult(
    val title: String,
    val htmlCode: String,
    val cssCode: String,
    val jsCode: String,
    val aiNotes: String,
    val interactiveFeatures: List<String>,
    val jsSuggestions: List<JsSuggestion>,
)

class AiCodeRepository {

    private val suggestionItemSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Titre court en français de l'amélioration JS proposée"),
            "description" to Schema.string(description = "Explication concise du gain UX ou front-end"),
            "category" to Schema.string(description = "Catégorie courte: Interactivité, Animation, Accessibilité, Performance"),
            "promptInstruction" to Schema.string(description = "Instruction précise pour implémenter cette fonctionnalité en JS/CSS/HTML"),
        )
    )

    private val prototypeSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Titre concis du prototype en français"),
            "htmlCode" to Schema.string(description = "Code HTML5 sémantique propre (contenu du body uniquement, sans balises html/head/body)"),
            "cssCode" to Schema.string(description = "Feuille de style CSS3 moderne responsive avec variables CSS"),
            "jsCode" to Schema.string(description = "Code JavaScript ES6+ propre, interactif et sans dépendance externe"),
            "aiNotes" to Schema.string(description = "Résumé technique en français des choix d'intégration front-end et d'interactivité"),
            "interactiveFeatures" to Schema.array(
                items = Schema.string(),
                description = "Liste de 2 à 5 fonctionnalités interactives implémentées"
            ),
            "jsSuggestions" to Schema.array(
                items = suggestionItemSchema,
                description = "3 à 4 propositions d'améliorations JavaScript interactives adaptées à l'image ou au code"
            ),
        )
    )

    private val prototypeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = prototypeSchema
            }
        )
    }

    suspend fun transcribeImagesToPrototype(
        bitmaps: List<Bitmap>,
        hotspots: List<ImageHotspot>,
        transcriptionMode: com.example.model.TranscriptionMode,
        developerPrompt: String,
    ): GeneratedPrototypeResult {
        require(bitmaps.isNotEmpty()) { "Au moins une image est requise." }
        val scaledBitmaps = bitmaps.take(4).map { scaleBitmapForAi(it) }
        val hotspotInstructions = if (hotspots.isEmpty()) {
            "Aucune zone manuelle épinglée : détecte automatiquement tous les éléments visuels interactifs (boutons, cartes, onglets, modales, filtres, graphiques ou zones clés de l'image)."
        } else {
            buildString {
                appendLine("L'utilisateur a marqué des zones interactives précises sur l'image :")
                hotspots.forEachIndexed { idx, spot ->
                    appendLine(
                        "${idx + 1}. Position (x=${(spot.xPercent * 100).roundToInt()}%, y=${(spot.yPercent * 100).roundToInt()}%) -> Label: \"${spot.label}\" | Comportement souhaité: ${spot.actionType}"
                    )
                }
                appendLine("Assure-toi que chacune de ces zones soit fidèlement traduite en HTML interactif avec un script JS fonctionnel.")
            }
        }

        val modeRules = when (transcriptionMode) {
            com.example.model.TranscriptionMode.REBUILD_UI -> buildString {
                appendLine("Mode choisi : RECONSTRUCTION UI COMPLÈTE.")
                appendLine("- Analyse l'image (ou les ${scaledBitmaps.size} images fournies) et reconstruis entièrement la mise en page, les couleurs, les cartes, la typographie et les contrôles en HTML5/CSS3/JS pur sans dépendre de l'image brute.")
            }
            com.example.model.TranscriptionMode.INTERACTIVE_IMAGE -> buildString {
                appendLine("Mode choisi : IMAGE INTERACTIVE INTÉGRÉE.")
                appendLine("- Intègre directement l'image sélectionnée dans le HTML via <img src=\"__PROTOSNAP_IMAGE_0__\" alt=\"Visuel interactif\" class=\"interactive-stage-img\" /> (et __PROTOSNAP_IMAGE_1__, etc. si plusieurs images sont fournies).")
                appendLine("- Superpose en CSS (position: relative / position: absolute en %) des points d'intérêt interactifs (hotspots) aux coordonnées exactes, avec panneau de détail dynamique, infobulles animées, zoom/lightbox et navigation clavier en JavaScript.")
            }
        }

        val promptText = buildString {
            appendLine("Tu es un architecte front-end senior expert en prototypage rapide.")
            appendLine("Analyse ces images issues de la galerie du développeur et transcris-les en code HTML5, CSS3 et JavaScript vanilla prêt pour la production.")
            appendLine()
            appendLine(modeRules)
            appendLine()
            appendLine("Règles strictes :")
            appendLine("1. htmlCode : retourne uniquement le balisage sémantique à placer à l'intérieur de <body> (pas de <!DOCTYPE>, <html>, <head> ni <script>/<style> inline).")
            appendLine("2. cssCode : CSS3 moderne autonome (variables :root, Flexbox/Grid, design responsive mobile-first, micro-transitions fluides, aucun CDN externe requis).")
            appendLine("3. jsCode : JavaScript vanilla ES6+ propre avec gestionnaires d'événements réels et retours visuels immédiats (modales, onglets, filtres, compteurs, infobulles, console.log explicites pour le debug).")
            appendLine("4. Propose 3 à 4 suggestions d'interactivité JS complémentaires dans jsSuggestions.")
            appendLine()
            appendLine(hotspotInstructions)
            if (developerPrompt.isNotBlank()) {
                appendLine("Instructions supplémentaires du développeur : $developerPrompt")
            }
        }

        return executeStructuredPrompt(
            promptText = promptText,
            bitmaps = scaledBitmaps,
            timeoutMessage = "La transcription de l'image a expiré après 60 secondes. Réessayez avec une image plus simple.",
        )
    }

    suspend fun generateInteractiveImageJs(
        bitmap: Bitmap?,
        hotspots: List<ImageHotspot>,
        currentHtml: String,
        currentCss: String,
        currentJs: String,
        instruction: String,
    ): GeneratedPrototypeResult {
        val scaledBitmap = bitmap?.let { scaleBitmapForAi(it) }
        val hotspotContext = if (hotspots.isNotEmpty()) {
            hotspots.joinToString("\n") { spot ->
                "- Hotspot \"${spot.label}\" à (${(spot.xPercent * 100).roundToInt()}%, ${(spot.yPercent * 100).roundToInt()}%) : ${spot.actionType}"
            }
        } else {
            "Aucun hotspot manuel supplémentaire."
        }

        val promptText = buildString {
            appendLine("Tu es un expert JavaScript & UI interactive.")
            appendLine("Enrichis et améliore le prototype ci-dessous en générant du code JavaScript interactif (et les ajustements HTML/CSS nécessaires) pour répondre exactement à la demande.")
            appendLine()
            appendLine("Demande d'interactivité : $instruction")
            appendLine("Zones interactives épinglées sur l'image :")
            appendLine(hotspotContext)
            appendLine()
            appendLine("Code actuel :")
            appendLine("--- HTML ---")
            appendLine(currentHtml.take(18000))
            appendLine("--- CSS ---")
            appendLine(currentCss.take(18000))
            appendLine("--- JS ---")
            appendLine(currentJs.take(18000))
            appendLine()
            appendLine("Contraintes : Code autonome sans bibliothèque externe, accessible (aria-label, clavier), fluide sur mobile et desktop. Retourne le code complet mis à jour et 3 nouvelles propositions JS.")
        }

        return executeStructuredPrompt(
            promptText = promptText,
            bitmaps = listOfNotNull(scaledBitmap),
            timeoutMessage = "La génération du code JS interactif a expiré après 60 secondes.",
        )
    }

    suspend fun optimizeFrontendIntegration(
        currentHtml: String,
        currentCss: String,
        currentJs: String,
    ): GeneratedPrototypeResult {
        val promptText = buildString {
            appendLine("Tu es un lead développeur front-end spécialisé en performance web, accessibilité (WCAG) et clean architecture.")
            appendLine("Optimise l'intégration front-end de ce prototype HTML, CSS et JS sans perdre aucune fonctionnalité existante :")
            appendLine("- HTML5 sémantique, attributs ARIA, labels accessibles et structure propre.")
            appendLine("- CSS maintenable avec variables :root, clamp(), Flexbox/Grid responsive, états :hover/:focus-visible/:active.")
            appendLine("- JS optimisé avec délégation d'événements, gestion d'état claire, zéro fuite mémoire, et commentaires utiles en français.")
            appendLine("- Dans aiNotes, résume clairement les optimisations front-end réalisées.")
            appendLine()
            appendLine("--- HTML ---")
            appendLine(currentHtml.take(20000))
            appendLine("--- CSS ---")
            appendLine(currentCss.take(20000))
            appendLine("--- JS ---")
            appendLine(currentJs.take(20000))
        }

        return executeStructuredPrompt(
            promptText = promptText,
            bitmaps = emptyList(),
            timeoutMessage = "L'optimisation front-end a expiré après 60 secondes.",
        )
    }

    private suspend fun executeStructuredPrompt(
        promptText: String,
        bitmaps: List<Bitmap>,
        timeoutMessage: String,
    ): GeneratedPrototypeResult {
        try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                if (bitmaps.isNotEmpty()) {
                    prototypeModel.generateContent(
                        content {
                            bitmaps.forEach { bmp -> image(bmp) }
                            text(promptText)
                        }
                    )
                } else {
                    prototypeModel.generateContent(promptText)
                }
            }
            val jsonString = response.text
                ?: throw IllegalStateException("Le modèle IA a renvoyé une réponse vide.")
            return parsePrototypeJson(jsonString)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Firebase AI request timed out", e)
            throw IllegalStateException(timeoutMessage, e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI request failed", e)
            throw e
        }
    }

    private fun parsePrototypeJson(jsonString: String): GeneratedPrototypeResult {
        val json = JSONObject(jsonString)
        val title = json.optString("title", "Prototype IA").ifBlank { "Prototype IA" }
        val htmlCode = json.optString("htmlCode", "")
        val cssCode = json.optString("cssCode", "")
        val jsCode = json.optString("jsCode", "")
        val aiNotes = json.optString("aiNotes", "")

        val featuresJson = json.optJSONArray("interactiveFeatures")
        val features = buildList {
            if (featuresJson != null) {
                for (i in 0 until featuresJson.length()) {
                    val value = featuresJson.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }

        val suggestionsJson = json.optJSONArray("jsSuggestions")
        val suggestions = buildList {
            if (suggestionsJson != null) {
                for (i in 0 until suggestionsJson.length()) {
                    val obj = suggestionsJson.optJSONObject(i) ?: continue
                    val sTitle = obj.optString("title").trim()
                    val sDesc = obj.optString("description").trim()
                    val sCat = obj.optString("category", "Interactivité").trim()
                    val sPrompt = obj.optString("promptInstruction").trim()
                    if (sTitle.isNotEmpty() && sPrompt.isNotEmpty()) {
                        add(
                            JsSuggestion(
                                id = "sug_${i}_${sTitle.hashCode()}",
                                title = sTitle,
                                description = sDesc,
                                category = sCat.ifBlank { "Interactivité" },
                                promptInstruction = sPrompt,
                            )
                        )
                    }
                }
            }
        }

        return GeneratedPrototypeResult(
            title = title,
            htmlCode = htmlCode,
            cssCode = cssCode,
            jsCode = jsCode,
            aiNotes = aiNotes,
            interactiveFeatures = features,
            jsSuggestions = suggestions,
        )
    }

    private fun scaleBitmapForAi(source: Bitmap, maxDimension: Int = 1440): Bitmap {
        val maxSide = max(source.width, source.height)
        if (maxSide <= maxDimension) return source
        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val targetWidth = (source.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
