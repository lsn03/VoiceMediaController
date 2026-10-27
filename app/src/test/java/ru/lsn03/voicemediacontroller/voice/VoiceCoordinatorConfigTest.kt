package ru.lsn03.voicemediacontroller.voice

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pins the three timing constants shipped in [VoiceCoordinatorConfig.default]. */
class VoiceCoordinatorConfigTest {

    @Test
    fun default_values_matchPlanConstants() {
        val cfg = VoiceCoordinatorConfig.default()

        assertThat(cfg.uiThrottleMs).isEqualTo(250L)
        assertThat(cfg.wakeDebounceMs).isEqualTo(1200L)
        assertThat(cfg.commandTimeoutMs).isEqualTo(10_000L)
    }

    @Test
    fun copy_overridesOneField_otherTwoPreserved() {
        val base = VoiceCoordinatorConfig.default()

        val overridden = base.copy(commandTimeoutMs = 42L)

        assertThat(overridden.commandTimeoutMs).isEqualTo(42L)
        assertThat(overridden.uiThrottleMs).isEqualTo(base.uiThrottleMs)
        assertThat(overridden.wakeDebounceMs).isEqualTo(base.wakeDebounceMs)
    }
}
