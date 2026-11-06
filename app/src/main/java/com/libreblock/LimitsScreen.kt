package com.libreblock

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Onglet Limites : temps maximal par jour dans certaines applis. */
@Composable
fun LimitsScreen(state: UiState, apps: List<AppEntry>?, onSetLimit: (String, Int?) -> Unit) {
    val settings = state.settings
    val today = state.today
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    val labels = remember(apps) { apps.orEmpty().associateBy { it.pkg } }
    val limited = settings.limits.keys.sortedBy { (labels[it]?.label ?: it).lowercase() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.limits_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.limits_hint), style = MaterialTheme.typography.bodySmall)
                    if (limited.isEmpty()) Text(stringResource(R.string.limits_empty))
                }
            }
            items(limited, key = { it }) { pkg ->
                val limit = settings.limits.getValue(pkg)
                val minutes = limit.on(today)
                val used = settings.stats.usage(pkg, today)
                ListItem(
                    headlineContent = { Text(labels[pkg]?.label ?: pkg) },
                    supportingContent = {
                        Column {
                            if (minutes != null) Text(stringResource(R.string.limit_per_day, formatDuration(minutes)))
                            Text(stringResource(R.string.limit_today, formatDuration((used / 60).toInt())))
                            if (settings.limitReached(pkg, today)) {
                                Text(stringResource(R.string.limit_reached), color = MaterialTheme.colorScheme.error)
                            }
                            if (limit.pendingOn(today)) {
                                val next = limit.next ?: 0
                                Text(
                                    if (next > 0) stringResource(R.string.limit_next, formatDuration(next))
                                    else stringResource(R.string.limit_removed_tomorrow),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    leadingContent = { AppIcon(labels[pkg]) },
                    modifier = Modifier.clickable { editing = pkg },
                )
            }
            item { Spacer(Modifier.size(88.dp)) } // place pour le bouton
        }
        ExtendedFloatingActionButton(
            onClick = { choosing = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.add_limit)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (choosing) {
        AppChooserDialog(apps, exclude = settings.limits.keys, onDismiss = { choosing = false }) {
            choosing = false
            editing = it
        }
    }
    editing?.let { pkg ->
        LimitDialog(
            label = labels[pkg]?.label ?: pkg,
            current = settings.limitOn(pkg, today),
            exists = pkg in settings.limits,
            usedSeconds = settings.stats.usage(pkg, today),
            onDismiss = { editing = null },
            onSave = { onSetLimit(pkg, it); editing = null },
        )
    }
}

@Composable
private fun AppIcon(app: AppEntry?) {
    val icon = app?.icon
    if (icon != null) Image(icon, contentDescription = null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
}

/** Réglage d'une limite. onSave(null) la supprime. */
@Composable
internal fun LimitDialog(
    label: String,
    current: Int?,
    exists: Boolean,
    usedSeconds: Long,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit,
) {
    var minutes by rememberSaveable { mutableIntStateOf(current ?: 30) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper(stringResource(R.string.daily_limit), minutes, 5..480, 5) { minutes = it }
                Text(stringResource(R.string.limit_today, formatDuration((usedSeconds / 60).toInt())))
                if (current != null && minutes > current) {
                    Text(stringResource(R.string.limit_raise_tomorrow), style = MaterialTheme.typography.bodySmall)
                }
                if (exists && current != null) {
                    TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.remove_limit)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(minutes) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Choix d'une seule appli, avec recherche. */
@Composable
private fun AppChooserDialog(
    apps: List<AppEntry>?,
    exclude: Set<String>,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Text(stringResource(R.string.choose_app), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
                if (apps == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    return@Column
                }
                val shown = remember(apps, query, exclude) {
                    apps.filter { it.pkg !in exclude && it.label.contains(query, ignoreCase = true) }
                        .sortedBy { it.label.lowercase() }
                }
                LazyColumn {
                    items(shown, key = { it.pkg }) { app ->
                        ListItem(
                            headlineContent = { Text(app.label) },
                            leadingContent = { AppIcon(app) },
                            modifier = Modifier.clickable { onChoose(app.pkg) },
                        )
                    }
                }
            }
        }
    }
}
