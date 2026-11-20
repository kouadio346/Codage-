package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.model.ViewportMode
import com.example.ui.ProtoSnapViewModel
import com.example.ui.StudioUiState
import com.example.ui.theme.ProtoSnapMonoFont
import kotlinx.coroutines.delay

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LivePreviewPane(
    state: StudioUiState,
    onSelectViewport: (ViewportMode) -> Unit,
    onToggleConsole: () -> Unit,
    onClearConsole: () -> Unit,
    onConsoleLog: (String, String) -> Unit,
    standaloneHtmlProvider: () -> String,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var reloadNonce by remember { mutableIntStateOf(0) }
    var copiedNonce by remember { mutableIntStateOf(0) }
    var isCopied by remember { mutableStateOf(false) }
    var showCopyMenu by remember { mutableStateOf(false) }

    LaunchedEffect(copiedNonce) {
        if (copiedNonce > 0) {
            isCopied = true
            delay(2000)
            isCopied = false
        }
    }

    var debouncedHtml by remember {
        mutableStateOf(
            ProtoSnapViewModel.buildStandaloneDocument(
                state.title,
                state.htmlCode,
                state.cssCode,
                state.jsCode,
            )
        )
    }

    LaunchedEffect(state.title, state.htmlCode, state.cssCode, state.jsCode, reloadNonce) {
        delay(200)
        debouncedHtml = ProtoSnapViewModel.buildStandaloneDocument(
            state.title,
            state.htmlCode,
            state.cssCode,
            state.jsCode,
        )
    }

    fun copyToClipboard(label: String, content: String, feedbackMessage: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, content))
        copiedNonce++
        onNotify(feedbackMessage)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Preview Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViewportMode.entries.forEach { mode ->
                val icon = when (mode) {
                    ViewportMode.MOBILE -> Icons.Rounded.PhoneAndroid
                    ViewportMode.TABLET -> Icons.Rounded.TabletAndroid
                    ViewportMode.FULL -> Icons.Rounded.DesktopWindows
                }
                FilterChip(
                    selected = state.viewportMode == mode,
                    onClick = { onSelectViewport(mode) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = mode.label,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    label = { Text(mode.label) },
                    modifier = Modifier.testTag("viewport_${mode.name.lowercase()}"),
                )
            }

            FilterChip(
                selected = state.isConsoleVisible,
                onClick = onToggleConsole,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = "Console JS",
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = {
                    Text(
                        if (state.consoleLogs.isEmpty()) "JS" else "JS (${state.consoleLogs.size})"
                    )
                },
                modifier = Modifier.testTag("toggle_console_button"),
            )

            IconButton(
                onClick = { reloadNonce++ },
                modifier = Modifier.testTag("reload_preview_button"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Recharger la prévisualisation",
                )
            }
        }

        // Real-Time WebView Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val frameModifier = when (val widthDp = state.viewportMode.widthPx) {
                null -> Modifier.fillMaxSize()
                else -> Modifier
                    .widthIn(max = widthDp.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                color = MaterialTheme.colorScheme.surface,
                modifier = frameModifier,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Simulated Mini Browser Header with Copy to Clipboard action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                            .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                        Text(
                            text = "LIVE · ${state.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = {
                                    copyToClipboard(
                                        label = "${state.title}.html",
                                        content = standaloneHtmlProvider(),
                                        feedbackMessage = "Code complet (HTML/CSS/JS) copié dans le presse-papiers !",
                                    )
                                },
                                colors = if (isCopied) {
                                    ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                } else {
                                    ButtonDefaults.filledTonalButtonColors()
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("preview_copy_code_button"),
                            ) {
                                AnimatedContent(
                                    targetState = isCopied,
                                    label = "preview_copy_state",
                                ) { copied ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                            contentDescription = stringResource(
                                                if (copied) R.string.action_copied else R.string.action_copy_code
                                            ),
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = stringResource(
                                                if (copied) R.string.action_copied else R.string.action_copy_code
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }

                            Box {
                                IconButton(
                                    onClick = { showCopyMenu = true },
                                    modifier = Modifier.testTag("preview_copy_options_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Options de copie du code",
                                    )
                                }
                                DropdownMenu(
                                    expanded = showCopyMenu,
                                    onDismissRequest = { showCopyMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Copier tout (index.html)") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                                        },
                                        onClick = {
                                            showCopyMenu = false
                                            copyToClipboard(
                                                label = "${state.title}.html",
                                                content = standaloneHtmlProvider(),
                                                feedbackMessage = "Bundle complet index.html copié dans le presse-papiers !",
                                            )
                                        },
                                        modifier = Modifier.testTag("copy_option_bundle"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copier HTML uniquement") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Code, contentDescription = null)
                                        },
                                        onClick = {
                                            showCopyMenu = false
                                            copyToClipboard(
                                                label = "index.html",
                                                content = state.htmlCode,
                                                feedbackMessage = "Code HTML copié dans le presse-papiers !",
                                            )
                                        },
                                        modifier = Modifier.testTag("copy_option_html"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copier CSS uniquement") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Code, contentDescription = null)
                                        },
                                        onClick = {
                                            showCopyMenu = false
                                            copyToClipboard(
                                                label = "styles.css",
                                                content = state.cssCode,
                                                feedbackMessage = "Code CSS copié dans le presse-papiers !",
                                            )
                                        },
                                        modifier = Modifier.testTag("copy_option_css"),
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Copier JS uniquement") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Code, contentDescription = null)
                                        },
                                        onClick = {
                                            showCopyMenu = false
                                            copyToClipboard(
                                                label = "app.js",
                                                content = state.jsCode,
                                                feedbackMessage = "Code JavaScript copié dans le presse-papiers !",
                                            )
                                        },
                                        modifier = Modifier.testTag("copy_option_js"),
                                    )
                                }
                            }
                        }
                    }

                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webViewClient = WebViewClient()
                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        if (consoleMessage != null) {
                                            val level = when (consoleMessage.messageLevel()) {
                                                ConsoleMessage.MessageLevel.ERROR -> "ERR"
                                                ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                                else -> "LOG"
                                            }
                                            onConsoleLog(level, consoleMessage.message())
                                        }
                                        return true
                                    }
                                }
                                loadDataWithBaseURL(
                                    "https://protosnap.local/",
                                    debouncedHtml,
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                                tag = "${debouncedHtml.hashCode()}_$reloadNonce"
                            }
                        },
                        update = { webView ->
                            val signature = "${debouncedHtml.hashCode()}_$reloadNonce"
                            if (webView.tag != signature) {
                                webView.tag = signature
                                webView.loadDataWithBaseURL(
                                    "https://protosnap.local/",
                                    debouncedHtml,
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("live_preview_webview"),
                    )
                }
            }
        }

        // Collapsible JS Console Inspector
        AnimatedVisibility(visible = state.isConsoleVisible) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Console JavaScript",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        IconButton(
                            onClick = onClearConsole,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Vider la console",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    if (state.consoleLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Aucun log JS pour le moment. Interagissez avec la page !",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(state.consoleLogs) { entry ->
                                val color = when (entry.level) {
                                    "ERR" -> MaterialTheme.colorScheme.error
                                    "WARN" -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "[${entry.timestamp}] ${entry.level}",
                                        fontFamily = ProtoSnapMonoFont,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = color,
                                    )
                                    Text(
                                        text = entry.message,
                                        fontFamily = ProtoSnapMonoFont,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
