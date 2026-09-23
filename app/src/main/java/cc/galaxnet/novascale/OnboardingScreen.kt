/*
 * NovaScale for Android
 * Copyright (C) 2026 NovaScale contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
package cc.galaxnet.novascale

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.galaxnet.novascale.core.TailnetState
import cc.galaxnet.novascale.ui.NovaIcons
import cc.galaxnet.novascale.ui.NovaTheme

@Composable
internal fun OnboardingScreen(busy: Boolean, error: Boolean, onComplete: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(R.string.onboard_network_title, R.string.onboard_tools_title, R.string.onboard_browser_title)
    val descriptions = listOf(R.string.onboard_network_body, R.string.onboard_tools_body, R.string.onboard_browser_body)
    val icons = listOf(NovaIcons.Hosts, NovaIcons.Terminal, Icons.Outlined.Language)
    BackHandler(page > 0 && !busy) { page-- }
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .12f), NovaTheme.colors.backgroundPrimary)))
            .safeDrawingPadding().padding(24.dp),
    ) {
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.height(24.dp))
            Surface(modifier = Modifier.align(Alignment.CenterHorizontally), shape = MaterialTheme.shapes.large, color = androidx.compose.ui.graphics.Color(0xFF3656C9)) {
                Icon(icons[page], null, Modifier.padding(20.dp).size(40.dp), tint = androidx.compose.ui.graphics.Color.White)
            }
            Text(stringResource(titles[page]), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(descriptions[page]), style = MaterialTheme.typography.bodyLarge, color = NovaTheme.colors.textSecondary)

        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            titles.indices.forEach { index ->
                Box(Modifier.size(if (index == page) 10.dp else 8.dp).background(
                    if (index == page) NovaTheme.colors.primary else NovaTheme.colors.borderSubtle, CircleShape))
            }
        }
        if (error) Text(stringResource(R.string.onboard_save_error), color = MaterialTheme.colorScheme.error)
        Button(enabled = !busy, onClick = { if (page < 2) page++ else onComplete() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (page == 2) R.string.onboard_start else R.string.onboard_next))
        }
        TextButton(enabled = !busy && page > 0, onClick = { page-- }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboard_back))
        }
    }
}

@Composable
internal fun SignedOutHosts(state: TailnetState, onLogin: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when (state) {
            TailnetState.Starting -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.tailnet_starting), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.tailnet_checking_session), color = NovaTheme.colors.textSecondary)
            }
            is TailnetState.LoginRequired -> {
                Icon(NovaIcons.Hosts, null, Modifier.size(64.dp), tint = NovaTheme.colors.primary)
                Text(stringResource(if (state.awaitingApproval) R.string.auth_waiting_approval else R.string.connect_tailnet), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(if (state.awaitingApproval) R.string.auth_approval_description else R.string.home_login_description), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onLogin) { Text(stringResource(if (state.awaitingApproval) R.string.auth_back_setup else R.string.continue_sign_in)) }
            }
            is TailnetState.Failed -> {
                Text(stringResource(R.string.status_connection_failed), style = MaterialTheme.typography.headlineSmall)
                Text(state.redactedMessage, color = MaterialTheme.colorScheme.error)
                Button(onClick = onLogin) { Text(stringResource(R.string.action_retry)) }
            }
            TailnetState.Stopped -> {
                Icon(NovaIcons.Hosts, null, Modifier.size(64.dp), tint = NovaTheme.colors.primary)
                Text(stringResource(R.string.status_offline), style = MaterialTheme.typography.headlineSmall)
                Button(onClick = onLogin) { Text(stringResource(R.string.connect_tailnet)) }
            }
            is TailnetState.Running -> Unit
        }
    }
}
