package ru.lsn03.voicemediacontroller.ui.screens.home

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.lsn03.voicemediacontroller.core.fakes.MainDispatcherRule
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository
import ru.lsn03.voicemediacontroller.voice.status.RecognizerStatus
import ru.lsn03.voicemediacontroller.voice.status.RecognizerStatusBus

/**
 * Behavioural pin for [HomeViewModel]: proves that the two surfaces the
 * UI consumes — `wakeWord` and `status` — are faithful passthroughs of
 * their respective sources ([WakeWordRepository] and [RecognizerStatusBus]).
 *
 * The ViewModel owns a `stateIn(viewModelScope, ...)` collector, so these
 * tests rely on [MainDispatcherRule] to install an unconfined test
 * dispatcher as `Dispatchers.Main`; without it, `viewModelScope` would
 * refuse to start on the JVM.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeWakeWordRepository(initial: String) : WakeWordRepository {
        val source: MutableStateFlow<String> = MutableStateFlow(initial)
        var setCalls: Int = 0
            private set
        var lastSet: String? = null
            private set

        override fun observe(): Flow<String> = source
        override suspend fun set(newWord: String) {
            setCalls += 1
            lastSet = newWord
            source.value = newWord
        }
        override fun current(): String = source.value
    }

    private fun newViewModel(
        wakeRepo: WakeWordRepository = FakeWakeWordRepository("джарвис"),
        bus: RecognizerStatusBus = RecognizerStatusBus(),
    ): HomeViewModel = HomeViewModel(wakeRepo = wakeRepo, statusBus = bus)

    @Test
    fun status_initial_isIdle() {
        val bus = RecognizerStatusBus()

        val vm = newViewModel(bus = bus)

        assertThat(vm.status.value).isEqualTo(RecognizerStatus.Idle)
    }

    @Test
    fun wakeWord_initial_reflectsRepositoryInitialEmission() = runTest {
        val repo = FakeWakeWordRepository("алиса")

        val vm = newViewModel(wakeRepo = repo)

        // Subscribe once so the WhileSubscribed collector actually starts.
        vm.wakeWord.test {
            assertThat(awaitItem()).isEqualTo("алиса")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun status_busPostWakeAccepted_propagatesToViewModel() = runTest {
        val bus = RecognizerStatusBus()
        val vm = newViewModel(bus = bus)

        vm.status.test {
            assertThat(awaitItem()).isEqualTo(RecognizerStatus.Idle)

            bus.post(RecognizerStatus.WakeAccepted("Джарвис"))
            assertThat(awaitItem())
                .isEqualTo(RecognizerStatus.WakeAccepted("Джарвис"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun wakeWord_repositoryEmitsNewValue_viewModelFlowReflectsIt() = runTest {
        val repo = FakeWakeWordRepository("джарвис")
        val vm = newViewModel(wakeRepo = repo)

        vm.wakeWord.test {
            assertThat(awaitItem()).isEqualTo("джарвис")

            repo.source.value = "алиса"
            assertThat(awaitItem()).isEqualTo("алиса")

            repo.source.value = "окей гугл"
            assertThat(awaitItem()).isEqualTo("окей гугл")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun status_sequenceOfPosts_viewModelObservesSameSequence() = runTest {
        val bus = RecognizerStatusBus()
        val vm = newViewModel(bus = bus)

        vm.status.test {
            assertThat(awaitItem()).isEqualTo(RecognizerStatus.Idle)

            bus.publish("слыш")
            assertThat(awaitItem()).isEqualTo(RecognizerStatus.WakeHint("слыш"))

            bus.post(RecognizerStatus.WakeAccepted("Джарвис"))
            assertThat(awaitItem())
                .isEqualTo(RecognizerStatus.WakeAccepted("Джарвис"))

            bus.post(RecognizerStatus.CommandHint("следу"))
            assertThat(awaitItem())
                .isEqualTo(RecognizerStatus.CommandHint("следу"))

            bus.post(RecognizerStatus.CommandExecuting("следующий трек"))
            assertThat(awaitItem())
                .isEqualTo(RecognizerStatus.CommandExecuting("следующий трек"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun status_busAndViewModelExposeSameStateFlowInstance() {
        // Pin: HomeViewModel.status is `statusBus.status` as-is — not a
        // transformed Flow, not a `stateIn` wrapper. This makes the propagation
        // of StateFlow conflation / replay semantics identical and lets the UI
        // compose a single lifecycle-aware collector.
        val bus = RecognizerStatusBus()

        val vm = newViewModel(bus = bus)

        assertThat(vm.status).isSameInstanceAs(bus.status)
    }
}
