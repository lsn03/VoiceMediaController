package ru.lsn03.voicemediacontroller.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Exhaustive transition-table pin for [VoiceStateMachine].
 *
 * SWITCHING is intentionally absent — it is a coordinator-local transient flag,
 * never a state the SM exposes. Invalid events return the current state unchanged
 * (no throw, no logging); the coordinator detects the no-op and decides whether
 * to log. These tests pin exactly that contract.
 */
class VoiceStateMachineTest {

    @Test
    fun constructor_default_startsInWakeListening() {
        val sm = VoiceStateMachine()

        assertThat(sm.state).isEqualTo(VoiceState.WAKE_LISTENING)
    }

    @Test
    fun constructor_explicitInitialCommandListening_startsInCommandListening() {
        val sm = VoiceStateMachine(initial = VoiceState.COMMAND_LISTENING)

        assertThat(sm.state).isEqualTo(VoiceState.COMMAND_LISTENING)
    }

    @Test
    fun on_wakeListening_wakeHeard_transitionsToCommandListening() {
        val sm = VoiceStateMachine()

        val next = sm.on(VoiceStateEvent.WakeHeard)

        assertThat(next).isEqualTo(VoiceState.COMMAND_LISTENING)
        assertThat(sm.state).isEqualTo(VoiceState.COMMAND_LISTENING)
    }

    @Test
    fun on_wakeListening_commandRecognised_isNoOpAndReturnsSameState() {
        val sm = VoiceStateMachine()

        val next = sm.on(VoiceStateEvent.CommandRecognised)

        assertThat(next).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(sm.state).isEqualTo(VoiceState.WAKE_LISTENING)
    }

    @Test
    fun on_wakeListening_commandTimeout_isNoOpAndReturnsSameState() {
        val sm = VoiceStateMachine()

        val next = sm.on(VoiceStateEvent.CommandTimeout)

        assertThat(next).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(sm.state).isEqualTo(VoiceState.WAKE_LISTENING)
    }

    @Test
    fun on_wakeListening_reset_staysInWakeListening() {
        val sm = VoiceStateMachine()

        val next = sm.on(VoiceStateEvent.Reset)

        assertThat(next).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(sm.state).isEqualTo(VoiceState.WAKE_LISTENING)
    }

    @Test
    fun on_commandListening_wakeHeard_isNoOpAndStaysInCommandListening() {
        // Pins coder behaviour: a wake while already in COMMAND is rejected
        // by the SM; the coordinator will log "Wake ignored: SM rejected…".
        val sm = VoiceStateMachine(initial = VoiceState.COMMAND_LISTENING)

        val next = sm.on(VoiceStateEvent.WakeHeard)

        assertThat(next).isEqualTo(VoiceState.COMMAND_LISTENING)
        assertThat(sm.state).isEqualTo(VoiceState.COMMAND_LISTENING)
    }

    @Test
    fun on_commandListening_commandRecognised_transitionsToWakeListening() {
        val sm = VoiceStateMachine(initial = VoiceState.COMMAND_LISTENING)

        val next = sm.on(VoiceStateEvent.CommandRecognised)

        assertThat(next).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(sm.state).isEqualTo(VoiceState.WAKE_LISTENING)
    }

    @Test
    fun on_commandListening_commandTimeout_transitionsToWakeListening() {
        val sm = VoiceStateMachine(initial = VoiceState.COMMAND_LISTENING)

        val next = sm.on(VoiceStateEvent.CommandTimeout)

        assertThat(next).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(sm.state).isEqualTo(VoiceState.WAKE_LISTENING)
    }

    @Test
    fun on_commandListening_reset_transitionsToWakeListening() {
        val sm = VoiceStateMachine(initial = VoiceState.COMMAND_LISTENING)

        val next = sm.on(VoiceStateEvent.Reset)

        assertThat(next).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(sm.state).isEqualTo(VoiceState.WAKE_LISTENING)
    }

    @Test
    fun on_validTransition_returnsNewStateNotPrevious() {
        val sm = VoiceStateMachine()
        val previous = sm.state

        val returned = sm.on(VoiceStateEvent.WakeHeard)

        assertThat(returned).isNotEqualTo(previous)
        assertThat(returned).isEqualTo(VoiceState.COMMAND_LISTENING)
    }

    @Test
    fun on_invalidEvent_returnsUnchangedCurrentState() {
        val sm = VoiceStateMachine()
        val before = sm.state

        val returned = sm.on(VoiceStateEvent.CommandTimeout)

        assertThat(returned).isEqualTo(before)
    }

    @Test
    fun fullCycle_wakeThenCommandRecognisedTwice_endsInWakeListening() {
        val sm = VoiceStateMachine()

        sm.on(VoiceStateEvent.WakeHeard)
        sm.on(VoiceStateEvent.CommandRecognised)
        sm.on(VoiceStateEvent.WakeHeard)
        sm.on(VoiceStateEvent.CommandRecognised)

        assertThat(sm.state).isEqualTo(VoiceState.WAKE_LISTENING)
    }

    @Test
    fun resetFromAnyState_alwaysReachesWakeListening() {
        // Reset is unconditional, independent of the current state.
        val fromCommand = VoiceStateMachine(initial = VoiceState.COMMAND_LISTENING)
        val fromWake = VoiceStateMachine(initial = VoiceState.WAKE_LISTENING)

        fromCommand.on(VoiceStateEvent.Reset)
        fromWake.on(VoiceStateEvent.Reset)

        assertThat(fromCommand.state).isEqualTo(VoiceState.WAKE_LISTENING)
        assertThat(fromWake.state).isEqualTo(VoiceState.WAKE_LISTENING)
    }
}
