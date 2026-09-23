// Copyright (C) 2026 NovaScale contributors
// SPDX-License-Identifier: GPL-3.0-only
package cc.galaxnet.novascale.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import cc.galaxnet.novascale.R
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import java.net.URI
import kotlin.coroutines.resume

internal data class BrowserUiState(val address: String = "http://", val url: String? = null, val progress: Int = 0,
    val title: String = "", val back: Boolean = false, val forward: Boolean = false, val error: Int? = null)
internal class BrowserTab(val id: Long, address: String = "http://") {
    val state = MutableStateFlow(BrowserUiState(address = address))
    var pendingOpen by mutableStateOf<String?>(null)
    var requestedUrl by mutableStateOf<String?>(null)
    var desktop by mutableStateOf(false)
}

internal class BrowserViewModel : ViewModel() {
    private var nextId = 1L
    val tabs = mutableStateListOf<BrowserTab>()
    // The landing-page draft is not an open browser tab.
    var active by mutableStateOf(BrowserTab(nextId++))
        private set
    val state get() = active.state
    fun select(tab: BrowserTab) { if (tab in tabs) active = tab }
    fun newTab(address: String = "http://") {
        active = BrowserTab(nextId++, address).also(tabs::add)
    }
    fun close(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tabs.remove(tab)
        if (tabs.isEmpty()) active = BrowserTab(nextId++)
        else if (active === tab) active = tabs[index.coerceAtMost(tabs.lastIndex)]
    }
    fun address(value: String) { state.value = state.value.copy(address = value) }
    fun open(): String? {
        val url = runCatching { browserUrl(state.value.address) }.getOrNull()
        state.value = state.value.copy(url = url ?: state.value.url, error = if (url == null) R.string.browser_invalid_url else null)
        if (url != null && active !in tabs) tabs.add(active)
        return url
    }
}

internal fun browserHostAddress(dnsName: String?, addresses: List<String>): String {
    val host = dnsName?.trimEnd('.')?.substringBefore('.')?.takeIf { it.isNotBlank() }
        ?: addresses.firstOrNull().orEmpty()
    return "http://" + if (":" in host) "[$host]" else host
}

internal fun browserUrl(raw: String): String {
    val value = raw.trim().let { if ("://" in it) it else "http://$it" }
    val uri = URI(value)
    require(uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
    require(uri.port == -1 || uri.port in 1..65535)
    val host = uri.host.lowercase().trim('[', ']').trimEnd('.')
    require(host != "localhost" && !host.endsWith(".localhost") && host != "::1" && host != "0.0.0.0" && !host.startsWith("127."))
    return value
}

// All browser windows use the same application proxy. Serializing acknowledgments
// prevents a cancelled setup callback overwriting a newer endpoint. Never clear
// to DIRECT on teardown: a stale rule must point to a closed listener.
private object BrowserProxyOverride {
    val mutex = Mutex()
    private var profile: String? = null
    @SuppressLint("RequiresFeature")
    suspend fun configure(context: android.content.Context, session: ProxySession) = withContext(NonCancellable) {
        mutex.withLock {
            check(WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE))
            if (profile != session.profileId) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    CookieManager.getInstance().removeAllCookies { if (continuation.isActive) continuation.resume(Unit) }
                }
                WebStorage.getInstance().deleteAllData()
                profile = session.profileId
            }
            suspendCancellableCoroutine { continuation ->
                ProxyController.getInstance().setProxyOverride(
                    ProxyConfig.Builder().addProxyRule(session.endpoint.httpUrl).removeImplicitRules().build(), context.mainExecutor,
                ) { if (continuation.isActive) continuation.resume(Unit) }
            }
        }
    }
}

