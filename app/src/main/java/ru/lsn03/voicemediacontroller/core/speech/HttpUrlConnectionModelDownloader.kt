package ru.lsn03.voicemediacontroller.core.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

class HttpUrlConnectionModelDownloader(
    private val context: Context,
    private val logger: AppLogger,
    private val connectionFactory: (String) -> HttpURLConnection = { url ->
        URL(url).openConnection() as HttpURLConnection
    },
) : ModelDownloader {

    override fun download(locale: SpeechLocale, entry: ModelCatalogEntry): Flow<DownloadProgress> = flow {
        emit(DownloadProgress.Idle)
        if (entry.url.isNullOrBlank()) {
            emit(DownloadProgress.Failed("No downloadable model for locale '${locale.tag}'"))
            return@flow
        }

        val finalDir = finalDir(locale)
        val stagingDir = stagingDir(locale)
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        var connection: HttpURLConnection? = null
        try {
            connection = connectionFactory(entry.url)
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS

            val code = connection.responseCode
            if (code !in 200..299) {
                stagingDir.deleteRecursively()
                emit(DownloadProgress.Failed("HTTP $code"))
                return@flow
            }

            val total: Long? = connection.contentLengthLong.takeIf { it > 0 }
            val digest = MessageDigest.getInstance("SHA-256")

            connection.inputStream.use { input ->
                var transferred = 0L
                val buffer = ByteArray(BUFFER_SIZE)
                emit(DownloadProgress.Downloading(0L, total))
                val tempZip = File(stagingDir, "model.zip")
                tempZip.outputStream().use { out ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        transferred += read
                        emit(DownloadProgress.Downloading(transferred, total))
                    }
                }

                val sha = digest.digest().toHex()
                emit(DownloadProgress.Verifying(sha))
                if (entry.sha256.isNotBlank() && !sha.equals(entry.sha256, ignoreCase = true)) {
                    stagingDir.deleteRecursively()
                    emit(DownloadProgress.Failed("Checksum mismatch"))
                    return@flow
                }

                unzip(tempZip, stagingDir)
                tempZip.delete()
            }

            if (finalDir.exists()) finalDir.deleteRecursively()
            finalDir.parentFile?.mkdirs()
            if (!stagingDir.renameTo(finalDir)) {
                stagingDir.deleteRecursively()
                emit(DownloadProgress.Failed("Rename failed"))
                return@flow
            }
            logger.d(APPLICATION_NAME, "ModelDownloader: locale=${locale.tag} installed at ${finalDir.absolutePath}")
            emit(DownloadProgress.Done)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            stagingDir.deleteRecursively()
            throw ce
        } catch (t: Throwable) {
            stagingDir.deleteRecursively()
            logger.w(APPLICATION_NAME, "ModelDownloader: failed locale=${locale.tag}", t)
            emit(DownloadProgress.Failed(t.message ?: t.javaClass.simpleName))
        } finally {
            connection?.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun delete(locale: SpeechLocale): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = finalDir(locale)
            if (dir.exists() && !dir.deleteRecursively()) {
                throw IOException("Failed to delete ${dir.absolutePath}")
            }
            val staging = stagingDir(locale)
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun finalDir(locale: SpeechLocale): File =
        File(File(context.filesDir, MODELS_DIR), locale.tag)

    private fun stagingDir(locale: SpeechLocale): File =
        File(File(context.filesDir, MODELS_DIR), "${locale.tag}$STAGING_SUFFIX")

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val outFile = File(destDir, entry.name).canonicalFile
                if (!outFile.absolutePath.startsWith(destDir.canonicalPath)) {
                    throw IOException("Zip entry escapes destination: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
            }
        }
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xff
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0f])
        }
        return sb.toString()
    }

    companion object {
        const val MODELS_DIR = "models"
        const val STAGING_SUFFIX = ".part"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 32 * 1024
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
