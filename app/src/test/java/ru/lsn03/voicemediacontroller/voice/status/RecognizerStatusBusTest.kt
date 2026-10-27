package ru.lsn03.voicemediacontroller.voice.status

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.lsn03.voicemediacontroller.core.text.TextPublisher

/**
 * Behavioural pin for [RecognizerStatusBus].
 *
 * The bus is deliberately small: a `MutableStateFlow` with two write paths
 * (`publish(text)` and `post(status)`). These tests cover the four invariants
 * the PR 5 plan commits to:
 *   1. initial status is [RecognizerStatus.Idle];
 *   2. the raw-text channel maps every string to [RecognizerStatus.WakeHint];
 *   3. typed callers pass through unchanged via [RecognizerStatusBus.post];
 *   4. the bus is the same object as its [TextPublisher] contract — callers
 *      do not need a separate publisher.
 *
 * The last-value conflation test documents (and asserts) that intermediate
 * writes between two `StateFlow` subscriptions are not observable, because
 * this is the exact property the plan relies on when it says "text-publish
 * semantics are latest wins".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecognizerStatusBusTest {

    @Test
    fun status_initialValue_isIdle() {
        val bus = RecognizerStatusBus()

        assertThat(bus.status.value).isEqualTo(RecognizerStatus.Idle)
    }

    @Test
    fun publish_withText_emitsWakeHintWrappingThatText() {
        val bus = RecognizerStatusBus()

        bus.publish("alpha")

        assertThat(bus.status.value).isEqualTo(RecognizerStatus.WakeHint("alpha"))
    }

    @Test
    fun publish_withEmptyString_emitsWakeHintCarryingEmptyString() {
        // Pin: the bus does NOT filter empty strings; it wraps them verbatim.
        // Callers that want to debounce blank partials must do so upstream.
        val bus = RecognizerStatusBus()

        bus.publish("")

        assertThat(bus.status.value).isEqualTo(RecognizerStatus.WakeHint(""))
    }

    @Test
    fun post_commandExecuting_emitsExactValue() {
        val bus = RecognizerStatusBus()

        bus.post(RecognizerStatus.CommandExecuting("следующий трек"))

        assertThat(bus.status.value)
            .isEqualTo(RecognizerStatus.CommandExecuting("следующий трек"))
    }

    @Test
    fun post_error_emitsExactValue() {
        val bus = RecognizerStatusBus()

        bus.post(RecognizerStatus.Error("Нет разрешения на микрофон"))

        assertThat(bus.status.value)
            .isEqualTo(RecognizerStatus.Error("Нет разрешения на микрофон"))
    }

    @Test
    fun post_multiplePosts_turbineObservesExactSequenceInOrder() = runTest {
        val bus = RecognizerStatusBus()

        bus.status.test {
            assertThat(awaitItem()).isEqualTo(RecognizerStatus.Idle)

            bus.publish("а")
            assertThat(awaitItem()).isEqualTo(RecognizerStatus.WakeHint("а"))

            bus.post(RecognizerStatus.CommandExecuting("б"))
            assertThat(awaitItem()).isEqualTo(RecognizerStatus.CommandExecuting("б"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun publish_rapidWrites_stateFlowConflatesToLatestValue() {
        // StateFlow conflation: callers reading `.value` after a burst of
        // writes see only the last-written value. Intermediate writes may
        // (and here, do) get discarded. This is the exact "latest wins"
        // semantics the PR plan calls out for text events.
        val bus = RecognizerStatusBus()

        for (i in 0 until 10) {
            bus.publish("partial-$i")
        }

        assertThat(bus.status.value).isEqualTo(RecognizerStatus.WakeHint("partial-9"))
    }

    @Test
    fun bus_isATextPublisher_andCastedPublishMutatesStatus() {
        // Identity check: the bus does not WRAP a TextPublisher — it IS one.
        // VoiceEffectsImpl is wired against the TextPublisher interface, so a
        // test cast proves the same instance drives both channels.
        val bus = RecognizerStatusBus()
        val asPublisher: TextPublisher = bus

        asPublisher.publish("hello from publisher")

        assertThat(bus.status.value)
            .isEqualTo(RecognizerStatus.WakeHint("hello from publisher"))
    }

    @Test
    fun post_wakeAcceptedThenCommandHint_bothValuesAreObserved() = runTest {
        val bus = RecognizerStatusBus()

        bus.status.test {
            assertThat(awaitItem()).isEqualTo(RecognizerStatus.Idle)

            bus.post(RecognizerStatus.WakeAccepted("Джарвис"))
            assertThat(awaitItem())
                .isEqualTo(RecognizerStatus.WakeAccepted("Джарвис"))

            bus.post(RecognizerStatus.CommandHint("следу"))
            assertThat(awaitItem())
                .isEqualTo(RecognizerStatus.CommandHint("следу"))

            cancelAndIgnoreRemainingEvents()
        }
    }
}
