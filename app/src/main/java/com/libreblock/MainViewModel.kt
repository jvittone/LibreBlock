package com.libreblock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate

/** Ce que l'interface affiche, recalculé à chaque seconde et à chaque changement de réglages. */
data class UiState(
    val settings: Settings,
    val now: Long,
    val actives: List<Active>,
    val today: LocalDate = Instant.ofEpochMilli(now).atZone(TrustedClock.zone).toLocalDate(),
) {
    val vacation get() = settings.vacationOn(today)
    val session get() = actives.firstOrNull { it.isSession }
    val locked get() = actives.locked()
    val mainListLocked get() = actives.mainListLocked()
    val lockedScheduleIds get() = actives.lockedScheduleIds()
}

/**
 * Point d'entrée de l'interface : expose l'état et applique toutes les règles (mode strict,
 * garde-fous) avant d'enregistrer une modification. Les écrans restent sans état.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository.get(app)

    private val ticker = flow {
        while (true) {
            emit(repo.now())
            delay(1000)
        }
    }

    val state: StateFlow<UiState> = combine(repo.settings, ticker) { settings, now ->
        UiState(settings, now, repo.evaluate(now))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currentState())

    private fun currentState(): UiState {
        val now = repo.now()
        return UiState(repo.settings.value, now, repo.evaluate(now))
    }

    /** Refusé si cela débloque une appli alors que la liste principale est verrouillée. */
    fun setMainList(list: AppList): Boolean {
        val current = currentState()
        if (current.mainListLocked && current.settings.mainList.isLoosenedBy(list)) return false
        repo.update { it.copy(mainList = list) }
        return true
    }

    enum class SaveResult { Saved, Locked, StrictTooLong }

    fun saveSchedule(schedule: Schedule): SaveResult {
        val current = currentState()
        val before = current.settings.schedules
        val old = before.firstOrNull { it.id == schedule.id }
        if (old != null && old.id in current.lockedScheduleIds && !onlyStricterList(old, schedule)) {
            return SaveResult.Locked
        }
        val after = before.withSchedule(schedule)
        if (!strictChangeAllowed(before, after)) return SaveResult.StrictTooLong
        repo.update { it.copy(schedules = after) }
        return SaveResult.Saved
    }

    /** Pendant un horaire strict, seule sa liste personnalisée peut changer, et seulement en plus strict. */
    private fun onlyStricterList(old: Schedule, new: Schedule) =
        old.copy(list = new.list) == new && !old.list.isLoosenedBy(new.list)

    fun deleteSchedule(id: Long): Boolean {
        if (id in currentState().lockedScheduleIds) return false
        repo.update { s -> s.copy(schedules = s.schedules.filter { it.id != id }) }
        return true
    }

    /** Refusé si ces vacances arrêteraient un horaire strict en cours. Les vacances passées sont oubliées. */
    fun saveVacation(vacation: Vacation): Boolean = changeVacations { list ->
        list.filter { it.id != vacation.id } + vacation
    }

    fun deleteVacation(id: Long): Boolean = changeVacations { list -> list.filter { it.id != id } }

    private fun changeVacations(change: (List<Vacation>) -> List<Vacation>): Boolean {
        val current = currentState()
        val after = current.settings.copy(
            vacations = change(current.settings.vacations).upcoming(current.today),
        )
        if (!vacationChangeAllowed(current.settings, after, current.now, TrustedClock.zone)) return false
        repo.update { it.copy(vacations = after.vacations) }
        return true
    }

    /** minutes = null : supprime la limite. Une hausse ou une suppression n'agit que le lendemain. */
    fun setLimit(pkg: String, minutes: Int?) = repo.setLimit(pkg, minutes)

    fun startSession(config: SessionConfig) = repo.startSession(config)

    /** Refusé pendant une session stricte. */
    fun stopSession(): Boolean {
        if (currentState().session?.strict == true) return false
        repo.update { it.copy(session = null) }
        return true
    }

    fun finishOnboarding() = repo.update { it.copy(onboardingDone = true) }
}
