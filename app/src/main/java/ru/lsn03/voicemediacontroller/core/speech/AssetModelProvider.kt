package ru.lsn03.voicemediacontroller.core.speech

import android.content.Context
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import java.io.File
import java.io.IOException

class AssetModelProvider(
    private val context: Context,
    private val logger: AppLogger,
) : ModelProvider {

    override fun isAvailable(locale: SpeechLocale): Boolean =
        hasOnDisk(locale) || hasAsset(locale)

    override suspend fun resolve(locale: SpeechLocale): Result<ModelLocation> = runCatching {
        cleanLegacyCacheModel()
        val existing = findModelDir(targetDir(locale))
        if (existing != null) {
            return@runCatching ModelLocation(locale, existing.absolutePath, ModelLocation.Source.ASSET)
        }
        if (!hasAsset(locale)) {
            throw ModelUnavailableException(locale)
        }
        val target = targetDir(locale)
        target.mkdirs()
        copyAssetFolder(assetRoot(locale), target)
        val copied = findModelDir(target)
            ?: throw IOException("Model copied but no inner model dir at ${target.absolutePath}")
        copied.setReadable(true, false)
        logger.d(APPLICATION_NAME, "AssetModelProvider: locale=${locale.tag} path=${copied.absolutePath}")
        ModelLocation(locale, copied.absolutePath, ModelLocation.Source.ASSET)
    }

    private fun targetDir(locale: SpeechLocale): File =
        File(File(context.filesDir, MODELS_DIR), locale.tag)

    private fun assetRoot(locale: SpeechLocale): String = "$MODELS_DIR/${locale.tag}"

    private fun hasAsset(locale: SpeechLocale): Boolean = try {
        (context.assets.list(assetRoot(locale))?.size ?: 0) > 0
    } catch (_: IOException) {
        false
    }

    private fun hasOnDisk(locale: SpeechLocale): Boolean =
        findModelDir(targetDir(locale)) != null

    private fun findModelDir(parent: File): File? {
        if (!parent.exists() || !parent.isDirectory) return null
        return parent.listFiles()
            ?.firstOrNull { it.isDirectory && (it.listFiles()?.size ?: 0) >= MIN_MODEL_FILES }
    }

    private fun cleanLegacyCacheModel() {
        val legacy = File(context.cacheDir, LEGACY_MODEL_DIR)
        if (legacy.exists()) {
            val removed = legacy.deleteRecursively()
            logger.d(APPLICATION_NAME, "AssetModelProvider: legacy cache cleanup removed=$removed")
        }
    }

    private fun copyAssetFolder(fromAssetPath: String, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        context.assets.list(fromAssetPath)?.forEach { child ->
            val childAsset = "$fromAssetPath/$child"
            val destFile = File(destDir, child)
            if (context.assets.list(childAsset)?.isNotEmpty() == true) {
                copyAssetFolder(childAsset, destFile)
            } else {
                destFile.parentFile?.mkdirs()
                context.assets.open(childAsset).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    companion object {
        const val MODELS_DIR = "models"
        private const val LEGACY_MODEL_DIR = "vosk-model-small-ru"
        private const val MIN_MODEL_FILES = 3
    }
}
