package com.libreblock

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Appli lançable du téléphone ; icon est null dans les tests. */
class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap? = null)

/** Applis lançables, chargées une fois en arrière-plan (null pendant le chargement). */
@Composable
fun rememberInstalledApps(): List<AppEntry>? {
    val context = LocalContext.current
    return produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { loadApps(context) } }.value
}

/** Résumé d'une liste : « 5 applis » ou « tout sauf 3 applis ». */
@Composable
fun listSummary(list: AppList): String = pluralStringResource(
    if (list.allowOnly) R.plurals.all_except_count else R.plurals.apps_count, list.apps.size, list.apps.size,
)

/** Onglet Applis : la liste principale, utilisée par les sessions manuelles. */
@Composable
fun AppsScreen(list: AppList, locked: Boolean, apps: List<AppEntry>?, onChange: (AppList) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.apps_intro),
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        AppPicker(list, locked, apps, onChange)
    }
}

/**
 * Choix du mode (bloquer ces applis / tout bloquer sauf) et des applis, avec recherche.
 * locked : on ne peut que rendre la liste plus stricte.
 */
@Composable
fun AppPicker(list: AppList, locked: Boolean, apps: List<AppEntry>?, onChange: (AppList) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    // Ordre figé à l'ouverture (applis cochées en premier) pour que la liste ne saute pas à chaque clic.
    val initialApps = remember { list.apps }
    val sorted = remember(apps) {
        apps?.sortedWith(compareBy({ it.pkg !in initialApps }, { it.label.lowercase() }))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !list.allowOnly,
                onClick = { onChange(list.copy(allowOnly = false)) },
                label = { Text(stringResource(R.string.mode_block_selected)) },
                enabled = !locked,
            )
            FilterChip(
                selected = list.allowOnly,
                onClick = { onChange(list.copy(allowOnly = true)) },
                label = { Text(stringResource(R.string.mode_allow_only)) },
                enabled = !locked,
            )
        }
        if (list.allowOnly) {
            Text(
                stringResource(R.string.allow_only_hint),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
                stringResource(if (list.allowOnly) R.string.picker_locked_allow else R.string.picker_locked),
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (sorted == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn {
            items(sorted.filter { it.label.contains(query, ignoreCase = true) }, key = { it.pkg }) { app ->
                val checked = app.pkg in list.apps
                val canToggle = !locked || list.canToggleWhenLocked(app.pkg)
                ListItem(
                    headlineContent = { Text(app.label) },
                    leadingContent = {
                        if (app.icon != null) Image(app.icon, contentDescription = null, Modifier.size(40.dp))
                        else Spacer(Modifier.size(40.dp))
                    },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = canToggle) },
                    modifier = Modifier.clickable(enabled = canToggle) { onChange(list.toggle(app.pkg)) },
                )
            }
        }
    }
}

/** AppPicker en plein écran, pour la liste personnalisée d'un horaire. */
@Composable
fun AppPickerDialog(
    title: String,
    initial: AppList,
    locked: Boolean,
    mainList: AppList,
    apps: List<AppEntry>?,
    onDismiss: () -> Unit,
    onDone: (AppList) -> Unit,
) {
    var selected by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onDone(selected) }) { Text(stringResource(R.string.ok)) }
                }
                TextButton(
                    onClick = { selected = mainList },
                    modifier = Modifier.padding(horizontal = 8.dp),
                    enabled = !locked && selected != mainList,
                ) { Text(stringResource(R.string.copy_main_list)) }
                AppPicker(selected, locked, apps) { selected = it }
            }
        }
    }
}

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val essentials = essentialPackages(context)
    return launchableApps(context)
        .filter { it.packageName !in essentials }
        .map {
            AppEntry(
                pkg = it.packageName,
                label = it.loadLabel(pm).toString(),
                icon = it.loadIcon(pm).toBitmap(96, 96).asImageBitmap(),
            )
        }
}

/** Applis qui ont une icône dans le lanceur : les seules qu'on puisse bloquer. */
fun launchableApps(context: Context) =
    @Suppress("DEPRECATION")
    context.packageManager
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
