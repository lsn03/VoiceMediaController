package ru.lsn03.voicemediacontroller.core.fakes

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit 4 rule that installs a test [CoroutineDispatcher] as `Dispatchers.Main`
 * for the duration of a single test method.
 *
 * ViewModels created in production use `viewModelScope`, which is backed by
 * `Dispatchers.Main.immediate`. On the JVM there is no main looper, so touching
 * that scope without first calling [Dispatchers.setMain] throws. This rule
 * handles setup and teardown so tests remain hermetic.
 *
 * [UnconfinedTestDispatcher] runs child coroutines eagerly on the caller
 * thread, which keeps `stateIn` collectors active and makes `StateFlow`
 * updates observable synchronously from the test body — the exact semantics
 * `HomeViewModelTest` relies on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
