/*
 * NovaScale for Android
 * Copyright (C) 2026 NovaScale contributors
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */
package cc.galaxnet.novascale

import android.app.Application
import cc.galaxnet.novascale.core.TailnetBackend
import cc.galaxnet.novascale.files.SftpPreviewCache
import cc.galaxnet.novascale.tailnet.GoTailnetBackend
import cc.galaxnet.novascale.terminalfeature.TerminalSessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NovaScaleApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val tailnetBackend: TailnetBackend by lazy { GoTailnetBackend(this) }
    internal val preferences: AppPreferences by lazy { AppPreferences(this) }
    internal val terminalSessions: TerminalSessionRegistry by lazy {
        TerminalSessionRegistry(this, tailnetBackend, applicationScope)
    }

    internal val localProxy by lazy { cc.galaxnet.novascale.browser.LocalProxyController(this, tailnetBackend, preferences, applicationScope) }

    override fun onCreate() {
        super.onCreate()
        localProxy
        SftpPreviewCache.clear(this)
        applicationScope.launch {
            preferences.keepTerminalSessionsInBackground.collectLatest { enabled ->
                terminalSessions.setKeepInBackground(enabled)
            }
        }
    }
}
