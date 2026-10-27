package ru.lsn03.voicemediacontroller.core.fakes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Self-test for the RecordingLogger fixture. */
class RecordingLoggerTest {

    @Test
    fun entries_captureInsertionOrderWithLevelTagAndMessage() {
        val logger = RecordingLogger()

        logger.d("Tag1", "d-message")
        logger.i("Tag2", "i-message")
        logger.w("Tag3", "w-message")
        logger.e("Tag4", "e-message")

        assertThat(logger.entries).containsExactly(
            RecordingLogger.Entry('d', "Tag1", "d-message", cause = null),
            RecordingLogger.Entry('i', "Tag2", "i-message", cause = null),
            RecordingLogger.Entry('w', "Tag3", "w-message", cause = null),
            RecordingLogger.Entry('e', "Tag4", "e-message", cause = null),
        ).inOrder()
    }

    @Test
    fun w_and_e_preserveCauseWhenProvided() {
        val logger = RecordingLogger()
        val warn = IllegalStateException("warn")
        val err = RuntimeException("err")

        logger.w("Tag", "w", warn)
        logger.e("Tag", "e", err)

        assertThat(logger.entries).containsExactly(
            RecordingLogger.Entry('w', "Tag", "w", warn),
            RecordingLogger.Entry('e', "Tag", "e", err),
        ).inOrder()
    }

    @Test
    fun w_and_e_withoutCause_recordNullCause() {
        val logger = RecordingLogger()

        logger.w("Tag", "w-no-cause")
        logger.e("Tag", "e-no-cause")

        assertThat(logger.entries.map { it.cause }).containsExactly(null, null)
    }

    @Test
    fun clear_emptiesTheRecordedEntries() {
        val logger = RecordingLogger()

        logger.d("Tag", "one")
        logger.i("Tag", "two")
        assertThat(logger.entries).hasSize(2)

        logger.clear()
        assertThat(logger.entries).isEmpty()

        logger.i("Tag", "post-clear")
        assertThat(logger.entries).hasSize(1)
    }

    @Test
    fun entries_snapshotIsNotMutatedByLaterLogCalls() {
        val logger = RecordingLogger()

        logger.d("Tag", "first")
        val snapshot = logger.entries

        logger.d("Tag", "second")

        assertThat(snapshot).hasSize(1)
        assertThat(logger.entries).hasSize(2)
    }
}
