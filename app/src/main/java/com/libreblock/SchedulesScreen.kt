package com.libreblock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun SchedulesScreen(store: Store, rev: Int, now: Long, onChange: () -> Unit) {
    val schedules = remember(rev) { store.schedules }
    val mainList = remember(rev) { store.blockedApps }
    val lockedIds = remember(rev, now) { store.lockedScheduleIds(now) }
    var editing by remember { mutableStateOf<Schedule?>(null) }

    fun save(s: Schedule) {
        store.schedules =
            if (schedules.any { it.id == s.id }) schedules.map { if (it.id == s.id) s else it }
            else schedules + s
        onChange()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Horaires", style = MaterialTheme.typography.headlineMedium)
                if (schedules.isEmpty()) {
                    Text(
                        "Aucun horaire. Appuie sur + pour bloquer des applis à heures fixes.",
                        Modifier.padding(top = 8.dp),
                    )
                }
            }
            items(schedules, key = { it.id }) { s ->
                val locked = s.id in lockedIds
                Card(Modifier.fillMaxWidth().clickable { editing = s }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${formatMinutes(s.startMin)} – ${formatMinutes(s.endMin)}" + if (s.strict) "  🔒" else "",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(formatDays(s.days))
                            Text(
                                buildString {
                                    append(
                                        if (s.useMainList) "Liste principale (${mainList.size})"
                                        else "Liste perso (${s.apps.size})"
                                    )
                                    append(if (s.strict) " · strict" else " · souple")
                                    if (s.workMin > 0) append(" · travail ${s.workMin} min / pause ${s.breakMin} min")
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (locked) Text("En cours, verrouillé", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = s.enabled,
                            onCheckedChange = { save(s.copy(enabled = it)) },
                            enabled = !locked,
                        )
                        IconButton(
                            onClick = { store.schedules = schedules.filter { it.id != s.id }; onChange() },
                            enabled = !locked,
                        ) { Icon(Icons.Default.Delete, contentDescription = "Supprimer") }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                editing = Schedule(System.currentTimeMillis(), 0b0011111, 9 * 60, 17 * 60, useMainList = true)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "Ajouter un horaire") }
    }

    editing?.let { s ->
        ScheduleDialog(
            s,
            locked = s.id in lockedIds,
            mainList = mainList,
            onDismiss = { editing = null },
        ) { save(it); editing = null }
    }
}

/** locked : horaire strict en cours, on ne peut qu'ajouter des applis. */
@Composable
private fun ScheduleDialog(
    initial: Schedule,
    locked: Boolean,
    mainList: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
) {
    var days by remember { mutableIntStateOf(initial.days) }
    var start by remember { mutableIntStateOf(initial.startMin) }
    var end by remember { mutableIntStateOf(initial.endMin) }
    var cycles by remember { mutableStateOf(initial.workMin > 0) }
    var work by remember { mutableIntStateOf(if (initial.workMin > 0) initial.workMin else 30) }
    var pause by remember { mutableIntStateOf(initial.breakMin) }
    var strict by remember { mutableStateOf(initial.strict) }
    var apps by remember { mutableStateOf(initial.apps) }
    var useMainList by remember { mutableStateOf(initial.useMainList) }
    var pickingStart by remember { mutableStateOf<Boolean?>(null) }
    var pickingApps by remember { mutableStateOf(false) }
    val editable = !locked

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horaire") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (locked) Text("🔒 Horaire strict en cours : tu peux seulement ajouter des applis.")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LETTERS.forEachIndexed { i, letter ->
                        DayToggle(letter, (days and (1 shl i)) != 0, editable) { days = days xor (1 shl i) }
                    }
                }
                OutlinedButton(onClick = { pickingStart = true }, Modifier.fillMaxWidth(), enabled = editable) {
                    Text("Début : ${formatMinutes(start)}")
                }
                OutlinedButton(onClick = { pickingStart = false }, Modifier.fillMaxWidth(), enabled = editable) {
                    Text("Fin : ${formatMinutes(end)}")
                }
                when {
                    end == start -> Text("Toute la journée", style = MaterialTheme.typography.bodySmall)
                    end < start -> Text("Se termine le lendemain", style = MaterialTheme.typography.bodySmall)
                }
                Text("Applis bloquées", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useMainList,
                        onClick = { useMainList = true },
                        label = { Text("Liste principale") },
                        enabled = editable,
                    )
                    FilterChip(
                        selected = !useMainList,
                        onClick = {
                            useMainList = false
                            if (apps.isEmpty()) apps = mainList
                        },
                        label = { Text("Personnalisée") },
                        enabled = editable,
                    )
                }
                if (useMainList) {
                    Text(
                        "${mainList.size} appli(s), modifiables dans l'onglet Applis",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    OutlinedButton(onClick = { pickingApps = true }, Modifier.fillMaxWidth()) {
                        Text("Choisir les applis (${apps.size})")
                    }
                }
                LabeledSwitch("Strict (impossible de modifier ou contourner pendant l'horaire)", strict, editable) {
                    strict = it
                }
                LabeledSwitch("Session de travail (avec pauses)", cycles, editable) { cycles = it }
                if (cycles) {
                    Stepper("Travail", work, 5..120, 5, editable) { work = it }
                    Stepper("Pause", pause, 1..60, 1, editable) { pause = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        initial.copy(
                            days = days, startMin = start, endMin = end,
                            workMin = if (cycles) work else 0, breakMin = pause,
                            strict = strict, apps = apps, useMainList = useMainList,
                        )
                    )
                },
                enabled = days != 0,
            ) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    pickingStart?.let { isStart ->
        TimePickDialog(if (isStart) start else end, onDismiss = { pickingStart = null }) { m ->
            if (isStart) start = m else end = m
            pickingStart = null
        }
    }
    if (pickingApps) {
        AppPickerDialog(
            "Applis de l'horaire",
            initial = apps,
            locked = locked,
            mainList = mainList,
            onDismiss = { pickingApps = false },
        ) { apps = it; pickingApps = false }
    }
}

@Composable
private fun DayToggle(letter: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = if (selected) colors.onPrimary else colors.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val state = rememberTimePickerState(initial / 60, initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        text = { TimePicker(state) },
    )
}
