package com.libreblock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Écran affiché à la place d'une appli bloquée. */
class BlockActivity : ComponentActivity() {

    private var pkg by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            LibreBlockTheme {
                BlockScreen(appLabel(pkg), reason(pkg), onClose = ::goHome)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun reason(pkg: String): String {
        val a = SettingsRepository.get(this).evaluate()
            .filter { it.blocking && it.list.blocks(pkg) }
            .maxByOrNull { it.phaseEnd }
        return when {
            a == null -> getString(R.string.reason_ended)
            a.cycles && a.phaseEnd < a.end -> getString(R.string.reason_work_next_break, formatClock(a.phaseEnd))
            a.cycles -> getString(R.string.reason_work_until, formatClock(a.end))
            a.isSession -> getString(R.string.reason_focus_until, formatClock(a.end))
            else -> getString(R.string.reason_schedule_until, formatClock(a.end))
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "package"
    }
}

@Composable
private fun BlockScreen(label: String, reason: String, onClose: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, Modifier.size(72.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.blocked_title, label),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(reason, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(onClick = onClose) { Text(stringResource(R.string.back_home)) }
        }
    }
}
