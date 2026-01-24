package ru.lsn03.voicemediacontroller.voice

import kotlinx.coroutines.flow.distinctUntilChanged

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONObject
import ru.lsn03.voicemediacontroller.action.VoiceAction
import ru.lsn03.voicemediacontroller.command.CommandBinding
import ru.lsn03.voicemediacontroller.db.VoiceCommandDao
import ru.lsn03.voicemediacontroller.db.VoiceWakePhraseSettingEntity
import ru.lsn03.voicemediacontroller.utils.Utilities.APPLICATION_NAME
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceCommandRepository @Inject constructor(
    private val dao: VoiceCommandDao
) {

    fun grammars(scope: CoroutineScope): StateFlow<VoskGrammars> =
        observeVoskGrammars()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                initialValue = VoskGrammars(
                    wakeWordGrammarJson = "[\"джарвис\",\"[unk]\"]",
                    commandGrammarJson = "[\"[unk]\"]",
                    wakeCommandGrammarJson = "[\"[unk]\"]"
                )
            )

    fun observeBindings(): Flow<List<CommandBinding>> =
        dao.observeEnabledPhrases().map { rows ->
            rows.groupBy { VoiceAction.valueOf(it.action) }
                .map { (action, list) ->
                    CommandBinding(list.map { it.normalized }.distinct(), action)
                }
        }

    fun observeWakeWord(): Flow<String> =
        dao.observeSettings()
            .map { it?.wakeWord?.trim()?.lowercase().orEmpty() }
            .map { if (it.isBlank()) "джарвис" else it }

    fun observeVoskGrammars(): Flow<VoskGrammars> =
        combine(observeWakeWord(), dao.observeEnabledPhrases()) { wake, rows ->
            val commands = rows.map { it.normalized }.distinct().filter { it.isNotBlank() }

            val commandJson = (commands.ifEmpty { listOf("[unk]") }).toVoskJsonArray()
            val wakeCommandJson = (commands.map { "$wake $it" } + "[unk]").distinct().toVoskJsonArray()
            val wakeWordJson = listOf(wake, "[unk]").toVoskJsonArray()

            VoskGrammars(wakeWordJson, commandJson, wakeCommandJson)
        }
            // ВАЖНО: сравниваем по строкам грамматики, чтобы одинаковые не проходили
            .distinctUntilChanged()
            .debounce(300)

    suspend fun setWakeWord(newWord: String) {
        val ww = newWord.trim().lowercase()
        dao.upsertSettings(VoiceWakePhraseSettingEntity(wakeWord = if (ww.isBlank()) "джарвис" else ww))
    }

    suspend fun addPhrase(action: VoiceAction, phrase: String) {
        val p = phrase.trim()
        if (p.isEmpty()) return
        dao.insertPhrase(
            ru.lsn03.voicemediacontroller.db.VoicePhraseEntity(
                action = action.name,
                phrase = p,
                normalized = p.lowercase(),
                enabled = true
            )
        )
    }

    private val MAX_JSON_CHARS = 50_000 // для твоих 23 команд хватит с огромным запасом

    private fun List<String>.toVoskJsonArray(): String {
        val sb = StringBuilder()
        sb.append('[')
        forEachIndexed { i, s ->
            if (i > 0) sb.append(',')
            sb.append(JSONObject.quote(s))
            if (sb.length > MAX_JSON_CHARS) {
                Log.e(APPLICATION_NAME, "Grammar JSON too big! len=${sb.length}, items=$size, lastLen=${s.length}")
                // аварийный выход, чтобы не словить OOM
                return "[\"[unk]\"]"
            }
        }
        sb.append(']')
        return sb.toString()
    }


}