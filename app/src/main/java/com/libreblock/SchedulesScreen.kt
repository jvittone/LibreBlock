package com.libreblock

import android.text.format.DateFormat
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.format.TextStyle

@Composable
fun SchedulesScreen(store: Store, rev: Int, now: Long, onChange: () -> Unit) {
    val schedules = remember(rev) { store.schedules }
    val mainList = remember(rev) { store.blockedApps }
    val lockedIds = remember(rev, now) { store.lockedScheduleIds(now) }
    var editing by remember { mutableStateOf<Schedule?>(null) }
    val context = LocalContext.current

    /** Enregistre, sauf si le mode strict ne laisserait plus assez de temps libre. */
    fun save(s: Schedule): Boolean {
        val updated = withSchedule(schedules, s)
        if (!strictChangeAllowed(schedules, updated)) {
            Toast.makeText(context, R.string.error_strict_too_long, Toast.LENGTH_LONG).show()
            return false
        }
        store.schedules = updated
        onChange()
        return true
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.tab_schedules), style = MaterialTheme.typography.headlineMedium)
                if (schedules.isEmpty()) {
                    Text(stringResource(R.string.schedules_empty), Modifier.padding(top = 8.dp))
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
                            Text(daysLabel(s.days))
                            Text(
                                listOfNotNull(
                                    if (s.useMainList) stringResource(R.string.list_main_count, mainList.size)
                                    else stringResource(R.string.list_custom_count, s.apps.size),
                                    stringResource(if (s.strict) R.string.strict_short else R.string.flexible_short),
                                    if (s.workMin > 0) stringResource(R.string.cycles_summary, s.workMin, s.breakMin)
                                    else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (locked) {
                                Text(stringResource(R.string.locked_running), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Switch(
                            checked = s.enabled,
                            onCheckedChange = { save(s.copy(enabled = it)) },
                            enabled = !locked,
                        )
                        IconButton(
                            onClick = { store.schedules = schedules.filter { it.id != s.id }; onChange() },
                            enabled = !locked,
                        ) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = {
                editing = Schedule(System.currentTimeMillis(), 0b0011111, 9 * 60, 17 * 60, useMainList = true)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_schedule)) }
    }

    editing?.let { s ->
        ScheduleDialog(
            s,
            locked = s.id in lockedIds,
            mainList = mainList,
            schedules = schedules,
            onDismiss = { editing = null },
        ) { if (save(it)) editing = null }
    }
}

private fun withSchedule(schedules: List<Schedule>, s: Schedule) =
    if (schedules.any { it.id == s.id }) schedules.map { if (it.id == s.id) s else it } else schedules + s

/** Refuse ce qui allongerait le verrouillage au-delà de la limite ; accepte tout ce qui le raccourcit. */
private fun strictChangeAllowed(before: List<Schedule>, after: List<Schedule>) =
    strictSchedulesAllowed(after) || longestStrictStretch(after) <= longestStrictStretch(before)

/** locked : horaire strict en cours, on ne peut qu'ajouter des applis. */
@Composable
private fun ScheduleDialog(
    initial: Schedule,
    locked: Boolean,
    mainList: Set<String>,
    schedules: List<Schedule>,
    onDismiss: () -> Unit,
    onSave: (Schedule) -> Unit,
) {
    var days by remember { mutableIntStateOf(initial.days) }
    var start by remember { mutableIntStateOf(initial.startMin) }
    var end by remember { mutableIntStateOf(initial.endMin) }
    var cycles by remember { mutableStateOf(initial.workMin > 0) }
    var work by remember { mutableIntStateOf(if (initial.workMin > 0) initial.workMin else 25) }
    var pause by remember { mutableIntStateOf(initial.breakMin) }
    var strict by remember { mutableStateOf(initial.strict) }
    var apps by remember { mutableStateOf(initial.apps) }
    var useMainList by remember { mutableStateOf(initial.useMainList) }
    var pickingStart by remember { mutableStateOf<Boolean?>(null) }
    var pickingApps by remember { mutableStateOf(false) }
    val editable = !locked
    val result = initial.copy(
        days = days, startMin = start, endMin = end,
        workMin = if (cycles) work else 0, breakMin = pause,
        strict = strict, apps = apps, useMainList = useMainList,
    )
    val tooLong = !strictChangeAllowed(schedules, withSchedule(schedules, result))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (locked) Text(stringResource(R.string.schedule_locked_notice))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNames(TextStyle.NARROW).forEachIndexed { i, letter ->
                        DayToggle(letter, (days and (1 shl i)) != 0, editable) { days = days xor (1 shl i) }
                    }
                }
                OutlinedButton(onClick = { pickingStart = true }, Modifier.fillMaxWidth(), enabled = editable) {
                    Text(stringResource(R.string.start_time, formatMinutes(start)))
                }
                OutlinedButton(onClick = { pickingStart = false }, Modifier.fillMaxWidth(), enabled = editable) {
                    Text(stringResource(R.string.end_time, formatMinutes(end)))
                }
                when {
                    end == start -> Text(stringResource(R.string.all_day), style = MaterialTheme.typography.bodySmall)
                    end < start -> Text(stringResource(R.string.ends_next_day), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.blocked_apps), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useMainList,
                        onClick = { useMainList = true },
                        label = { Text(stringResource(R.string.main_list)) },
                        enabled = editable,
                    )
                    FilterChip(
                        selected = !useMainList,
                        onClick = {
                            useMainList = false
                            if (apps.isEmpty()) apps = mainList
                        },
                        label = { Text(stringResource(R.string.custom_list)) },
                        enabled = editable,
                    )
                }
                if (useMainList) {
                    Text(
                        pluralStringResource(R.plurals.main_list_hint, mainList.size, mainList.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    OutlinedButton(onClick = { pickingApps = true }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.choose_apps, apps.size))
                    }
                }
                LabeledSwitch(stringResource(R.string.strict_schedule_switch), strict, editable) { strict = it }
                if (tooLong) {
                    Text(
                        stringResource(R.string.error_strict_too_long),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LabeledSwitch(stringResource(R.string.cycles_switch), cycles, editable) { cycles = it }
                if (cycles) {
                    Stepper(stringResource(R.string.work), work, 5..120, 5, editable) { work = it }
                    Stepper(stringResource(R.string.break_label), pause, 1..60, 1, editable) { pause = it }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(result) },
                enabled = days != 0 && !tooLong,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    pickingStart?.let { isStart ->
        TimePickDialog(if (isStart) start else end, onDismiss = { pickingStart = null }) { m ->
            if (isStart) start = m else end = m
            pickingStart = null
        }
    }
    if (pickingApps) {
        AppPickerDialog(
            stringResource(R.string.schedule_apps_title),
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

/** « En semaine », « Week-end », « Tous les jours » ou les jours abrégés dans la langue du téléphone. */
@Composable
private fun daysLabel(days: Int): String = when (days) {
    0b1111111 -> stringResource(R.string.every_day)
    0b0011111 -> stringResource(R.string.weekdays)
    0b1100000 -> stringResource(R.string.weekend)
    else -> dayNames(TextStyle.SHORT).filterIndexed { i, _ -> (days and (1 shl i)) != 0 }.joinToString(" ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initial / 60, initial % 60, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { TimePicker(state) },
    )
}
