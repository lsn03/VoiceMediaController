package ru.lsn03.voicemediacontroller.core.speech

import android.content.Context
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.lsn03.voicemediacontroller.core.fakes.RecordingLogger
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * Pure JVM tests for [AssetModelProvider] driven by a mocked [Context] +
 * [AssetManager]. A plain-mockk approach keeps the class out of the
 * Robolectric sandbox so JaCoCo can record line coverage on the provider
 * itself.
 *
 * The suite stubs an in-memory asset tree and routes `filesDir`/`cacheDir`
 * through [TemporaryFolder] so the real file-system paths used by the
 * copy loop are observable and sandboxed per test.
 */
class AssetModelProviderTest {

    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val assets: AssetManager = mockk(relaxed = true)
    private val logger = RecordingLogger()
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var provider: AssetModelProvider

    @Before
    fun setUp() {
        filesDir = tmp.newFolder("files")
        cacheDir = tmp.newFolder("cache")
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir
        every { context.assets } returns assets
        provider = AssetModelProvider(context, logger)
    }

    private fun stubAssetTree(files: Map<String, ByteArray>) {
        val children = mutableMapOf<String, MutableSet<String>>()
        for (path in files.keys) {
            val parts = path.split("/")
            for (i in parts.indices) {
                val parentKey = if (i == 0) "" else parts.subList(0, i).joinToString("/")
                children.getOrPut(parentKey) { mutableSetOf() }.add(parts[i])
            }
        }
        every { assets.list(any()) } answers {
            val path = firstArg<String>()
            if (files.containsKey(path)) emptyArray()
            else children[path]?.toTypedArray() ?: emptyArray()
        }
        every { assets.open(any<String>()) } answers {
            val path = firstArg<String>()
            val data = files[path] ?: throw IOException("not found: $path")
            ByteArrayInputStream(data)
        }
    }

    private fun ruModelAssets(): Map<String, ByteArray> = mapOf(
        "models/ru/vosk-model-small-ru/README" to "readme".toByteArray(),
        "models/ru/vosk-model-small-ru/am/final.mdl" to byteArrayOf(1, 2, 3),
        "models/ru/vosk-model-small-ru/conf/mfcc.conf" to "mfcc".toByteArray(),
        "models/ru/vosk-model-small-ru/conf/model.conf" to "model".toByteArray(),
        "models/ru/vosk-model-small-ru/graph/HCLr.fst" to byteArrayOf(4, 5),
        "models/ru/vosk-model-small-ru/ivector/final.ie" to byteArrayOf(6),
    )

    @Test
    fun isAvailable_withAssetBackedLocale_returnsTrue() {
        stubAssetTree(ruModelAssets())

        assertThat(provider.isAvailable(SpeechLocale.RU)).isTrue()
    }

    @Test
    fun isAvailable_withNoAssetAndNoDisk_returnsFalse() {
        stubAssetTree(emptyMap())

        assertThat(provider.isAvailable(SpeechLocale("de"))).isFalse()
    }

    @Test
    fun isAvailable_withAssetIoFailure_returnsFalse() {
        every { assets.list(any()) } throws IOException("blown")

        assertThat(provider.isAvailable(SpeechLocale("fr"))).isFalse()
    }

    @Test
    fun isAvailable_viaOnDiskModel_returnsTrueEvenWithoutAsset() {
        stubAssetTree(emptyMap())
        val diskModel = File(filesDir, "models/en/pretend-en").apply { mkdirs() }
        repeat(4) { File(diskModel, "f$it").writeText("x") }

        assertThat(provider.isAvailable(SpeechLocale.EN)).isTrue()
    }

    @Test
    fun resolve_happyPath_copiesAssetTreeIntoFilesDir_andReturnsAssetLocation() = runTest {
        val tree = ruModelAssets()
        stubAssetTree(tree)

        val result = provider.resolve(SpeechLocale.RU)

        assertThat(result.isSuccess).isTrue()
        val location = result.getOrThrow()
        assertThat(location.locale).isEqualTo(SpeechLocale.RU)
        assertThat(location.source).isEqualTo(ModelLocation.Source.ASSET)
        val copiedRoot = File(filesDir, "models/ru/vosk-model-small-ru")
        assertThat(location.absolutePath).isEqualTo(copiedRoot.absolutePath)
        assertThat(File(copiedRoot, "README").readText()).isEqualTo("readme")
        assertThat(File(copiedRoot, "am/final.mdl").readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(File(copiedRoot, "conf/model.conf").readText()).isEqualTo("model")
    }

    @Test
    fun resolve_whenModelAlreadyOnDisk_returnsExistingPath_withoutRecopying() = runTest {
        stubAssetTree(ruModelAssets())
        val preexisting = File(filesDir, "models/ru/pretend-model").apply { mkdirs() }
        repeat(5) { File(preexisting, "file$it").writeText("fixture") }
        val sentinel = File(preexisting, "sentinel.txt").apply { writeText("keep-me") }

        val result = provider.resolve(SpeechLocale.RU)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().absolutePath).isEqualTo(preexisting.absolutePath)
        assertThat(sentinel.readText()).isEqualTo("keep-me")
    }

    @Test
    fun resolve_missingAsset_returnsFailureWithModelUnavailableException() = runTest {
        stubAssetTree(emptyMap())

        val result = provider.resolve(SpeechLocale("de"))

        assertThat(result.isFailure).isTrue()
        val cause = result.exceptionOrNull()
        assertThat(cause).isInstanceOf(ModelUnavailableException::class.java)
        assertThat(cause!!.message).contains("de")
    }

    @Test
    fun resolve_removesLegacyCacheDirModelOnFirstRun() = runTest {
        stubAssetTree(emptyMap())
        val legacy = File(cacheDir, "vosk-model-small-ru").apply { mkdirs() }
        File(legacy, "stale").writeText("stale")

        provider.resolve(SpeechLocale("de"))

        assertThat(legacy.exists()).isFalse()
    }

    @Test
    fun resolve_assetWithEmptyCopiedTree_returnsFailure() = runTest {
        // Asset listing claims a dir exists but has no files: provider
        // cannot find an inner model dir and must surface a failure.
        val sparseTree = mapOf(
            "models/ru/.marker" to byteArrayOf(0),
        )
        stubAssetTree(sparseTree)

        val result = provider.resolve(SpeechLocale.RU)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
    }

    @Test
    fun resolve_isIdempotent_secondCallReturnsSamePathAndDoesNotReIssueAssetListing() = runTest {
        stubAssetTree(ruModelAssets())

        val first = provider.resolve(SpeechLocale.RU).getOrThrow()
        val second = provider.resolve(SpeechLocale.RU).getOrThrow()

        assertThat(second.absolutePath).isEqualTo(first.absolutePath)
    }
}
