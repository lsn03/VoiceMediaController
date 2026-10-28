package ru.lsn03.voicemediacontroller.core.speech

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.lsn03.voicemediacontroller.core.fakes.RecordingLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * MockWebServer-driven coverage for [HttpUrlConnectionModelDownloader].
 *
 * The downloader streams a ZIP of a Vosk model into
 * `filesDir/models/<locale>.part/` and atomically renames to
 * `filesDir/models/<locale>/`. These tests pin:
 *   - happy path: progress emissions, final layout, staging cleanup.
 *   - 404: `Failed(...)` + no staging left.
 *   - truncated stream: `Failed(...)` + no staging left.
 *   - cancellation: no exception past collector, staging cleaned, no final dir.
 *   - delete: wipes the model dir.
 *
 * Note on checksum mismatch: the production code reads `entry.sha256` from
 * the hard-wired [ModelCatalog] singleton (constructor arg is typed as the
 * singleton type), and the catalog's EN entry currently carries an empty
 * `sha256`. The downloader intentionally skips strict verification when the
 * catalog sha is blank, so the mismatch branch cannot be exercised in a
 * JVM test without a seam. The scenario is covered as `@Ignore` below and
 * flagged for the reviewer in the hand-off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloaderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val logger = RecordingLogger()
    private lateinit var filesDir: File
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        every { context.filesDir } returns filesDir
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun newDownloader(url: String): HttpUrlConnectionModelDownloader =
        HttpUrlConnectionModelDownloader(
            context = context,
            logger = logger,
            connectionFactory = { _ -> java.net.URL(url).openConnection() as HttpURLConnection },
        )

    private fun zipOf(files: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, body) in files) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(body)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun modelsDir(locale: SpeechLocale): File =
        File(File(filesDir, HttpUrlConnectionModelDownloader.MODELS_DIR), locale.tag)

    private fun stagingDir(locale: SpeechLocale): File =
        File(
            File(filesDir, HttpUrlConnectionModelDownloader.MODELS_DIR),
            "${locale.tag}${HttpUrlConnectionModelDownloader.STAGING_SUFFIX}",
        )

    // ---------- happy path ----------

    @Test
    fun download_happyPath_emitsIdleDownloadingVerifyingDone_andInstallsModel() = runTest {
        val payload = zipOf(mapOf(
            "vosk-model-small-en/README" to "readme".toByteArray(),
            "vosk-model-small-en/am/final.mdl" to byteArrayOf(1, 2, 3),
            "vosk-model-small-en/conf/model.conf" to "x".toByteArray(),
        ))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size.toString())
                .setBody(Buffer().write(payload)),
        )
        val url = server.url("/model.zip").toString()
        val downloader = newDownloader(url)

        val progress = downloader.download(SpeechLocale.EN).toList()

        assertThat(progress.first()).isEqualTo(DownloadProgress.Idle)
        assertThat(progress.any { it is DownloadProgress.Downloading }).isTrue()
        assertThat(progress.any { it is DownloadProgress.Verifying }).isTrue()
        assertThat(progress.last()).isEqualTo(DownloadProgress.Done)

        val finalDir = modelsDir(SpeechLocale.EN)
        assertThat(finalDir.exists()).isTrue()
        assertThat(File(finalDir, "vosk-model-small-en/README").readText()).isEqualTo("readme")
        assertThat(File(finalDir, "vosk-model-small-en/am/final.mdl").readBytes())
            .isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(stagingDir(SpeechLocale.EN).exists()).isFalse()
    }

    @Test
    fun download_happyPath_verifyingProgressCarriesComputedSha() = runTest {
        val payload = zipOf(mapOf("a/f" to byteArrayOf(9, 9, 9)))
        val expectedSha = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { String.format("%02x", it) }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(Buffer().write(payload)),
        )
        val downloader = newDownloader(server.url("/model.zip").toString())

        val progress = downloader.download(SpeechLocale.EN).toList()

        val verifying = progress.filterIsInstance<DownloadProgress.Verifying>()
        assertThat(verifying).hasSize(1)
        assertThat(verifying.single().sha).isEqualTo(expectedSha)
    }

    // ---------- error paths ----------

    @Test
    fun download_http404_emitsFailed_andCleansStaging() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val downloader = newDownloader(server.url("/missing.zip").toString())

        val progress = downloader.download(SpeechLocale.EN).toList()

        val failed = progress.last() as DownloadProgress.Failed
        assertThat(failed.reason).contains("404")
        assertThat(stagingDir(SpeechLocale.EN).exists()).isFalse()
        assertThat(modelsDir(SpeechLocale.EN).exists()).isFalse()
    }

    @Test
    fun download_truncatedStream_emitsFailed_andCleansStaging() = runTest {
        // Send a valid 30-byte local file header that advertises a 1024-byte
        // filename, then stop sending. The ZIP parser inside the downloader
        // consumes the header, tries to read the filename, hits EOF, and
        // raises an exception; the catch-all branch cleans up and emits
        // DownloadProgress.Failed.
        val header = ByteArray(30)
        // Local file header signature: PK\003\004
        header[0] = 0x50; header[1] = 0x4B; header[2] = 0x03; header[3] = 0x04
        // Version needed to extract (2 bytes, little-endian)
        header[4] = 0x0A; header[5] = 0x00
        // General purpose bit flag (2) + compression method (2) +
        // last mod time (2) + last mod date (2) + crc32 (4) +
        // compressed size (4) + uncompressed size (4) all zero.
        // File name length (little-endian): 1024
        header[26] = 0x00.toByte(); header[27] = 0x04.toByte()
        // Extra field length: 0
        header[28] = 0x00; header[29] = 0x00
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", header.size.toString())
                .setBody(Buffer().write(header)),
        )
        val downloader = newDownloader(server.url("/trunc.zip").toString())

        val progress = downloader.download(SpeechLocale.EN).toList()

        assertThat(progress.last()).isInstanceOf(DownloadProgress.Failed::class.java)
        assertThat(stagingDir(SpeechLocale.EN).exists()).isFalse()
        assertThat(modelsDir(SpeechLocale.EN).exists()).isFalse()
    }

    @Test
    fun download_unreachableHost_emitsFailed_andCleansStaging() = runTest {
        // Point the downloader at a port we know has nothing listening.
        val closed = MockWebServer().apply { start() }
        val url = closed.url("/x").toString()
        closed.shutdown()
        val downloader = HttpUrlConnectionModelDownloader(
            context = context,
            logger = logger,
            connectionFactory = { _ -> java.net.URL(url).openConnection() as HttpURLConnection },
        )

        val progress = downloader.download(SpeechLocale.EN).toList()

        assertThat(progress.last()).isInstanceOf(DownloadProgress.Failed::class.java)
        assertThat(stagingDir(SpeechLocale.EN).exists()).isFalse()
        assertThat(modelsDir(SpeechLocale.EN).exists()).isFalse()
    }

    @Test
    fun download_ruLocale_hasNoDownloadableUrl_emitsFailed() = runTest {
        // The RU entry in the real catalog has url=null (asset-only), so a
        // download request must short-circuit to Failed before any HTTP work.
        val downloader = HttpUrlConnectionModelDownloader(
            context = context,
            logger = logger,
            connectionFactory = { _ -> error("must not open a connection for asset-only locale") },
        )

        val progress = downloader.download(SpeechLocale.RU).toList()

        val failed = progress.last() as DownloadProgress.Failed
        assertThat(failed.reason).contains("ru")
    }

    @org.junit.Ignore(
        "ModelCatalog is a Kotlin `object` wired by concrete type into the " +
            "downloader constructor, so a test-only catalog with a non-blank sha " +
            "cannot be injected. The EN catalog entry currently carries " +
            "sha256=\"\" (TODO) and the downloader skips strict verification " +
            "when the sha is blank; the checksum-mismatch branch is therefore " +
            "unreachable from JVM tests. Reviewer: convert ModelCatalog to an " +
            "interface (or pass sha explicitly to download(...)) to re-enable."
    )
    @Test
    fun download_checksumMismatch_emitsFailed_andCleansStaging() {
        // Intentionally empty: see @Ignore reason above.
    }

    // ---------- cancellation ----------

    @Test
    fun download_cancelled_doesNotPropagate_andCleansStaging() = runTest {
        // Streams a slow body in chunks so cancellation lands mid-flight.
        val slowBody = Buffer().apply {
            // Enough bytes to force several read loops of the 32KB buffer.
            write(ByteArray(32 * 1024 * 8) { it.toByte() })
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", slowBody.size.toString())
                .setBody(slowBody)
                .throttleBody(1024, 50, java.util.concurrent.TimeUnit.MILLISECONDS),
        )
        val downloader = newDownloader(server.url("/slow.zip").toString())

        // Run on a real IO scope so the throttled read actually blocks and
        // we can cancel it mid-stream. runTest's virtual clock cannot
        // advance through the real socket's wall-clock throttle.
        val scope = CoroutineScope(Dispatchers.IO)
        val seenDownloading = Channel<Unit>(capacity = 1)
        val job = scope.launch {
            downloader.download(SpeechLocale.EN).collect { p ->
                if (p is DownloadProgress.Downloading && p.bytes > 0) {
                    seenDownloading.trySend(Unit)
                }
            }
        }

        seenDownloading.receive() // await mid-flight
        job.cancel()
        job.join()

        assertThat(job.isCancelled).isTrue()
        assertThat(stagingDir(SpeechLocale.EN).exists()).isFalse()
        assertThat(modelsDir(SpeechLocale.EN).exists()).isFalse()
    }

    // ---------- delete ----------

    @Test
    fun delete_removesInstalledModelDir_andReturnsSuccess() = runTest {
        val installed = modelsDir(SpeechLocale.EN).apply { mkdirs() }
        File(installed, "stale.txt").writeText("stale")
        val downloader = newDownloader(server.url("/x").toString())

        val result = downloader.delete(SpeechLocale.EN)

        assertThat(result.isSuccess).isTrue()
        assertThat(installed.exists()).isFalse()
    }

    @Test
    fun delete_whenNothingInstalled_isNoOp_andReturnsSuccess() = runTest {
        val downloader = newDownloader(server.url("/x").toString())

        val result = downloader.delete(SpeechLocale.EN)

        assertThat(result.isSuccess).isTrue()
        assertThat(modelsDir(SpeechLocale.EN).exists()).isFalse()
    }

    @Test
    fun delete_alsoClearsLeftoverStagingDir() = runTest {
        val staging = stagingDir(SpeechLocale.EN).apply { mkdirs() }
        File(staging, "partial").writeText("x")
        val downloader = newDownloader(server.url("/x").toString())

        val result = downloader.delete(SpeechLocale.EN)

        assertThat(result.isSuccess).isTrue()
        assertThat(staging.exists()).isFalse()
    }
}
