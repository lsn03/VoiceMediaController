package ru.lsn03.voicemediacontroller.vosk

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import ru.lsn03.voicemediacontroller.service.VoiceService.Companion.SAMPLE_RATE
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import ru.lsn03.voicemediacontroller.utils.Utilities.MODEL_NAME
import java.io.File
import java.io.IOException

class VoskEngine(
    private val context: Context
) {

    private lateinit var model: Model
    private lateinit var wakeRecognizer: Recognizer
    private lateinit var wakeCommandRecognizer: Recognizer
    private lateinit var commandRecognizer: Recognizer

    fun start() {
        model = Model(modelPath())

        // 👤 Wake word recognizer (маленькая грамматика)
        //        wakeRecognizer = Recognizer(model, SAMPLE_RATE.toFloat(), """["джарвис"]""")
        wakeRecognizer = Recognizer(model, SAMPLE_RATE.toFloat(), """["джарвис", "[unk]"]""")

        // 🎵 Command recognizer (команды)
        commandRecognizer = Recognizer(
            model, SAMPLE_RATE.toFloat(),
            """
                    ["следующий трек","следующий", "предыдущий трек",
                     "предыдущий", "некст","прев", "пауза", "стоп",
                      "уменьши", "увеличь", "громче", "тише", "продолжить",
                       "продолжи","возобнови","плей", "плэй", "играй",
                       "старт", "стоп", "что за хуйня","че за хуйня", "время", "название"
                       ]
                       """
        )

        wakeCommandRecognizer = Recognizer(
            model, SAMPLE_RATE.toFloat(),
            """
                            [
                              "джарвис следующий трек", "джарвис следующий", "джарвис некст", "джарвис что за хуйня", "джарвис че за хуйня",
                              "джарвис предыдущий трек", "джарвис предыдущий", "джарвис прев",
                              "джарвис пауза", "джарвис стоп",
                              "джарвис громче", "джарвис увеличь",
                              "джарвис тише", "джарвис уменьши",
                              "джарвис продолжи", "джарвис продолжить", "джарвис возобнови",
                              "джарвис плей", "джарвис плэй", "джарвис играй", "джарвис старт",
                              "джарвис время", "джарвис название",
                              "[unk]"
                            ]
                            """.trimIndent()
        )
    }

    fun acceptWake(pcm: ByteArray): VoskResult {
        val isFinal = wakeCommandRecognizer.acceptWaveForm(pcm, pcm.size)
        return if (isFinal) {
            val text = parseText(wakeCommandRecognizer.result).trim()
            if (text.isEmpty()) VoskResult.None else VoskResult.Final(text)
        } else {
            val p = parsePartial(wakeCommandRecognizer.partialResult).trim()
            if (p.isEmpty()) VoskResult.None else VoskResult.Partial(p)
        }
    }

    fun acceptCommand(pcm: ByteArray): VoskResult {
        val isFinal = commandRecognizer.acceptWaveForm(pcm, pcm.size)
        return if (isFinal) {
            val text = parseText(commandRecognizer.result).trim()
            if (text.isEmpty()) VoskResult.None else VoskResult.Final(text)
        } else {
            val p = parsePartial(commandRecognizer.partialResult).trim()
            if (p.isEmpty()) VoskResult.None else VoskResult.Partial(p)
        }
    }

    fun resetWake() {
        wakeCommandRecognizer.reset()
    }

    fun resetCommand() {
commandRecognizer.reset()
    }


    private fun modelPath(): String {
        val modelDir = File(context.cacheDir, MODEL_NAME)
        Log.d(APPLICATION_NAME, "VoiceService::modelPath Модель: ${modelDir.absolutePath}")

        if (modelDir.exists() && modelDir.listFiles()?.size ?: 0 > 5) {  // >5 файлов = OK
            Log.d(APPLICATION_NAME, "VoiceService::modelPath Модель готова: ${modelDir.listFiles()?.size} файлов")
            return modelDir.absolutePath
        }

        // Копируем ПАПКУ из assets
        try {
            copyAssetFolder(MODEL_NAME, modelDir)
            modelDir.setReadable(true, false)
            Log.d(APPLICATION_NAME, "VoiceService::modelPath Модель скопирована: ${modelDir.listFiles()?.size} файлов")
        } catch (e: IOException) {
            Log.e(APPLICATION_NAME, "VoiceService::modelPath Ошибка копирования модели", e)
            throw e
        }
        return modelDir.absolutePath
    }

    private fun copyAssetFolder(fromAssetPath: String, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()

        context.assets.list(fromAssetPath)?.forEach { child ->
//            Log.d(APPLICATION_NAME, "VoiceService::copyAssetFolder child: $child")
            val childAsset = "$fromAssetPath/$child"
            val destFile = File(destDir, child)

            if (context.assets.list(childAsset)?.isNotEmpty() == true) {
                // Рекурсивно папка
                copyAssetFolder(childAsset, destFile)
            } else {
                // Файл
                context.assets.open(childAsset).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun parseText(jsonString: String): String =
        try { JSONObject(jsonString).optString("text", "") } catch (_: Exception) { "" }

    private fun parsePartial(jsonString: String): String =
        try { JSONObject(jsonString).optString("partial", "") } catch (_: Exception) { "" }
}