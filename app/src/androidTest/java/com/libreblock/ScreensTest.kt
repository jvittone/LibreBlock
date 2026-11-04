package com.libreblock

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.libreblock.MainViewModel.SaveResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Tests d'interface : les écrans sans état sont pilotés avec des données factices.
 * À lancer sur la copie séparée, que Gradle désinstalle ensuite : gradlew connectedDebugAndroidTest -Ppreview
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private val youtube = "com.google.android.youtube"
    private val maps = "com.google.android.apps.maps"
    private val apps = listOf(AppEntry(youtube, "YouTube"), AppEntry(maps, "Maps"))

    @Test
    fun lockedAllowListCanOnlyBecomeStricter() {
        var list by mutableStateOf(AppList(setOf(maps), allowOnly = true))
        compose.setContent { LibreBlockTheme { AppPicker(list, locked = true, apps = apps) { list = it } } }

        compose.onNodeWithText(str(R.string.mode_block_selected)).assertIsNotEnabled()
        compose.onNodeWithText("YouTube").performClick() // l'autoriser débloquerait YouTube : refusé
        assertEquals(setOf(maps), list.apps)
        compose.onNodeWithText("Maps").performClick() // retirer une appli autorisée : accepté
        assertEquals(emptySet<String>(), list.apps)
    }

    @Test
    fun switchingToBlockAllExceptKeepsTheSelection() {
        var list by mutableStateOf(AppList(setOf(maps)))
        compose.setContent { LibreBlockTheme { AppPicker(list, locked = false, apps = apps) { list = it } } }

        compose.onNodeWithText(str(R.string.mode_allow_only)).performClick()
        assertEquals(AppList(setOf(maps), allowOnly = true), list)
        compose.onNodeWithText(str(R.string.allow_only_hint)).assertExists()
    }

    private fun stateWith(schedule: Schedule, running: Boolean): UiState {
        val actives = if (running) {
            listOf(Active(2_000, true, 2_000, schedule.strict, false, schedule.list, schedule.id, false))
        } else emptyList()
        return UiState(Settings(schedules = listOf(schedule)), now = 1_000, actives = actives)
    }

    @Test
    fun runningStrictScheduleCannotBeDeleted() {
        var deleted = false
        val schedule = Schedule(1, 0b1111111, 9 * 60, 12 * 60, strict = true)
        compose.setContent {
            LibreBlockTheme {
                SchedulesScreen(stateWith(schedule, running = true), apps, { SaveResult.Saved }) { deleted = true; true }
            }
        }
        compose.onAllNodesWithContentDescription(str(R.string.delete))[0].assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.locked_running)).assertExists()
        assertFalse(deleted)
    }

    @Test
    fun flexibleScheduleCanBeDeleted() {
        val schedule = Schedule(1, 0b1111111, 9 * 60, 12 * 60, strict = false)
        compose.setContent {
            LibreBlockTheme { SchedulesScreen(stateWith(schedule, running = true), apps, { SaveResult.Saved }) { true } }
        }
        compose.onAllNodesWithContentDescription(str(R.string.delete))[0].assertIsEnabled()
    }

    @Test
    fun roundTheClockStrictScheduleCannotBeSaved() {
        var saved = false
        val schedule = Schedule(1, 0b1111111, 6 * 60, 6 * 60, strict = true)
        compose.setContent {
            LibreBlockTheme {
                ScheduleDialog(schedule, locked = false, mainList = AppList(), schedules = emptyList(), apps = apps,
                    onDismiss = {}) { saved = true }
            }
        }
        compose.onNodeWithText(str(R.string.error_strict_too_long)).assertExists()
        compose.onNodeWithText(str(R.string.save)).assertIsNotEnabled()
        assertFalse(saved)
    }

    @Test
    fun cannotStartSessionWithEmptyBlockList() {
        compose.setContent {
            LibreBlockTheme { HomeScreen(UiState(Settings(), 1_000, emptyList()), serviceOn = true, onStart = {}, onStop = {}) }
        }
        compose.onNodeWithText(str(R.string.start)).assertIsNotEnabled()
        compose.onNodeWithText(str(R.string.choose_apps_first)).assertExists()
    }

    @Test
    fun emptyAllowListCanStartSession() {
        var started: SessionConfig? = null
        val settings = Settings(mainList = AppList(allowOnly = true))
        compose.setContent {
            LibreBlockTheme { HomeScreen(UiState(settings, 1_000, emptyList()), serviceOn = true, onStart = { started = it }, onStop = {}) }
        }
        compose.onNodeWithText(str(R.string.start)).assertIsEnabled().performClick()
        assertEquals(SessionConfig(), started) // 25 min par défaut
    }

    @Test
    fun statsShowTodayStreakAndTopApps() {
        val today = LocalDate.of(2026, 11, 4)
        var settings = Settings().recordBlock(youtube, today).recordBlock(youtube, today)
        for (i in 0..2) settings = settings.recordFocus(1_500, today.minusDays(i.toLong()))
        compose.setContent { LibreBlockTheme { StatsScreen(settings.stats, today, apps) } }

        // Concentration du jour, puis les barres des 3 derniers jours.
        compose.onAllNodesWithText("25 min").assertCountEquals(4)
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.stats_streak, 3, 3)).assertExists()
        compose.onNodeWithText("YouTube").assertExists()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.times_count, 2, 2)).assertExists()
    }
}
