package com.libreblock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Pour prévenir des pauses ; au premier lancement, c'est l'écran d'accueil qui le demande.
        if (viewModel.state.value.settings.onboardingDone &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            LibreBlockTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val apps = rememberInstalledApps()
                if (state.settings.onboardingDone) {
                    App(state, apps, viewModel)
                } else {
                    OnboardingScreen(state.settings.mainList, apps, { viewModel.setMainList(it) }, viewModel::finishOnboarding)
                }
            }
        }
    }
}

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Default.Home),
    Apps(R.string.tab_apps, Icons.AutoMirrored.Filled.List),
    Schedules(R.string.tab_schedules, Icons.Default.DateRange),
    Stats(R.string.tab_stats, Icons.Default.Star),
}

@Composable
private fun App(state: UiState, apps: List<AppEntry>?, viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Home -> HomeScreen(
                    state,
                    serviceOn = remember(state.now) { BlockerService.isEnabled(context) },
                    onStart = viewModel::startSession,
                    onStop = { viewModel.stopSession() },
                )
                Tab.Apps -> AppsScreen(state.settings.mainList, state.mainListLocked, apps) { viewModel.setMainList(it) }
                Tab.Schedules -> SchedulesScreen(
                    state, apps,
                    viewModel::saveSchedule, viewModel::deleteSchedule,
                    viewModel::saveVacation, viewModel::deleteVacation,
                )
                Tab.Stats -> StatsScreen(
                    state.settings.stats,
                    Instant.ofEpochMilli(state.now).atZone(TrustedClock.zone).toLocalDate(),
                    apps,
                )
            }
        }
    }
}

@Composable
internal fun HomeScreen(state: UiState, serviceOn: Boolean, onStart: (SessionConfig) -> Unit, onStop: () -> Unit) {
    val context = LocalContext.current
    val actives = state.actives
    val session = state.session
    val schedule = actives.filter { !it.isSession }.maxByOrNull { it.end }
    val mainList = state.settings.mainList

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

        if (!serviceOn) ServiceCard(context)

        // État actuel
        val blocking = actives.any { it.blocking }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (blocking) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        when {
                            blocking -> R.string.status_blocking
                            actives.isNotEmpty() -> R.string.status_break
                            else -> R.string.status_idle
                        }
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(stringResource(R.string.main_list_summary, listSummary(mainList)))
                state.vacation?.let {
                    Text(stringResource(R.string.on_vacation, formatDate(it.end, LocalConfiguration.current.locales[0])))
                }
                if (schedule != null) {
                    Text(
                        stringResource(
                            if (schedule.strict) R.string.schedule_running_strict else R.string.schedule_running_flexible,
                            formatClock(schedule.end),
                        )
                    )
                    if (schedule.cycles) PhaseText(schedule, state.now)
                }
                if (state.locked) Text(stringResource(R.string.strict_locked_notice))
            }
        }

        // Session manuelle
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session != null) {
                    Text(
                        stringResource(if (session.cycles) R.string.session_work else R.string.session_focus),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (session.cycles) PhaseText(session, state.now, MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.session_remaining, formatCountdown(session.end - state.now), formatClock(session.end)) +
                            if (session.strict) stringResource(R.string.session_strict_suffix) else "",
                        style = if (session.cycles) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.headlineSmall,
                    )
                    OutlinedButton(onClick = onStop, enabled = !session.strict) {
                        Text(stringResource(R.string.stop_session))
                    }
                } else {
                    // En mode « tout bloquer sauf », une liste vide bloque tout : c'est valide.
                    NewSession(state.settings.lastSession, canStart = mainList.allowOnly || mainList.apps.isNotEmpty(), onStart)
                }
            }
        }

        PrivacyCard()
    }
}

@Composable
private fun NewSession(last: SessionConfig, canStart: Boolean, onStart: (SessionConfig) -> Unit) {
    var cycles by rememberSaveable { mutableStateOf(last.workMin > 0) }
    var minutes by rememberSaveable { mutableFloatStateOf(last.minutes.toFloat().coerceIn(5f, 480f)) }
    var work by rememberSaveable { mutableIntStateOf(if (last.workMin > 0) last.workMin else 25) }
    var pause by rememberSaveable { mutableIntStateOf(last.breakMin) }
    var strict by rememberSaveable { mutableStateOf(last.strict) }

    Text(stringResource(R.string.new_session), style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !cycles,
            onClick = { cycles = false },
            label = { Text(stringResource(R.string.mode_continuous)) },
        )
        FilterChip(
            selected = cycles,
            onClick = { cycles = true },
            label = { Text(stringResource(R.string.mode_cycles)) },
        )
    }
    Text(stringResource(R.string.total_duration, formatDuration(minutes.toInt())))
    Slider(
        value = minutes,
        onValueChange = { minutes = it },
        valueRange = 5f..480f,
        steps = 94, // pas de 5 min
    )
    if (cycles) {
        Stepper(stringResource(R.string.work), work, 5..120, 5) { work = it }
        Stepper(stringResource(R.string.break_label), pause, 1..60, 1) { pause = it }
    }
    LabeledSwitch(stringResource(R.string.strict_session_switch), strict) { strict = it }
    Button(
        onClick = { onStart(SessionConfig(minutes.toInt(), strict, if (cycles) work else 0, pause)) },
        enabled = canStart,
    ) { Text(stringResource(R.string.start)) }
    if (!canStart) Text(stringResource(R.string.choose_apps_first))
}

/** « Travail — pause dans 12:34 » ou « Pause — reprise dans 3:21 ». */
@Composable
private fun PhaseText(a: Active, now: Long, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val left = formatCountdown(a.phaseEnd - now)
    Text(
        when {
            !a.blocking -> stringResource(R.string.phase_break, left)
            a.phaseEnd < a.end -> stringResource(R.string.phase_work, left)
            else -> stringResource(R.string.phase_last)
        },
        style = style,
    )
}

@Composable
private fun ServiceCard(context: Context) {
    var disclosure by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.service_off_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.service_off_text))
            Button(onClick = { disclosure = true }) {
                Text(stringResource(R.string.enable_blocking))
            }
            Text(stringResource(R.string.restricted_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }) { Text(stringResource(R.string.app_info)) }
        }
    }
    if (disclosure) {
        AccessibilityDisclosure(
            onAccept = {
                disclosure = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDecline = { disclosure = false },
        )
    }
}

/**
 * Information exigée par Google Play avant d'activer un service d'accessibilité
 * qui n'est pas un outil d'accessibilité : ce qui est lu, pourquoi, et consentement explicite.
 */
@Composable
private fun AccessibilityDisclosure(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.disclosure_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.disclosure_intro))
                Text(stringResource(R.string.disclosure_does_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.disclosure_does_1))
                Text(stringResource(R.string.disclosure_does_2))
                Text(stringResource(R.string.disclosure_doesnt_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.disclosure_doesnt))
                Text(stringResource(R.string.disclosure_disable))
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.accept)) } },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.decline)) } },
    )
}

@Composable
private fun PrivacyCard() {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** Réglage d'une durée en minutes avec des boutons − / +. */
@Composable
fun Stepper(
    label: String, value: Int, range: IntRange, step: Int, enabled: Boolean = true, onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = enabled && value > range.first) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(stringResource(R.string.minutes_value, value), Modifier.width(64.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = enabled && value < range.last) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
