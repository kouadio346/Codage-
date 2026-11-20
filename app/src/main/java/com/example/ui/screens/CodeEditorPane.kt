package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CodeTab
import com.example.ui.StudioUiState
import com.example.ui.theme.ProtoSnapMonoFont
import kotlinx.coroutines.delay

@Composable
fun CodeEditorPane(
    state: StudioUiState,
    onSelectCodeTab: (CodeTab) -> Unit,
    onCodeChanged: (String) -> Unit,
    onSaveSnippet: () -> Unit,
    standaloneHtmlProvider: () -> String,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var currentCopiedNonce by remember { mutableIntStateOf(0) }
    var currentCopied by remember { mutableStateOf(false) }
    var bundleCopiedNonce by remember { mutableIntStateOf(0) }
    var bundleCopied by remember { mutableStateOf(false) }

    LaunchedEffect(currentCopiedNonce) {
        if (currentCopiedNonce > 0) {
            currentCopied = true
            delay(2000)
            currentCopied = false
        }
    }

    LaunchedEffect(bundleCopiedNonce) {
        if (bundleCopiedNonce > 0) {
            bundleCopied = true
            delay(2000)
            bundleCopied = false
        }
    }

    val currentCode = when (state.activeCodeTab) {
        CodeTab.HTML -> state.htmlCode
        CodeTab.CSS -> state.cssCode
        CodeTab.JS -> state.jsCode
    }
    val lineCount = currentCode.lines().size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Language Tabs + Copy/Share Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CodeTab.entries.forEach { tab ->
                    val selected = state.activeCodeTab == tab
                    val accent = when (tab) {
                        CodeTab.HTML -> MaterialTheme.colorScheme.primary
                        CodeTab.CSS -> MaterialTheme.colorScheme.tertiary
                        CodeTab.JS -> MaterialTheme.colorScheme.secondary
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectCodeTab(tab) },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.2f),
                            selectedLabelColor = accent,
                        ),
                        modifier = Modifier.testTag("code_tab_${tab.name.lowercase()}"),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        copyText(context, state.activeCodeTab.extension, currentCode)
                        currentCopiedNonce++
                        onNotify("${state.activeCodeTab.label} copié dans le presse-papiers.")
                    },
                    modifier = Modifier.testTag("copy_current_code_button"),
                ) {
                    Icon(
                        imageVector = if (currentCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copier ${state.activeCodeTab.label}",
                        tint = if (currentCopied) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = {
                        val bundle = standaloneHtmlProvider()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "${state.title} - index.html")
                            putExtra(Intent.EXTRA_TEXT, bundle)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Exporter le prototype HTML"))
                    },
                    modifier = Modifier.testTag("share_code_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.action_share_bundle),
                    )
                }
            }
        }

        // Quick developer snippet bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val snippets = when (state.activeCodeTab) {
                CodeTab.HTML -> listOf(
                    "+ Bouton" to "\n<button type=\"button\" class=\"cta-btn\">Action</button>",
                    "+ Carte" to "\n<article class=\"plan-card\">\n  <h2>Titre</h2>\n  <p>Description</p>\n</article>",
                )
                CodeTab.CSS -> listOf(
                    "+ Flex Row" to "\n.flex-row { display: flex; gap: 12px; align-items: center; }",
                    "+ Hover" to "\n.interactive:hover { transform: translateY(-2px); transition: 0.2s ease; }",
                )
                CodeTab.JS -> listOf(
                    "+ Click Listener" to "\ndocument.querySelectorAll('button').forEach((b) => b.addEventListener('click', () => console.log('Clic:', b.textContent)));",
                    "+ Toggle Class" to "\ndocument.body.classList.toggle('active');",
                )
            }
            snippets.forEach { (label, snippet) ->
                AssistChip(
                    onClick = { onCodeChanged(currentCode + snippet) },
                    label = { Text(label) },
                )
            }
            Spacer(Modifier.width(4.dp))
            FilledTonalButton(
                onClick = onSaveSnippet,
                enabled = !state.isSaving,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("editor_save_firestore_button"),
            ) {
                Icon(
                    imageVector = if (state.activePrototypeId != null && !state.hasUnsavedChanges) {
                        Icons.Rounded.CloudDone
                    } else {
                        Icons.Rounded.Save
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (state.activePrototypeId != null && !state.hasUnsavedChanges) "Sauvé" else "Sauver",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            OutlinedButton(
                onClick = {
                    copyText(context, "${state.title}.html", standaloneHtmlProvider())
                    bundleCopiedNonce++
                    onNotify("Bundle complet index.html copié !")
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("copy_full_bundle_button"),
            ) {
                Icon(
                    imageVector = if (bundleCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (bundleCopied) R.string.action_copied else R.string.action_copy_bundle),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // Monospace Editor Surface
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.activeCodeTab.extension,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "$lineCount lignes · Édition live",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp),
                ) {
                    BasicTextField(
                        value = currentCode,
                        onValueChange = onCodeChanged,
                        textStyle = TextStyle(
                            fontFamily = ProtoSnapMonoFont,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("code_editor_textfield"),
                    )
                }
            }
        }
    }
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
