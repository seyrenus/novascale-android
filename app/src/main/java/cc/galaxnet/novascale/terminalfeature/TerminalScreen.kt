/*
 * NovaScale for Android
 * Copyright (C) 2026 NovaScale contributors
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package cc.galaxnet.novascale.terminalfeature

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.galaxnet.novascale.R
import cc.galaxnet.novascale.core.TailnetState
import cc.galaxnet.novascale.core.TailnetPeer
import cc.galaxnet.novascale.remote.HostKeyConfirmationDialog
import cc.galaxnet.novascale.remote.RemoteConnectionConfig
import cc.galaxnet.novascale.terminal.TerminalCanvasView
import cc.galaxnet.novascale.ui.NovaSpacing
import cc.galaxnet.novascale.ui.NovaIcons
import cc.galaxnet.novascale.ui.NovaTheme
import cc.galaxnet.novascale.ui.asString
import cc.galaxnet.novascale.ui.uiText
import java.util.UUID

private data class TerminalChromeColors(
    val background: Color,
    val terminalBackground: Color,
    val foreground: Color,
    val secondary: Color,
    val border: Color,
    val accent: Color,
    val connected: Color,
    val destructive: Color,
)

private fun TerminalThemePalette.chromeColors(): TerminalChromeColors {
    val foreground = Color(foreground)
    return TerminalChromeColors(
        background = Color(adjustTerminalChromeBackground(background)),
        terminalBackground = Color(background),
        foreground = foreground,
        secondary = foreground.copy(alpha = 0.60f),
        border = foreground.copy(alpha = 0.15f),
        accent = Color(ansi[4]),
        connected = Color(ansi[2]),
        destructive = Color(ansi[1]),
    )
}

@Composable
internal fun TerminalScreen(
    terminalSessions: TerminalSessionRegistry,
    sessionKey: String,
    hostId: String,
    peer: TailnetPeer,
    tailnetState: TailnetState,
    initialConfig: RemoteConnectionConfig,
    terminalSettings: TerminalSettings,
    terminalTheme: TerminalThemePalette,
    onTerminalFontSize: (Float) -> Unit,
    onExit: () -> Unit,
    onOpenNewWindow: () -> Unit,
    onEditSshSettings: () -> Unit,
) {
    val chrome = remember(terminalTheme) { terminalTheme.chromeColors() }
    val group = remember(terminalSessions, sessionKey) {
        terminalSessions.getOrCreate(
            key = sessionKey,
            hostId = hostId,
            peer = peer,
            config = initialConfig,
        )
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hostDisplayName = peer.displayName
    val tabs by group.tabs.collectAsStateWithLifecycle()
    val selectedTabId by group.selectedTabId.collectAsStateWithLifecycle()
    val tab = tabs.firstOrNull { it.id == selectedTabId } ?: tabs.first()
    val controller = tab.runtime
    val state by controller.state.collectAsStateWithLifecycle()
    val viewAttachmentId = remember(group) { UUID.randomUUID().toString() }
    var terminalView by remember(selectedTabId) { mutableStateOf<TerminalCanvasView?>(null) }
    LaunchedEffect(controller) {
        controller.clipboardWrites.collect { text ->
            // Hidden/background sessions must not overwrite the device clipboard.
            terminalView?.takeIf { it.hasWindowFocus() && it.isShown && it.isFocused }?.copyRemoteText(text)
        }
    }
    var lastAutoInputTarget by remember(group) {
        mutableStateOf<Pair<Int, TerminalCanvasView>?>(null)
    }
    var terminalInputCollapsed by remember(selectedTabId) { mutableStateOf(false) }
    var controlActive by remember(selectedTabId) { mutableStateOf(false) }
    var altActive by remember(selectedTabId) { mutableStateOf(false) }
    var closeSessionRequested by remember { mutableStateOf(false) }
    var tabActions by remember { mutableStateOf<TerminalSessionTab?>(null) }
    var tabPendingClose by remember { mutableStateOf<TerminalSessionTab?>(null) }
    var tabPendingRename by remember { mutableStateOf<TerminalSessionTab?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var composerOpen by remember(selectedTabId) { mutableStateOf(false) }
    var composerText by remember(selectedTabId) { mutableStateOf(TextFieldValue()) }
    val addTerminalTab: () -> Unit = {
        group.addTab(connectImmediately = tailnetState is TailnetState.Running)
    }
    val resignTerminalInput: () -> Unit = {
        terminalView?.releaseTerminalInput()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    val detachTerminal: () -> Unit = {
        resignTerminalInput()
        onExit()
    }
    val closeComposer: () -> Unit = {
        composerOpen = false
        keyboardController?.hide()
        terminalView?.let { view ->
            view.post { view.requestTerminalInput(showSoftwareKeyboard = false) }
        }
    }
    val submitComposer: () -> Unit = {
        if (composerText.text.isNotEmpty() && state.connected) {
            controller.send(terminalComposedInputSequence(composerText.text, state.snapshot?.mouseFlags ?: 0))
            composerText = TextFieldValue()
            closeComposer()
        }
    }

    TerminalSystemBars(chrome)

    BackHandler(enabled = !composerOpen, onBack = detachTerminal)

    DisposableEffect(group) {
        val attachment = group.attachView(viewAttachmentId)
        onDispose(attachment::close)
    }

    LaunchedEffect(group, selectedTabId) {
        group.activateView(viewAttachmentId)
    }

    LaunchedEffect(controller, terminalTheme.id) {
        controller.applyTheme(terminalTheme.toNativeTheme())
    }

    LaunchedEffect(tailnetState, group) {
        if (tailnetState is TailnetState.Running && initialConfig.validate() == null) {
            group.ensureConnected()
        }
    }

    LaunchedEffect(
        selectedTabId,
        terminalView,
        state.connected,
        state.snapshot != null,
    ) {
        val view = terminalView ?: return@LaunchedEffect
        val target = selectedTabId to view
        if (
            state.connected &&
            state.snapshot != null &&
            !terminalInputCollapsed &&
            lastAutoInputTarget != target
        ) {
            lastAutoInputTarget = target
            view.requestTerminalInput()
        }
    }

    state.hostKeyPrompt?.let { prompt ->
        HostKeyConfirmationDialog(
            prompt = prompt,
            endpoint = runCatching { tab.config.endpoint }.getOrDefault(tab.config.host),
            onTrust = controller::acceptHostKey,
            onReject = controller::rejectHostKey,
        )
    }
    if (closeSessionRequested) {
        AlertDialog(
            onDismissRequest = { closeSessionRequested = false },
            title = { Text(stringResource(R.string.close_terminal_title)) },
            text = {
                Text(
                    if (tabs.size > 1) {
                        stringResource(R.string.close_terminal_multiple_description, tabs.size)
                    } else {
                        stringResource(R.string.close_terminal_single_description)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        closeSessionRequested = false
                        resignTerminalInput()
                        onExit()
                        group.closeSession()
                    },
                ) {
                    Text(stringResource(R.string.action_close), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { closeSessionRequested = false }) {
                    Text(stringResource(R.string.keep_session))
                }
            },
        )
    }
    tabActions?.let { selected ->
        val selectedTitle = selected.title.asString()
        AlertDialog(
            onDismissRequest = { tabActions = null },
            title = { Text(selectedTitle) },
            text = {
                Column {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            renameValue = selectedTitle
                            tabPendingRename = selected
                            tabActions = null
                        },
                    ) { Text(stringResource(R.string.action_rename)) }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            tabPendingClose = selected
                            tabActions = null
                        },
                    ) {
                        Text(stringResource(R.string.close_tab), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tabActions = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    tabPendingRename?.let { selected ->
        AlertDialog(
            onDismissRequest = { tabPendingRename = null },
            title = { Text(stringResource(R.string.rename_tab)) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(TerminalSessionGroup.MAX_TAB_TITLE_LENGTH) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.tab_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (group.renameTab(selected.id, renameValue)) {
                            tabPendingRename = null
                        }
                    },
                    enabled = renameValue.isNotBlank(),
                ) { Text(stringResource(R.string.action_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { tabPendingRename = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    tabPendingClose?.let { selected ->
        val selectedTitle = selected.title.asString()
        AlertDialog(
            onDismissRequest = { tabPendingClose = null },
            title = {
                Text(stringResource(R.string.close_named_tab_title, selectedTitle))
            },
            text = { Text(stringResource(R.string.close_tab_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        tabPendingClose = null
                        if (group.closeTab(selected.id)) detachTerminal()
                    },
                ) {
                    Text(stringResource(R.string.close_tab), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { tabPendingClose = null }) {
                    Text(stringResource(R.string.keep_tab))
                }
            },
        )
    }
    if (composerOpen) {
        AlertDialog(
            onDismissRequest = closeComposer,
            title = { Text(stringResource(R.string.terminal_composer_title)) },
            text = {
                val composerFocus = remember { FocusRequester() }
                val composerKeyboard = LocalSoftwareKeyboardController.current
                LaunchedEffect(composerFocus) {
                    composerFocus.requestFocus()
                    composerKeyboard?.show()
                }
                OutlinedTextField(
                    value = composerText,
                    onValueChange = { composerText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(composerFocus)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter && event.isCtrlPressed) {
                                submitComposer()
                                true
                            } else {
                                false
                            }
                        },
                    minLines = 4,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                )
            },
            confirmButton = {
                TextButton(onClick = submitComposer, enabled = composerText.text.isNotEmpty() && state.connected) {
                    Text(stringResource(R.string.terminal_composer_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { composerText = composerText.withNewlineAtSelection() }) {
                    Text(stringResource(R.string.terminal_composer_newline))
                }
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        TerminalSessionHeader(
            colors = chrome,
            hostDisplayName = hostDisplayName,
            status = state.status.asString(),
            connected = state.connected,
            showAddTab = tabs.size == 1,
            onExit = detachTerminal,
            onAddTab = addTerminalTab,
            onOpenNewWindow = onOpenNewWindow,
            onDisconnect = { closeSessionRequested = true },
        )
        if (shouldShowTerminalTabBar(tabs.size)) {
            TerminalTabBar(
                colors = chrome,
                tabs = tabs,
                selectedTabId = selectedTabId,
                onSelect = group::selectTab,
                onAdd = addTerminalTab,
                onActions = { tabActions = it },
            )
        }

        if (state.connected && state.snapshot != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AndroidView(
                    factory = { viewContext ->
                        TerminalCanvasView(viewContext).apply {
                            setOnFocusChangeListener { _, focused ->
                                if (focused) {
                                    terminalInputCollapsed = false
                                    group.activateView(viewAttachmentId)
                                }
                            }
                            setWindowFocusGainedListener {
                                group.activateView(viewAttachmentId)
                            }
                            terminalView = this
                            applyAppearance(
                                token = terminalAppearanceToken(terminalSettings, terminalTheme),
                                typeface = terminalSettings.font.typeface(viewContext),
                                textSizeSp = terminalSettings.fontSizeSp,
                                cursorArgb = terminalTheme.cursor,
                                cursorShape = terminalSettings.cursorShape.shapeOverride,
                                selectionArgb = terminalTheme.selection,
                                selectedTextArgb = terminalTheme.selectedText,
                            )
                            setTextSizeChangedListener(onTerminalFontSize)
                            setOneShotModifiers(controlActive, altActive)
                            setTerminalCallbacks(
                                onInput = controller::send,
                                onResize = { columns, rows ->
                                    group.resizeFromView(tab.id, viewAttachmentId, columns, rows)
                                },
                                onScroll = controller::scroll,
                                onModifiersConsumed = {
                                    controlActive = false
                                    altActive = false
                                },
                            )
                            state.snapshot?.let(::submitSnapshot)
                        }
                    },
                    update = { view ->
                        terminalView = view
                        view.setOnFocusChangeListener { _, focused ->
                            if (focused) {
                                terminalInputCollapsed = false
                                group.activateView(viewAttachmentId)
                            }
                        }
                        view.applyAppearance(
                            token = terminalAppearanceToken(terminalSettings, terminalTheme),
                            typeface = terminalSettings.font.typeface(view.context),
                            textSizeSp = terminalSettings.fontSizeSp,
                            cursorArgb = terminalTheme.cursor,
                            cursorShape = terminalSettings.cursorShape.shapeOverride,
                            selectionArgb = terminalTheme.selection,
                            selectedTextArgb = terminalTheme.selectedText,
                        )
                        view.setTextSizeChangedListener(onTerminalFontSize)
                        view.setOneShotModifiers(controlActive, altActive)
                        view.setWindowFocusGainedListener {
                            group.activateView(viewAttachmentId)
                        }
                        view.setTerminalCallbacks(
                            onInput = controller::send,
                            onResize = { columns, rows ->
                                group.resizeFromView(tab.id, viewAttachmentId, columns, rows)
                            },
                            onScroll = controller::scroll,
                            onModifiersConsumed = {
                                controlActive = false
                                altActive = false
                            },
                        )
                        state.snapshot?.let(view::submitSnapshot)
                    },
                    onRelease = { view ->
                        view.releaseTerminalInput()
                        if (terminalView === view) terminalView = null
                        view.setOnFocusChangeListener(null)
                        view.setWindowFocusGainedListener(null)
                        view.setTextSizeChangedListener(null)
                        view.setTerminalCallbacks(
                            onInput = null,
                            onResize = null,
                            onScroll = null,
                            onModifiersConsumed = null,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!terminalInputCollapsed) {
                TerminalAccessoryKeyboard(
                    colors = chrome,
                    controlActive = controlActive,
                    altActive = altActive,
                    onDetach = detachTerminal,
                    onCloseSession = { closeSessionRequested = true },
                    onCompose = {
                        terminalView?.releaseTerminalInput()
                        controlActive = false
                        altActive = false
                        terminalView?.setOneShotModifiers(false, false)
                        composerOpen = true
                    },
                    onModifiersChanged = { control, alt ->
                        controlActive = control
                        altActive = alt
                        terminalView?.setOneShotModifiers(control, alt)
                    },
                    onSend = controller::send,
                    onPaste = { terminalView?.pasteFromClipboard() },
                    onKeepTerminalFocus = {
                        terminalView?.requestTerminalInput(showSoftwareKeyboard = false)
                    },
                    onCollapseInput = {
                        terminalView?.releaseTerminalInput()
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        terminalInputCollapsed = true
                    },
                )
            }
        } else {
            TerminalConnectionSurface(
                modifier = Modifier.weight(1f),
                hostDisplayName = hostDisplayName,
                endpoint = runCatching { initialConfig.endpoint }.getOrDefault(initialConfig.host),
                state = state,
                tailnetRunning = tailnetState is TailnetState.Running,
                configurationValid = initialConfig.validate() == null,
                onRetry = { controller.connect(tab.config) },
                onEditSshSettings = {
                    resignTerminalInput()
                    onEditSshSettings()
                },
            )
        }
    }
}

private fun terminalAppearanceToken(
    settings: TerminalSettings,
    theme: TerminalThemePalette,
): String = "${theme.id}:${settings.font.id}:${settings.fontSizeSp}:${settings.cursorShape.id}"

@Suppress("DEPRECATION")
@Composable
private fun TerminalSystemBars(colors: TerminalChromeColors) {
    val view = LocalView.current
    val activity = remember(view) { view.context.findActivity() } ?: return
    val appBackground = NovaTheme.colors.backgroundPrimary

    DisposableEffect(activity, view, colors.background, appBackground) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)
        val previousStatusBarColor = window.statusBarColor
        val previousNavigationBarColor = window.navigationBarColor
        val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
        val previousLightNavigationBars = insetsController.isAppearanceLightNavigationBars

        fun apply(color: Color) {
            window.statusBarColor = color.toArgb()
            window.navigationBarColor = color.toArgb()
            val useDarkIcons = color.luminance() >= 0.5f
            insetsController.isAppearanceLightStatusBars = useDarkIcons
            insetsController.isAppearanceLightNavigationBars = useDarkIcons
        }

        apply(colors.background)
        onDispose {
            window.statusBarColor = previousStatusBarColor
            window.navigationBarColor = previousNavigationBarColor
            insetsController.isAppearanceLightStatusBars = previousLightStatusBars
            insetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun TerminalSessionHeader(
    colors: TerminalChromeColors,
    hostDisplayName: String,
    status: String,
    connected: Boolean,
    showAddTab: Boolean,
    onExit: () -> Unit,
    onAddTab: () -> Unit,
    onOpenNewWindow: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showGestures by remember { mutableStateOf(false) }
    if (showGestures) TerminalGestureGuide { showGestures = false }
    Surface(
        color = colors.background,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = NovaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    NovaIcons.ArrowBack,
                    contentDescription = stringResource(R.string.cd_detach_terminal_to_host),
                    tint = colors.foreground,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(NovaSpacing.xxs),
            ) {
                Text(
                    hostDisplayName,
                    color = colors.foreground,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    status,
                    color = if (connected) colors.connected else colors.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            if (showAddTab) {
                IconButton(onClick = onAddTab) {
                    Icon(
                        NovaIcons.Add,
                        contentDescription = stringResource(R.string.cd_new_terminal_tab),
                        tint = colors.foreground,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.cd_terminal_actions),
                        tint = colors.foreground,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.terminal_gestures)) },
                        onClick = { menuExpanded = false; showGestures = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_new_window)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenNewWindow()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.close_session),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.PowerSettingsNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDisconnect()
                        },
                    )
                }
            }
        }
    }
}

internal fun shouldShowTerminalTabBar(tabCount: Int): Boolean = tabCount > 1

@Composable
private fun TerminalConnectionSurface(
    modifier: Modifier,
    hostDisplayName: String,
    endpoint: String,
    state: TerminalConnectionState,
    tailnetRunning: Boolean,
    configurationValid: Boolean,
    onRetry: () -> Unit,
    onEditSshSettings: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = false) {}
            .padding(NovaSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NovaSpacing.md),
        ) {
            Icon(
                imageVector = NovaIcons.Terminal,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = NovaTheme.colors.primary,
            )
            Text(
                hostDisplayName,
                style = MaterialTheme.typography.titleLarge,
                color = NovaTheme.colors.textPrimary,
            )
            Text(
                endpoint,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = NovaTheme.colors.textSecondary,
            )
            if (
                state.busy ||
                (
                    tailnetRunning &&
                        configurationValid &&
                        state.status == uiText(R.string.terminal_status_preparing)
                )
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
            }
            Text(
                when {
                    !tailnetRunning -> stringResource(R.string.terminal_requires_tailnet)
                    !configurationValid -> stringResource(R.string.terminal_requires_settings)
                    else -> state.status.asString()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (!tailnetRunning || !configurationValid) {
                    NovaTheme.colors.statusDisconnected
                } else {
                    NovaTheme.colors.textSecondary
                },
            )
            if (!state.busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(NovaSpacing.sm)) {
                    if (tailnetRunning && configurationValid) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    TextButton(onClick = onEditSshSettings) {
                        Text(stringResource(R.string.feature_ssh_settings))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalTabBar(
    colors: TerminalChromeColors,
    tabs: List<TerminalSessionTab>,
    selectedTabId: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onActions: (TerminalSessionTab) -> Unit,
) {
    Surface(
        color = colors.background,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = NovaSpacing.sm, vertical = NovaSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(NovaSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == selectedTabId
                val title = tab.title.asString()
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) colors.foreground.copy(alpha = 0.13f) else Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, colors.foreground.copy(alpha = 0.28f)) else null,
                    modifier = Modifier.combinedClickable(
                        onClick = { onSelect(tab.id) },
                        onLongClickLabel = stringResource(R.string.cd_manage_tab, title),
                        onLongClick = { onActions(tab) },
                    ),
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.foreground else colors.secondary,
                        maxLines = 1,
                    )
                }
            }
            IconButton(onClick = onAdd, enabled = tabs.size < 6) {
                Icon(
                    NovaIcons.Add,
                    contentDescription = stringResource(R.string.cd_new_terminal_tab),
                    tint = if (tabs.size < 6) colors.secondary else colors.secondary.copy(alpha = 0.35f),
                )
            }
        }
    }
}

@Composable
private fun TerminalAccessoryKeyboard(
    colors: TerminalChromeColors,
    controlActive: Boolean,
    altActive: Boolean,
    onDetach: () -> Unit,
    onCloseSession: () -> Unit,
    onCompose: () -> Unit,
    onModifiersChanged: (control: Boolean, alt: Boolean) -> Unit,
    onSend: (ByteArray) -> Unit,
    onPaste: () -> Unit,
    onKeepTerminalFocus: () -> Unit,
    onCollapseInput: () -> Unit,
) {
    var symbolsVisible by rememberSaveable { mutableStateOf(true) }

    fun send(value: String, isNavigation: Boolean = false) {
        onKeepTerminalFocus()
        onSend(terminalAccessorySequence(value, controlActive, altActive, isNavigation))
        onModifiersChanged(false, false)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding(),
        color = colors.background,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NovaSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(NovaSpacing.xs),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = NovaSpacing.sm, end = NovaSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(NovaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TerminalIconKey(
                        colors = colors,
                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.cd_detach_terminal),
                        tint = colors.accent,
                        onClick = onDetach,
                    )
                    TerminalIconKey(
                        colors = colors,
                        icon = Icons.Outlined.Cancel,
                        contentDescription = stringResource(R.string.cd_close_terminal),
                        tint = colors.destructive,
                        onClick = onCloseSession,
                    )
                    TerminalAccessorySeparator(colors)
                    TerminalKey(colors, stringResource(R.string.terminal_composer_open), onClick = onCompose)
                    TerminalKey(colors, "Esc") { send("\u001b") }
                    TerminalKey(colors, "Ctl", selected = controlActive) {
                        onKeepTerminalFocus()
                        onModifiersChanged(!controlActive, altActive)
                    }
                    TerminalKey(colors, "Alt", selected = altActive) {
                        onKeepTerminalFocus()
                        onModifiersChanged(controlActive, !altActive)
                    }
                    TerminalKey(colors, "Tab") { send("\t") }
                    TerminalKey(colors, "←") { send("D", isNavigation = true) }
                    TerminalKey(colors, "↓") { send("B", isNavigation = true) }
                    TerminalKey(colors, "↑") { send("A", isNavigation = true) }
                    TerminalKey(colors, "→") { send("C", isNavigation = true) }
                    TerminalIconKey(
                        colors = colors,
                        icon = Icons.Outlined.ContentPaste,
                        contentDescription = stringResource(R.string.cd_paste),
                        tint = colors.foreground,
                        onClick = {
                            onKeepTerminalFocus()
                            onPaste()
                            onModifiersChanged(false, false)
                        },
                    )
                    TerminalKey(
                        colors,
                        stringResource(
                            if (symbolsVisible) R.string.terminal_symbols_hide
                            else R.string.terminal_symbols_show,
                        ),
                    ) {
                        onKeepTerminalFocus()
                        symbolsVisible = !symbolsVisible
                    }
                }
                TerminalIconKey(
                    colors = colors,
                    icon = Icons.Outlined.KeyboardHide,
                    contentDescription = stringResource(R.string.cd_hide_keyboard),
                    tint = colors.foreground,
                    onClick = onCollapseInput,
                )
            }
            if (symbolsVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = NovaSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(NovaSpacing.xs),
                ) {
                    TerminalSymbols.forEach { symbol ->
                        TerminalKey(colors, symbol) { send(symbol) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalIconKey(
    colors: TerminalChromeColors,
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(19.dp),
                tint = tint,
            )
        }
    }
}

@Composable
private fun TerminalAccessorySeparator(colors: TerminalChromeColors) {
    Surface(
        modifier = Modifier.size(width = 1.dp, height = 26.dp),
        color = colors.border,
    ) {}
}

@Composable
private fun TerminalKey(
    colors: TerminalChromeColors,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 38.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) colors.foreground else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) colors.foreground else colors.border),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (selected) colors.terminalBackground else colors.foreground,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}

internal fun terminalAccessorySequence(
    value: String,
    control: Boolean,
    alt: Boolean,
    navigation: Boolean,
): ByteArray {
    if (navigation) {
        var modifier = 1
        if (alt) modifier += 2
        if (control) modifier += 4
        val sequence = if (modifier == 1) "\u001b[$value" else "\u001b[1;${modifier}$value"
        return sequence.toByteArray(Charsets.UTF_8)
    }

    val payload = if (control && value.length == 1) {
        val codepoint = value[0].code
        byteArrayOf(
            when (codepoint) {
                ' '.code, '@'.code -> 0
                in 'a'.code..'z'.code -> codepoint - 'a'.code + 1
                in 'A'.code..'Z'.code -> codepoint - 'A'.code + 1
                '['.code -> 27
                '\\'.code -> 28
                ']'.code -> 29
                '^'.code -> 30
                '_'.code -> 31
                '?'.code -> 127
                else -> codepoint
            }.toByte(),
        )
    } else {
        value.toByteArray(Charsets.UTF_8)
    }
    return if (alt) byteArrayOf(0x1b) + payload else payload
}

private fun TextFieldValue.withNewlineAtSelection(): TextFieldValue {
    val start = minOf(selection.start, selection.end)
    val end = maxOf(selection.start, selection.end)
    return TextFieldValue(text.replaceRange(start, end, "\n"), TextRange(start + 1))
}

private fun terminalComposedInputSequence(text: String, mouseFlags: Int): ByteArray {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace('\n', '\r')
    // Paste multiline text as one unit when the remote terminal enables bracketed paste.
    val payload = if (mouseFlags and 256 != 0) "\u001b[200~$normalized\u001b[201~" else normalized
    return "$payload\r".toByteArray(Charsets.UTF_8)
}

private val TerminalSymbols = listOf(
    "/", "-", "_", "|", "~", ".", ":", ";", "$", "@", "#", "&", "*",
    "(", ")", "{", "}", "[", "]", "'", "\"", "`",
)
