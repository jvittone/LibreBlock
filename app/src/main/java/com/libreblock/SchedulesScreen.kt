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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.libreblock.MainViewModel.SaveResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle

@Composable
fun SchedulesScreen(
    state: UiState,
    apps: List<AppEntry>?,
    onSave: (Schedule) -> SaveResult,
    onDelete: (Long) -> Boolean,
    onSaveVacation: (Vacation) -> Boolean,
    onDeleteVacation: (Long) -> Boolean,
) {
    val schedules = state.settings.schedules
    val mainList = state.settings.mainList
    val lockedIds = state.lockedScheduleIds
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var editingVacation by remember { mutableStateOf<Vacation?>(null) }
    val context = LocalContext.current

    fun vacationRefused() =
        Toast.makeText(context, R.string.vacation_locked, Toast.LENGTH_LONG).show()

    fun save(s: Schedule): Boolean {
        val result = onSave(s)
        if (result == SaveResult.StrictTooLong) {
            Toast.makeText(context, R.string.error_strict_too_long, Toast.LENGTH_LONG).show()
        }
        return result == SaveResult.Saved
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
                                    if (s.useMainList) stringResource(R.string.main_list_summary, listSummary(mainList))
                                    else stringResource(R.string.custom_list_summary, listSummary(s.list)),
                                    stringResource(if (s.strict) R.string.strict_short else R.string.flexible_short),
                                    if (s.pausedOnVacation) stringResource(R.string.work_short) else null,
                                    if (s.workMin > 0) stringResource(R.string.cycles_summary, s.workMin, s.breakMin)
                                    else null,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (locked) {
                                Text(stringResource(R.string.locked_running), style = MaterialTheme.typography.bodySmall)
                            } else if (s.pausedOnVacation && state.vacation != null) {
                                Text(stringResource(R.string.suspended_vacation), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Switch(
                            checked = s.enabled,
                            onCheckedChange = { save(s.copy(enabled = it)) },
                            enabled = !locked,
                        )
                        IconButton(onClick = { onDelete(s.id) }, enabled = !locked) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.vacations_title),
                    Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(stringResource(R.string.vacations_hint), style = MaterialTheme.typography.bodySmall)
            }
            items(state.settings.vacations.upcoming(state.today), key = { "vacation-${it.id}" }) { v ->
                val locale = LocalConfiguration.current.locales[0]
                Card(Modifier.fillMaxWidth().clickable { editingVacation = v }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.vacation_dates, formatDate(v.start, locale), formatDate(v.end, locale)),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (v.label.isNotBlank()) Text(v.label)
                            if (state.today in v) {
                                Text(stringResource(R.string.vacation_now), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { if (!onDeleteVacation(v.id)) vacationRefused() }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = {
                    editingVacation = Vacation(System.currentTimeMillis(), state.today, state.today.plusDays(6))
                }) { Text(stringResource(R.string.add_vacation)) }
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
            apps = apps,
            onDismiss = { editing = null },
        ) { if (save(it)) editing = null }
    }
    editingVacation?.let { v ->
        VacationDialog(v, onDismiss = { editingVacation = null }) {
            if (onSaveVacation(it)) editingVacation = null else vacationRefused()
        }
    }
}

/** Choix des dates (calendrier de période) et d'un nom facultatif. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VacationDialog(initial: Vacation, onDismiss: () -> Unit, onSave: (Vacation) -> Unit) {
    // Le calendrier travaille en millisecondes UTC à minuit.
    fun LocalDate.millis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun Long.date() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

    val dates = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial.start.millis(),
        initialSelectedEndDateMillis = initial.end.millis(),
    )
    var label by remember { mutableStateOf(initial.label) }
    val start = dates.selectedStartDateMillis
    val end = dates.selectedEndDateMillis ?: start

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(start = start!!.date(), end = end!!.date(), label = label.trim())) },
                enabled = start != null,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.vacation_label_hint)) },
            singleLine = true,
        )
        DateRangePicker(state = dates, modifier = Modifier.weight(1f), title = null, showModeToggle = false)
    }
}

/** locked : horaire strict en cours, on ne peut que rendre sa liste plus stricte. */
@Composable
internal fun ScheduleDialog(
    initial: Schedule,
    locked: Boolean,
    mainList: AppList,
    schedules: List<Schedule>,
    apps: List<AppEntry>?,
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
    var list by remember { mutableStateOf(initial.list) }
    var useMainList by remember { mutableStateOf(initial.useMainList) }
    var pausedOnVacation by remember { mutableStateOf(initial.pausedOnVacation) }
    var pickingStart by remember { mutableStateOf<Boolean?>(null) }
    var pickingApps by remember { mutableStateOf(false) }
    val editable = !locked
    val result = initial.copy(
        days = days, startMin = start, endMin = end,
        workMin = if (cycles) work else 0, breakMin = pause,
        strict = strict, list = list, useMainList = useMainList, pausedOnVacation = pausedOnVacation,
    )
    val tooLong = !strictChangeAllowed(schedules, schedules.withSchedule(result))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (locked) Text(stringResource(R.string.schedule_locked_notice))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayNames(TextStyle.NARROW, LocalConfiguration.current.locales[0]).forEachIndexed { i, letter ->
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
                            if (list.apps.isEmpty() && !list.allowOnly) list = mainList
                        },
                        label = { Text(stringResource(R.string.custom_list)) },
                        enabled = editable,
                    )
                }
                if (useMainList) {
                    Text(
                        stringResource(R.string.summary_editable, listSummary(mainList)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    OutlinedButton(onClick = { pickingApps = true }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.choose_apps, listSummary(list)))
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
                LabeledSwitch(stringResource(R.string.cycles_switch), cycles, editable) {
                    cycles = it
                    if (it) pausedOnVacation = true // une session de travail est a priori du travail
                }
                LabeledSwitch(stringResource(R.string.work_switch), pausedOnVacation, editable) { pausedOnVacation = it }
                if (cycles) {
                    Stepper(stringResource(R.string.work), work, 5..120, 5, editable) { work = it }
                    Stepper(stringResource(R.string.break_label), pause, 1..60, 1, editable) { pause = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(result) }, enabled = days != 0 && !tooLong) {
                Text(stringResource(R.string.save))
            }
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
            initial = list,
            locked = locked,
            mainList = mainList,
            apps = apps,
            onDismiss = { pickingApps = false },
        ) { list = it; pickingApps = false }
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
    else -> dayNames(TextStyle.SHORT, LocalConfiguration.current.locales[0]).filterIndexed { i, _ -> (days and (1 shl i)) != 0 }.joinToString(" ")
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