@Composable
internal fun BrowserScreen(controller: LocalProxyController, model: BrowserViewModel = viewModel(),
    onBrowsingChanged: (Boolean) -> Unit = {}, visible: Boolean = true,
    appNavigationVisible: Boolean = false, onToggleAppNavigation: () -> Unit = {}) {
    val proxy by controller.state.collectAsStateWithLifecycle()
    BrowserContent(proxy.session, { controller.state.value.session === it }, model, onBrowsingChanged,
        visible, appNavigationVisible, onToggleAppNavigation)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserContent(session: ProxySession?, isSessionCurrent: (ProxySession) -> Boolean,
    model: BrowserViewModel = viewModel(), onBrowsingChanged: (Boolean) -> Unit = {},
    visible: Boolean = true, appNavigationVisible: Boolean = false, onToggleAppNavigation: () -> Unit = {}) {
    val tab = model.active
    val tabState = tab.state
    val state by tabState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var ready by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var requestedUrl by tab::requestedUrl
    val pages = remember(session, foreground) { mutableMapOf<Long, WebView>() }
    val disposedPages = remember(session, foreground) { booleanArrayOf(false) }
    var switchingTabs by remember { mutableStateOf(false) }
    DisposableEffect(session, foreground) {
        // Pages belong to this foreground proxy session, never to a different profile.
        model.tabs.forEach { it.requestedUrl = null }
        onDispose {
            model.tabs.forEach { it.pendingOpen = null }
            disposedPages[0] = true
            pages.values.forEach { it.stopLoading(); it.destroy() }
            pages.clear()
        }
    }
    var editing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var desktop by tab::desktop
    var video by remember { mutableStateOf<android.view.View?>(null) }
    var videoCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    fun closeVideo() { val callback = videoCallback; videoCallback = null; video = null; callback?.onCustomViewHidden() }
    fun closeTab(closed: BrowserTab) {
        if (closed === tab) closeVideo()
        val page = pages.remove(closed.id)
        if (closed !== tab || page?.parent == null) { page?.stopLoading(); page?.destroy() }
        model.close(closed)
    }
    fun navigate() {
        model.open()?.let { url ->
            editing = false; focus.clearFocus(); keyboard?.hide()
            requestedUrl = url
            pages[tab.id]?.loadUrl(url)
        }
    }
    DisposableEffect(requestedUrl) {
        onBrowsingChanged(requestedUrl != null)
        onDispose { onBrowsingChanged(false) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); closeVideo() }
    }
    LaunchedEffect(session, foreground) {
        ready = null
        if (!foreground || session == null) closeVideo()
        if (session != null && foreground) {
            try { BrowserProxyOverride.configure(context, session); ready = session.endpoint.id }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { tabState.value = tabState.value.copy(error = R.string.browser_proxy_unsupported) }
        }
    }
    // A host-sheet Open is explicit navigation; wait for the authenticated proxy.
    LaunchedEffect(tab, ready, visible, foreground, tab.pendingOpen) {
        if (visible && foreground && session != null && ready == session.endpoint.id) {
            tab.pendingOpen?.let { url ->
                tab.pendingOpen = null
                model.address(url)
                navigate()
            }
        }
    }
    LaunchedEffect(visible) {
        if (!visible) {
            closeVideo()
            editing = false; menu = false; switchingTabs = false
            pages.values.forEach { it.clearFocus(); it.onPause() }
        }
    }
    // Keep ownership/effects above this boundary, but remove all hidden UI,
    // dialogs and Back handlers so other app sections remain interactive.
    if (!visible) return
    BackHandler(enabled = requestedUrl != null || editing || video != null) {
        when { video != null -> closeVideo(); editing -> editing = false
            webView?.canGoBack() == true -> webView?.goBack()
            else -> requestedUrl = null }
    }
    val addressFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    @Composable
    fun addressField() {
        OutlinedTextField(state.address, model::address, modifier = Modifier.fillMaxWidth().focusRequester(addressFocus), singleLine = true,
            label = { Text(stringResource(R.string.browser_address)) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                autoCorrectEnabled = false),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { if (ready != null) navigate() }))
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        if (requestedUrl == null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.nav_browser), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (model.tabs.isNotEmpty()) TextButton(onClick = { switchingTabs = true }) { Text(pluralStringResource(R.plurals.browser_tab_count, model.tabs.size, model.tabs.size)) }
                }
                Spacer(Modifier.height(24.dp))
                Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text(stringResource(R.string.browser_ready), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.browser_welcome), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                addressField()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8080, 3000, 8123, 9090, 8096).forEach { port ->
                        SuggestionChip(enabled = browserPortUrl(state.address, port) != null, onClick = { browserPortUrl(state.address, port)?.let(model::address) }, label = { Text(":$port") })
                    }
                }
                Button(onClick = ::navigate, enabled = ready != null && session != null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.browser_go))
                }
                if (session == null) Text(stringResource(R.string.browser_proxy_required), style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                if (session != null && foreground && ready == session.endpoint.id) key(session, tab.id) {
                    AndroidView(factory = { viewContext ->
                        (pages[tab.id] ?: createBrowserWebView(viewContext, session,
                            isCurrent = { isSessionCurrent(session) && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) },
                            changed = { view, error -> tabState.value = tabState.value.copy(
                                url = view.url?.takeUnless { it == "about:blank" } ?: tabState.value.url,
                                title = view.title.orEmpty(),
                                back = view.canGoBack(), forward = view.canGoForward(), progress = view.progress,
                                error = error ?: tabState.value.error) },
                            started = { tabState.value = tabState.value.copy(error = null) },
                            fullscreen = { custom, callback ->
                                if (model.active === tab) { closeVideo(); video = custom; videoCallback = callback }
                                else callback?.onCustomViewHidden()
                            },
                        ).also { page ->
                            if (tab.desktop) page.settings.userAgentString = WebSettings.getDefaultUserAgent(context)
                                .replace("Android", "X11; Linux x86_64").replace("Mobile", "")
                            pages[tab.id] = page
                            requestedUrl?.let(page::loadUrl)
                        }).also { webView = it; it.onResume() }
                    }, onRelease = {
                        closeVideo()
                        if (!disposedPages[0]) {
                            if (pages[tab.id] === it) it.onPause() else { it.stopLoading(); it.destroy() }
                        }
                        if (webView === it) webView = null
                    },
                        modifier = Modifier.fillMaxSize().clipToBounds())
                }
            }
            Surface(Modifier.zIndex(1f).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 4.dp) {
                Column {
                    if (state.progress in 1..99) LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    Surface(onClick = { model.address(state.url ?: state.address); editing = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(if (state.url?.startsWith("https://") == true) Icons.Outlined.Lock else Icons.Outlined.Language,
                                contentDescription = stringResource(if (state.url?.startsWith("https://") == true) R.string.browser_https else R.string.browser_http), modifier = Modifier.size(18.dp))
                            Text(state.url ?: state.address, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.Edit, stringResource(R.string.browser_edit_address), modifier = Modifier.size(18.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(enabled = state.back, onClick = { webView?.goBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back)) }
                        IconButton(enabled = state.forward, onClick = { webView?.goForward() }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.browser_forward)) }
                        IconButton(onClick = { if (state.progress in 1..99) webView?.stopLoading() else webView?.reload() }) {
                            Icon(if (state.progress in 1..99) Icons.Outlined.Close else Icons.Outlined.Refresh,
                                stringResource(if (state.progress in 1..99) R.string.action_stop else R.string.action_reload))
                        }
                        IconButton(onClick = { switchingTabs = true }) {
                            Icon(Icons.Outlined.Tab, pluralStringResource(R.plurals.browser_tab_count, model.tabs.size, model.tabs.size))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.browser_actions)) }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.browser_start_page)) },
                                    leadingIcon = { Icon(Icons.Outlined.Home, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menu = false; requestedUrl = null })
                                DropdownMenuItem(text = { Text(stringResource(R.string.browser_copy_link)) },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, tint = MaterialTheme.colorScheme.primary) }, onClick = {
                                    menu = false
                                    context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("URL", state.url ?: state.address))
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.browser_share)) },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null, tint = MaterialTheme.colorScheme.primary) }, onClick = {
                                    menu = false
                                    context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, state.url ?: state.address)
                                    }, null))
                                })
                                DropdownMenuItem(text = { Text(stringResource(if (desktop) R.string.browser_mobile else R.string.browser_desktop)) },
                                    leadingIcon = { Icon(if (desktop) Icons.Outlined.Smartphone else Icons.Outlined.DesktopWindows, null, tint = MaterialTheme.colorScheme.primary) }, onClick = {
                                    menu = false; desktop = !desktop
                                    webView?.apply {
                                        settings.userAgentString = if (desktop) WebSettings.getDefaultUserAgent(context).replace("Android", "X11; Linux x86_64").replace("Mobile", "") else WebSettings.getDefaultUserAgent(context)
                                        reload()
                                    }
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.browser_close_tab)) },
                                    leadingIcon = { Icon(Icons.Outlined.Close, null, tint = MaterialTheme.colorScheme.primary) }, onClick = { menu = false; closeTab(tab) })
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (appNavigationVisible) R.string.browser_hide_app_navigation else R.string.browser_show_app_navigation)) },
                                    leadingIcon = { Icon(if (appNavigationVisible) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = { menu = false; onToggleAppNavigation() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (switchingTabs) ModalBottomSheet(onDismissRequest = { switchingTabs = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pluralStringResource(R.plurals.browser_tab_count, model.tabs.size, model.tabs.size), style = MaterialTheme.typography.titleLarge)
            model.tabs.toList().forEach { item ->
                val itemState by item.state.collectAsStateWithLifecycle()
                Surface(onClick = { closeVideo(); model.select(item); switchingTabs = false },
                    modifier = Modifier.semantics { selected = item === tab },
                    color = if (item === tab) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            Text(itemState.title.ifBlank { stringResource(R.string.browser_new_tab) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(itemState.url ?: itemState.address, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { closeTab(item) }) { Icon(Icons.Outlined.Close, stringResource(R.string.browser_close_tab)) }
                    }
                }
            }
            Button(onClick = { closeVideo(); model.newTab(); switchingTabs = false }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.browser_new_tab))
            }
        }
    }
    if (editing) ModalBottomSheet(onDismissRequest = { editing = false }) {
        Column(Modifier.fillMaxWidth().imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.browser_edit_address), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { model.address(""); addressFocus.requestFocus(); keyboard?.show() }) {
                    Text(stringResource(R.string.browser_clear_address))
                }
            }
            addressField()
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = ::navigate, enabled = ready != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.browser_go)) }
        }
    }
    video?.let { custom ->
        androidx.compose.ui.window.Dialog(onDismissRequest = ::closeVideo,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                AndroidView(factory = { custom }, modifier = Modifier.fillMaxSize(), onRelease = { (it.parent as? android.view.ViewGroup)?.removeView(it) })
                IconButton(onClick = ::closeVideo, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.browser_exit_video), tint = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
internal fun createBrowserWebView(context: android.content.Context, session: ProxySession, isCurrent: () -> Boolean,
    changed: (WebView, Int?) -> Unit, started: () -> Unit = {},
    fullscreen: (android.view.View?, WebChromeClient.CustomViewCallback?) -> Unit = { _, callback -> callback?.onCustomViewHidden() }): WebView = WebView(context).apply {
    clipToOutline = true
    outlineProvider = android.view.ViewOutlineProvider.BOUNDS
    layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
    clearCache(true)
    settings.javaScriptEnabled = true; settings.domStorageEnabled = true
    settings.allowFileAccess = false; settings.allowContentAccess = false
    settings.javaScriptCanOpenWindowsAutomatically = false; settings.setSupportMultipleWindows(false)
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW; settings.safeBrowsingEnabled = true
    settings.useWideViewPort = true; settings.loadWithOverviewMode = true
    settings.builtInZoomControls = true; settings.displayZoomControls = false
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isCurrent() || runCatching { browserUrl(request.url.toString()) }.isFailure
        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            // Never answer a destination website's authentication challenge with proxy credentials.
            if (isCurrent() && host == "127.0.0.1" && realm == session.realm) handler.proceed(session.username, session.password)
            else handler.cancel()
        }
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { started(); changed(view, null) }
        override fun onPageFinished(view: WebView, url: String) { changed(view, null) }
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) changed(view, R.string.browser_open_failed)
        }
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) { handler.cancel(); changed(view, R.string.browser_tls_failed) }
    }
    webChromeClient = object : WebChromeClient() {
        override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) { if (isCurrent()) fullscreen(view, callback) else callback.onCustomViewHidden() }
        override fun onHideCustomView() { fullscreen(null, null) }
        override fun onReceivedTitle(view: WebView, title: String) { changed(view, null) }
        override fun onProgressChanged(view: WebView, progress: Int) { if (isCurrent()) changed(view, null) }
        override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
    }
}

internal fun browserPortUrl(address: String, port: Int): String? = runCatching {
    val uri = URI(browserUrl(address))
    browserUrl(URI(uri.scheme, null, uri.host, port, uri.path?.ifEmpty { "/" } ?: "/", null, null).toString())
}.getOrNull()
