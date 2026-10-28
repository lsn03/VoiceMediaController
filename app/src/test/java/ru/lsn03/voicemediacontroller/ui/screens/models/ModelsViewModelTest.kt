package ru.lsn03.voicemediacontroller.ui.screens.models

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.lsn03.voicemediacontroller.core.fakes.MainDispatcherRule
import ru.lsn03.voicemediacontroller.core.speech.DownloadProgress
import ru.lsn03.voicemediacontroller.core.speech.ModelCatalog
import ru.lsn03.voicemediacontroller.core.speech.ModelCatalogEntry
import ru.lsn03.voicemediacontroller.core.speech.ModelDownloader
import ru.lsn03.voicemediacontroller.core.speech.ModelLocation
import ru.lsn03.voicemediacontroller.core.speech.ModelProvider
import ru.lsn03.voicemediacontroller.core.speech.SpeechLocale

/**
 * Behavioural tests for [ModelsViewModel].
 *
 * The ViewModel reads the static [ModelCatalog] directly in `init { refresh() }`,
 * so the initial state always has one entry per catalog locale (ru + en). Fakes
 * for [ModelProvider] and [ModelDownloader] script `isAvailable` and
 * `download(...)` to steer the state machine, and Turbine pins the emission
 * order on `state`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeProvider(
        private val available: MutableMap<SpeechLocale, Boolean> = mutableMapOf(),
    ) : ModelProvider {
        override fun isAvailable(locale: SpeechLocale): Boolean = available[locale] ?: false

        override suspend fun resolve(locale: SpeechLocale): Result<ModelLocation> =
            Result.failure(IllegalStateException("not used"))

        fun markInstalled(locale: SpeechLocale, installed: Boolean = true) {
            available[locale] = installed
        }
    }

    private class ScriptedDownloader(
        private val scripts: MutableMap<SpeechLocale, () -> Flow<DownloadProgress>> = mutableMapOf(),
    ) : ModelDownloader {
        var deleteResult: Result<Unit> = Result.success(Unit)
        var lastDeleted: SpeechLocale? = null
            private set
        var deleteCalls: Int = 0
            private set

        var downloadCalls: Int = 0
            private set
        var lastDownloadArgs: Pair<SpeechLocale, ModelCatalogEntry>? = null
            private set

        override fun download(
            locale: SpeechLocale,
            entry: ModelCatalogEntry,
        ): Flow<DownloadProgress> {
            downloadCalls += 1
            lastDownloadArgs = locale to entry
            return scripts[locale]?.invoke()
                ?: flowOf(DownloadProgress.Idle, DownloadProgress.Done)
        }

        override suspend fun delete(locale: SpeechLocale): Result<Unit> {
            deleteCalls += 1
            lastDeleted = locale
            return deleteResult
        }

        fun script(locale: SpeechLocale, factory: () -> Flow<DownloadProgress>) {
            scripts[locale] = factory
        }
    }

    private fun ModelsUiState.itemFor(locale: SpeechLocale): LocaleUi =
        items.first { it.locale == locale }

    @Test
    fun initialState_listsAllCatalogLocales_withStatusDrivenByProvider() = runTest {
        val provider = FakeProvider().apply {
            markInstalled(SpeechLocale.RU, installed = true)
            markInstalled(SpeechLocale.EN, installed = false)
        }
        val downloader = ScriptedDownloader()

        val vm = ModelsViewModel(provider, downloader)

        vm.state.test {
            // init {} launches on the main dispatcher; the initial empty state
            // may or may not be delivered depending on scheduling, so skip
            // any emission whose items are still empty.
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()

            assertThat(state.items.map { it.locale })
                .containsExactlyElementsIn(ModelCatalog.locales())
            // RU has url=null in the catalog -> installed-from-asset.
            val ru = state.itemFor(SpeechLocale.RU)
            assertThat(ru.status).isEqualTo(LocaleUi.Status.InstalledFromAsset)
            assertThat(ru.downloadable).isFalse()
            // EN has a URL and provider says not installed.
            val en = state.itemFor(SpeechLocale.EN)
            assertThat(en.status).isEqualTo(LocaleUi.Status.NotInstalled)
            assertThat(en.downloadable).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onDownload_transitionsThroughDownloading_toDownloaded() = runTest {
        val provider = FakeProvider().apply {
            markInstalled(SpeechLocale.RU, installed = true)
            markInstalled(SpeechLocale.EN, installed = false)
        }
        val channel = Channel<DownloadProgress>(capacity = Channel.UNLIMITED)
        val downloader = ScriptedDownloader().apply {
            script(SpeechLocale.EN) { channel.receiveAsFlow() }
        }

        val vm = ModelsViewModel(provider, downloader)

        vm.state.test {
            // Settle initial refresh.
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()
            assertThat(state.itemFor(SpeechLocale.EN).status)
                .isEqualTo(LocaleUi.Status.NotInstalled)

            vm.onDownload(SpeechLocale.EN)
            channel.send(DownloadProgress.Idle)
            channel.send(DownloadProgress.Downloading(bytes = 10, total = 100))
            // Downloading -> Status.Downloading.
            var downloading = awaitItem()
            while (downloading.itemFor(SpeechLocale.EN).status != LocaleUi.Status.Downloading) {
                downloading = awaitItem()
            }
            assertThat(downloading.itemFor(SpeechLocale.EN).progress)
                .isInstanceOf(DownloadProgress.Downloading::class.java)

            channel.send(DownloadProgress.Verifying(sha = "deadbeef"))
            var verifying = awaitItem()
            while (verifying.itemFor(SpeechLocale.EN).progress !is DownloadProgress.Verifying) {
                verifying = awaitItem()
            }
            assertThat(verifying.itemFor(SpeechLocale.EN).status)
                .isEqualTo(LocaleUi.Status.Downloading)

            // Flip provider.isAvailable BEFORE sending Done, so the trailing
            // refreshLocale() computes the installed status correctly.
            provider.markInstalled(SpeechLocale.EN, installed = true)
            channel.send(DownloadProgress.Done)
            channel.close()

            var done = awaitItem()
            while (done.itemFor(SpeechLocale.EN).status != LocaleUi.Status.Downloaded) {
                done = awaitItem()
            }
            assertThat(done.itemFor(SpeechLocale.EN).status)
                .isEqualTo(LocaleUi.Status.Downloaded)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onDownload_whenDownloaderEmitsFailed_statusBecomesError() = runTest {
        val provider = FakeProvider().apply {
            markInstalled(SpeechLocale.EN, installed = false)
        }
        // Stream progress through a channel so the collector sees each
        // StateFlow update between emissions, instead of letting the
        // viewModelScope coroutine run end-to-end and conflate everything
        // into a single terminal state via the trailing refreshLocale().
        val channel = Channel<DownloadProgress>(capacity = Channel.UNLIMITED)
        val downloader = ScriptedDownloader().apply {
            script(SpeechLocale.EN) { channel.receiveAsFlow() }
        }

        val vm = ModelsViewModel(provider, downloader)

        vm.state.test {
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()

            vm.onDownload(SpeechLocale.EN)
            channel.send(DownloadProgress.Failed("boom"))

            // Wait until the Failed update lands.
            var latest = state
            while (latest.itemFor(SpeechLocale.EN).progress !is DownloadProgress.Failed) {
                latest = awaitItem()
            }
            val en = latest.itemFor(SpeechLocale.EN)
            assertThat(en.status).isEqualTo(LocaleUi.Status.Error)
            assertThat(en.progress).isEqualTo(DownloadProgress.Failed("boom"))

            // Close the flow so the trailing refreshLocale() can run; the
            // ViewModel then settles back to NotInstalled because the fake
            // provider still reports isAvailable == false.
            channel.close()
            var settled = latest
            while (settled.itemFor(SpeechLocale.EN).status != LocaleUi.Status.NotInstalled) {
                settled = awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onDelete_callsDownloaderDelete_andRefreshesStatus() = runTest {
        val provider = FakeProvider().apply {
            markInstalled(SpeechLocale.EN, installed = true)
        }
        val downloader = ScriptedDownloader()

        val vm = ModelsViewModel(provider, downloader)

        vm.state.test {
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()
            assertThat(state.itemFor(SpeechLocale.EN).status)
                .isEqualTo(LocaleUi.Status.Downloaded)

            // After delete, the provider must report "not installed".
            provider.markInstalled(SpeechLocale.EN, installed = false)
            vm.onDelete(SpeechLocale.EN)

            var latest = state
            while (latest.itemFor(SpeechLocale.EN).status != LocaleUi.Status.NotInstalled) {
                latest = awaitItem()
            }
            assertThat(downloader.deleteCalls).isEqualTo(1)
            assertThat(downloader.lastDeleted).isEqualTo(SpeechLocale.EN)
            assertThat(latest.message == null).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onDelete_whenDownloaderFails_surfacesMessageInState() = runTest {
        val provider = FakeProvider().apply {
            markInstalled(SpeechLocale.EN, installed = true)
        }
        val downloader = ScriptedDownloader().apply {
            deleteResult = Result.failure(IllegalStateException("disk full"))
        }

        val vm = ModelsViewModel(provider, downloader)

        vm.state.test {
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()

            vm.onDelete(SpeechLocale.EN)

            var latest = state
            while (latest.message == null) latest = awaitItem()
            assertThat(latest.message).contains("disk full")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onSelect_marksChosenLocale_asSelected_andClearsOthers() = runTest {
        val provider = FakeProvider().apply {
            markInstalled(SpeechLocale.RU, installed = true)
            markInstalled(SpeechLocale.EN, installed = true)
        }
        val downloader = ScriptedDownloader()

        val vm = ModelsViewModel(provider, downloader)

        vm.state.test {
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()
            assertThat(state.items.none { it.selected }).isTrue()

            vm.onSelect(SpeechLocale.EN)
            val selectedEn = awaitItem()
            assertThat(selectedEn.itemFor(SpeechLocale.EN).selected).isTrue()
            assertThat(selectedEn.itemFor(SpeechLocale.RU).selected).isFalse()

            vm.onSelect(SpeechLocale.RU)
            val selectedRu = awaitItem()
            assertThat(selectedRu.itemFor(SpeechLocale.RU).selected).isTrue()
            assertThat(selectedRu.itemFor(SpeechLocale.EN).selected).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onMessageShown_clearsMessage() = runTest {
        val provider = FakeProvider()
        val downloader = ScriptedDownloader().apply {
            deleteResult = Result.failure(RuntimeException("nope"))
        }
        val vm = ModelsViewModel(provider, downloader)

        vm.state.test {
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()

            vm.onDelete(SpeechLocale.EN)
            var withMessage = state
            while (withMessage.message == null) withMessage = awaitItem()
            assertThat(withMessage.message).isNotNull()

            vm.onMessageShown()
            var cleared = withMessage
            while (cleared.message != null) cleared = awaitItem()
            assertThat(cleared.message == null).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @org.junit.Ignore(
        "ModelsViewModel resolves catalog entries via the ModelCatalog " +
            "Kotlin `object` singleton directly (ModelCatalog.entryFor(locale)), " +
            "so a test-only catalog cannot be injected to force the " +
            "entry==null branch. Additionally, init { refresh() } only " +
            "populates `items` from ModelCatalog.locales(), so calling " +
            "onDownload(unknownLocale) hits updateProgress() but the Failed " +
            "status cannot surface in `state.items` because no item carries " +
            "that locale. Reviewer/android-coder follow-up: inject a " +
            "ModelCatalog seam (interface or constructor arg) and either " +
            "add an \"items.add(...)\" step for unknown locales, or surface " +
            "the error via `state.message`."
    )
    @Test
    fun onDownload_whenCatalogHasNoEntry_emitsFailedWithoutDispatching() = runTest {
        val provider = FakeProvider()
        val downloader = ScriptedDownloader()
        val vm = ModelsViewModel(provider, downloader)

        val unknown = SpeechLocale("de")
        vm.onDownload(unknown)

        // Observable half: downloader.download() must not run.
        assertThat(downloader.downloadCalls).isEqualTo(0)
        // Unobservable half (requires the seam described above): the
        // Failed("No catalog entry for locale 'de'") emission is dropped
        // because `items` never contains an entry for "de".
    }

    @Test
    fun onDownload_whileAlreadyActive_isIgnored() = runTest {
        val provider = FakeProvider().apply {
            markInstalled(SpeechLocale.EN, installed = false)
        }
        val callCount = IntArray(1)
        val channel = Channel<DownloadProgress>(capacity = Channel.UNLIMITED)
        val downloader = ScriptedDownloader().apply {
            script(SpeechLocale.EN) {
                callCount[0] += 1
                channel.receiveAsFlow()
            }
        }

        val vm = ModelsViewModel(provider, downloader)

        vm.state.test {
            var state = awaitItem()
            while (state.items.isEmpty()) state = awaitItem()

            vm.onDownload(SpeechLocale.EN)
            // Drive the Downloading emission so the job is clearly active.
            channel.send(DownloadProgress.Downloading(bytes = 1, total = 100))
            var downloading = awaitItem()
            while (downloading.itemFor(SpeechLocale.EN).status != LocaleUi.Status.Downloading) {
                downloading = awaitItem()
            }

            // Second call must be a no-op; downloader.download() must not run twice.
            vm.onDownload(SpeechLocale.EN)
            assertThat(callCount[0]).isEqualTo(1)

            channel.close()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
