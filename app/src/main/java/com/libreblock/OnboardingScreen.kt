package com.libreblock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val STEPS = 4

/** Premier lancement : présentation, activation du service et choix des premières applis. */
@Composable
fun OnboardingScreen(
    mainList: AppList,
    apps: List<AppEntry>?,
    onListChange: (AppList) -> Unit,
    onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> HowItWorksStep()
                    2 -> EnableStep()
                    else -> AppsStep(mainList, apps, onListChange)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(STEPS) { i ->
                    val color = if (i == step) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.onboarding_back)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (step < STEPS - 1) step++ else onDone()
                }) {
                    Text(stringResource(if (step < STEPS - 1) R.string.onboarding_next else R.string.onboarding_finish))
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(24.dp))
        Text(
            stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            stringResource(R.string.onboarding_welcome_text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HowItWorksStep() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.onboarding_how_title), style = MaterialTheme.typography.headlineMedium)
        Feature("⏱", R.string.onboarding_sessions_title, R.string.onboarding_sessions_text)
        Feature("⏰", R.string.onboarding_schedules_title, R.string.onboarding_schedules_text)
        Feature("🔒", R.string.onboarding_strict_title, R.string.onboarding_strict_text)
    }
}

@Composable
private fun Feature(emoji: String, title: Int, text: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(emoji, Modifier.width(48.dp), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Activation du service d'accessibilité, précédée de l'information exigée par Google Play :
 * le bouton « J'accepte et j'active » vaut consentement explicite.
 */
@Composable
private fun EnableStep() {
    val context = LocalContext.current
    // Rafraîchi chaque seconde pour voir l'activation au retour des réglages.
    val tick by produceState(0) { while (true) { delay(1000); value++ } }
    val serviceOn = remember(tick) { BlockerService.isEnabled(context) }
    var notificationsOn by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = it
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.onboarding_enable_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.disclosure_intro))
        Text(stringResource(R.string.disclosure_does_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.disclosure_does_1))
        Text(stringResource(R.string.disclosure_does_2))
        Text(stringResource(R.string.disclosure_doesnt_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.disclosure_doesnt))
        if (serviceOn) {
            Text(
                stringResource(R.string.onboarding_enabled),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text(stringResource(R.string.onboarding_accept_enable))
            }
            Text(stringResource(R.string.restricted_hint), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.onboarding_notifications_text), Modifier.padding(top = 8.dp))
        if (notificationsOn) {
            Text(stringResource(R.string.onboarding_notifications_on), color = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text(stringResource(R.string.onboarding_notifications_button))
            }
        }
    }
}

@Composable
private fun AppsStep(mainList: AppList, apps: List<AppEntry>?, onListChange: (AppList) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(R.string.onboarding_apps_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_apps_text), Modifier.padding(top = 8.dp))
        AppPicker(mainList, locked = false, apps = apps, onChange = onListChange)
    }
}
