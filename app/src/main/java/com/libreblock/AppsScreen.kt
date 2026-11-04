package com.libreblock

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap)

/** Onglet Applis : la liste principale, utilisée par les sessions manuelles. */
@Composable
fun AppsScreen(store: Store, rev: Int, locked: Boolean, onChange: () -> Unit) {
    val blocked = remember(rev) { store.blockedApps }
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.apps_intro),
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        AppPicker(blocked, locked) {
            store.blockedApps = it
            onChange()
        }
    }
}

/**
 * Liste des applis avec recherche et cases à cocher.
 * locked : on peut ajouter des applis mais pas en retirer.
 */
@Composable
fun AppPicker(selected: Set<String>, locked: Boolean, onChange: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { loadApps(context, selected) }
    }
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )
        Text(
            stringResource(R.string.essentials_note),
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        if (locked) {
            Text(
                stringResource(R.string.picker_locked),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn {
            items(list.filter { it.label.contains(query, ignoreCase = true) }, key = { it.pkg }) { app ->
                val isBlocked = app.pkg in selected
                val canToggle = !(locked && isBlocked)
                ListItem(
                    headlineContent = { Text(app.label) },
                    leadingContent = { Image(app.icon, contentDescription = null, Modifier.size(40.dp)) },
                    trailingContent = { Checkbox(checked = isBlocked, onCheckedChange = null, enabled = canToggle) },
                    modifier = Modifier.clickable(enabled = canToggle) {
                        onChange(if (isBlocked) selected - app.pkg else selected + app.pkg)
                    },
                )
            }
        }
    }
}

/** AppPicker en plein écran, pour la liste d'un horaire. */
@Composable
fun AppPickerDialog(
    title: String,
    initial: Set<String>,
    locked: Boolean,
    mainList: Set<String>,
    onDismiss: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Text(
                        "$title (${selected.size})",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { onDone(selected) }) { Text(stringResource(R.string.ok)) }
                }
                TextButton(
                    onClick = { selected = selected + mainList },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    enabled = !selected.containsAll(mainList),
                ) { Text(stringResource(R.string.add_main_list)) }
                AppPicker(selected, locked) { selected = it }
            }
        }
    }
}

/** Applis lançables, celles déjà cochées en premier. */
private fun loadApps(context: Context, selected: Set<String>): List<AppEntry> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val essentials = essentialPackages(context)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName !in essentials }
        .map {
            AppEntry(
                pkg = it.packageName,
                label = it.loadLabel(pm).toString(),
                icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
            )
        }
        .sortedWith(compareBy({ it.pkg !in selected }, { it.label.lowercase() }))
}
