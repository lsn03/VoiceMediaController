package ru.lsn03.voicemediacontroller.service.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository

/**
 * Robolectric is required because [VoiceNotificationController] uses
 * `android.app.NotificationManager`, `NotificationChannel` and
 * `NotificationCompat.Builder` — all real Android types that the JVM
 * classpath exposes only as stubs. SDK 33 is pinned to match the rest
 * of the Robolectric-driven suite (`HandlerSchedulerTest`,
 * `GrammarRepositoryTest`).
 *
 * These tests pin:
 *   * channel creation is idempotent;
 *   * title and body text format exactly as shipped (Russian strings with
 *     the wake word title-cased);
 *   * refresh() posts a notification through the real
 *     [NotificationManager] under `NOTIF_ID = 1`;
 *   * blank input falls back to [WakeWordRepository.DEFAULT_WAKE_WORD]
 *     before title-casing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class VoiceNotificationControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val channelId = "voice_channel_test"
    private val controller = VoiceNotificationController(context, channelId)

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test
    fun ensureChannel_createsTheChannelOnFirstCall() {
        controller.ensureChannel()

        val channel = notificationManager.getNotificationChannel(channelId)
        assertThat(channel).isNotNull()
        assertThat(channel.id).isEqualTo(channelId)
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun ensureChannel_calledTwice_doesNotThrow_andLeavesSingleChannel() {
        controller.ensureChannel()
        controller.ensureChannel()

        // SDK 26+ `createNotificationChannel` is idempotent on channel id;
        // the second call must not throw and the channel must still exist.
        val channel = notificationManager.getNotificationChannel(channelId)
        assertThat(channel).isNotNull()
    }

    @Test
    fun build_withPlainWakeWord_rendersTitleWithTitleCasedWake() {
        controller.ensureChannel()

        val notif: Notification = controller.build("джарвис")

        val title = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        assertThat(title).isEqualTo("🎤 Слушает Джарвис")
    }

    @Test
    fun build_withPlainWakeWord_rendersBodyWithTitleCasedWake() {
        controller.ensureChannel()

        val notif: Notification = controller.build("джарвис")

        val body = notif.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertThat(body).isEqualTo("Говори 'Джарвис, следующий трек'")
    }

    @Test
    fun build_withSurroundingWhitespace_trimsBeforeTitleCasing() {
        controller.ensureChannel()

        val notif: Notification = controller.build("  алиса  ")

        val title = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = notif.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        assertThat(title).isEqualTo("🎤 Слушает Алиса")
        assertThat(body).isEqualTo("Говори 'Алиса, следующий трек'")
    }

    @Test
    fun build_withBlankInput_fallsBackToDefaultWakeWordTitleCased() {
        controller.ensureChannel()

        val notif: Notification = controller.build("   ")

        val title = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val expectedTitle = "🎤 Слушает " +
            WakeWordRepository.DEFAULT_WAKE_WORD.replaceFirstChar { it.uppercaseChar() }
        assertThat(title).isEqualTo(expectedTitle)
    }

    @Test
    fun build_setsSmallIconPriorityOngoingAndOnlyAlertOnceFlags() {
        controller.ensureChannel()

        val notif: Notification = controller.build("джарвис")

        // ongoing -> FLAG_ONGOING_EVENT must be set.
        assertThat(notif.flags and Notification.FLAG_ONGOING_EVENT)
            .isEqualTo(Notification.FLAG_ONGOING_EVENT)
        // onlyAlertOnce -> FLAG_ONLY_ALERT_ONCE must be set.
        assertThat(notif.flags and Notification.FLAG_ONLY_ALERT_ONCE)
            .isEqualTo(Notification.FLAG_ONLY_ALERT_ONCE)
        // Small icon is the system "speak now" drawable.
        assertThat(notif.smallIcon.resId).isEqualTo(android.R.drawable.ic_btn_speak_now)
    }

    @Test
    fun refresh_postsNotificationUnderNotifId() {
        controller.ensureChannel()

        controller.refresh("алиса")

        val active = shadowOf(notificationManager).activeNotifications
        assertThat(active).hasLength(1)
        assertThat(active.single().id).isEqualTo(VoiceNotificationController.NOTIF_ID)
        assertThat(active.single().id).isEqualTo(1)
        val title = active.single().notification.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
        assertThat(title).isEqualTo("🎤 Слушает Алиса")
    }

    @Test
    fun refresh_calledTwice_latestWakeWordWins_andOnlyOneActiveNotification() {
        controller.ensureChannel()

        controller.refresh("джарвис")
        controller.refresh("алиса")

        val active = shadowOf(notificationManager).activeNotifications
        assertThat(active).hasLength(1)
        val title = active.single().notification.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
        assertThat(title).isEqualTo("🎤 Слушает Алиса")
    }
}
