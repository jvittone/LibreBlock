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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Pour prévenir des débuts et fins de pause.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val store = Store(this)
        setContent { LibreBlockTheme { App(store) } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Accueil", Icons.Default.Home),
    Apps("Applis", Icons.AutoMirrored.Filled.List),
    Schedules("Horaires", Icons.Default.DateRange),
}

@Composable
private fun App(store: Store) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    // Incrémenté à chaque modification du Store pour relire les valeurs.
    var rev by remember { mutableIntStateOf(0) }
    val onChange: () -> Unit = { rev++ }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }
    val locked = remember(rev, now) { store.isLocked(now) }
    val mainListLocked = remember(rev, now) { store.mainListLocked(now) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.Home -> HomeScreen(store, now, rev, locked, onChange)
                Tab.Apps -> AppsScreen(store, rev, mainListLocked, onChange)
                Tab.Schedules -> SchedulesScreen(store, rev, now, onChange)
            }
        }
    }
}

@Composable
private fun HomeScreen(store: Store, now: Long, rev: Int, locked: Boolean, onChange: () -> Unit) {
    val context = LocalContext.current
    val serviceOn = remember(now) { BlockerService.isEnabled(context) }
    val blockedCount = remember(rev) { store.blockedApps.size }
    val actives = remember(rev, now) { store.active(now) }
    val session = actives.firstOrNull { it.isSession }
    val schedule = actives.filter { !it.isSession }.maxByOrNull { it.end }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("LibreBlock", style = MaterialTheme.typography.headlineMedium)

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
                    when {
                        blocking -> "Blocage actif"
                        actives.isNotEmpty() -> "Pause"
                        else -> "Aucun blocage en cours"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text("$blockedCount appli(s) dans la liste principale")
                if (schedule != null) {
                    Text(
                        "Horaire ${if (schedule.strict) "strict" else "souple"} en cours jusqu'à " +
                            "${formatClock(schedule.end)} · ${schedule.apps.size} appli(s)"
                    )
                    if (schedule.cycles) PhaseText(schedule, now)
                }
                if (locked) Text("🔒 Mode strict : impossible de désactiver ou désinstaller LibreBlock")
            }
        }

        // Session manuelle
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session != null) {
                    Text(
                        if (session.cycles) "Session de travail" else "Session de concentration",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (session.cycles) PhaseText(session, now, MaterialTheme.typography.headlineSmall)
                    Text(
                        "Reste ${formatCountdown(session.end - now)} · fin à ${formatClock(session.end)}" +
                            if (session.strict) " · mode strict" else "",
                        style = if (session.cycles) MaterialTheme.typography.bodyMedium
                        else MaterialTheme.typography.headlineSmall,
                    )
                    OutlinedButton(
                        onClick = { store.sessionEnd = 0L; onChange() },
                        enabled = !session.strict,
                    ) { Text("Arrêter la session") }
                } else {
                    NewSession(store, blockedCount, onChange)
                }
            }
        }

        PrivacyCard()
    }
}

@Composable
private fun NewSession(store: Store, blockedCount: Int, onChange: () -> Unit) {
    var cycles by rememberSaveable { mutableStateOf(false) }
    var minutes by rememberSaveable { mutableFloatStateOf(30f) }
    var work by rememberSaveable { mutableIntStateOf(30) }
    var pause by rememberSaveable { mutableIntStateOf(5) }
    var strict by rememberSaveable { mutableStateOf(false) }

    Text("Nouvelle session", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !cycles, onClick = { cycles = false }, label = { Text("Blocage continu") })
        FilterChip(selected = cycles, onClick = { cycles = true }, label = { Text("Travail + pauses") })
    }
    Text("Durée totale : ${formatDuration(minutes.toInt())}")
    Slider(
        value = minutes,
        onValueChange = { minutes = it },
        valueRange = 5f..480f,
        steps = 94, // pas de 5 min
    )
    if (cycles) {
        Stepper("Travail", work, 5..120, 5) { work = it }
        Stepper("Pause", pause, 1..60, 1) { pause = it }
    }
    LabeledSwitch("Mode strict (impossible d'arrêter avant la fin)", strict) { strict = it }
    Button(
        onClick = {
            store.startSession(minutes.toInt(), strict, if (cycles) work else 0, pause)
            onChange()
        },
        enabled = blockedCount > 0,
    ) { Text("Démarrer") }
    if (blockedCount == 0) Text("Choisis d'abord des applis dans l'onglet Applis.")
}

/** « Travail — pause dans 12:34 » ou « Pause — reprise dans 3:21 ». */
@Composable
private fun PhaseText(a: Active, now: Long, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val left = formatCountdown(a.phaseEnd - now)
    Text(
        when {
            !a.blocking -> "☕ Pause — reprise dans $left"
            a.phaseEnd < a.end -> "Travail — pause dans $left"
            else -> "Travail — dernière ligne droite"
        },
        style = style,
    )
}

@Composable
private fun ServiceCard(context: Context) {
    var disclosure by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Le service de blocage est désactivé", style = MaterialTheme.typography.titleMedium)
            Text("Active « LibreBlock » dans les réglages d'accessibilité pour que le blocage fonctionne.")
            Button(onClick = { disclosure = true }) {
                Text("Activer le blocage")
            }
            Text(
                "Option grisée ? (Android 13+) Va dans Infos de l'appli → menu ⋮ → « Autoriser les paramètres restreints », puis réessaie.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }) { Text("Infos de l'appli") }
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
        title = { Text("Utilisation de l'accessibilité") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "LibreBlock utilise l'API d'accessibilité d'Android (AccessibilityService) pour bloquer " +
                        "les applis que tu as choisies."
                )
                Text("Ce que LibreBlock fait avec cette autorisation :", style = MaterialTheme.typography.titleSmall)
                Text("• détecter quelle appli est ouverte, pour afficher l'écran de blocage si elle est bloquée ;")
                Text(
                    "• en mode strict uniquement, lire le texte des écrans Paramètres et de désinstallation " +
                        "pour refermer ceux qui permettraient de désactiver ou de désinstaller LibreBlock."
                )
                Text("Ce que LibreBlock ne fait pas :", style = MaterialTheme.typography.titleSmall)
                Text(
                    "• aucune donnée n'est collectée, enregistrée ni envoyée : l'appli n'a pas accès à Internet ;\n" +
                        "• le contenu des autres applis n'est ni lu ni conservé."
                )
                Text("Tu peux désactiver ce service à tout moment dans les réglages d'accessibilité (hors mode strict).")
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("J'accepte") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("Refuser") } },
    )
}

@Composable
private fun PrivacyCard() {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Confidentialité", style = MaterialTheme.typography.titleMedium)
            Text(
                "LibreBlock n'a pas accès à Internet et ne collecte aucune donnée. Tes réglages restent sur ce téléphone.",
                style = MaterialTheme.typography.bodySmall,
            )
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
        Text("$value min", Modifier.width(64.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = enabled && value < range.last) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
