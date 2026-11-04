package com.libreblock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun StatsScreen(stats: Map<String, DayStats>, today: LocalDate, apps: List<AppEntry>?) {
    val todayStats = stats.day(today)
    val streak = stats.streak(today)
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val top = stats.topBlocked(today, days = 7, limit = 5)
    val labels = apps.orEmpty().associate { it.pkg to it.label }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.headlineMedium)
        if (stats.isEmpty()) Text(stringResource(R.string.stats_empty))

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.stats_today), style = MaterialTheme.typography.titleMedium)
                Row {
                    StatValue(formatDuration((todayStats.focusSeconds / 60).toInt()), stringResource(R.string.stats_focus))
                    StatValue(todayStats.blocked.toString(), stringResource(R.string.stats_blocked))
                }
                if (streak > 0) Text(pluralStringResource(R.plurals.stats_streak, streak, streak))
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.stats_week), style = MaterialTheme.typography.titleMedium)
                WeekChart(week.map { it to stats.day(it).focusSeconds / 60 })
            }
        }

        if (top.isNotEmpty()) {
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.stats_top_apps), style = MaterialTheme.typography.titleMedium)
                    top.forEach { (pkg, count) ->
                        Row {
                            Text(labels[pkg] ?: pkg, Modifier.weight(1f))
                            Text(pluralStringResource(R.plurals.times_count, count, count))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StatValue(value: String, label: String) {
    Column(Modifier.weight(1f)) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

private val BAR_MAX_HEIGHT = 96.dp

/** Barres des minutes de concentration par jour, du plus ancien au plus récent. */
@Composable
private fun WeekChart(days: List<Pair<LocalDate, Long>>) {
    val max = days.maxOf { it.second }.coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().height(140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { (day, minutes) ->
            val label = formatDuration(minutes.toInt())
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "$day: $label" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (minutes > 0) {
                    Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(BAR_MAX_HEIGHT * (minutes.toFloat() / max).coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, LocalConfiguration.current.locales[0]),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
