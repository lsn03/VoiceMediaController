package ru.lsn03.voicemediacontroller.voice.repo

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import ru.lsn03.voicemediacontroller.voice.GrammarBuilder
import ru.lsn03.voicemediacontroller.voice.VoskGrammars

interface GrammarRepository {
    fun observeGrammars(): Flow<VoskGrammars>
}

class GrammarRepositoryImpl(
    private val phraseRepo: VoicePhraseRepository,
    private val wakeRepo: WakeWordRepository,
    private val grammarBuilder: GrammarBuilder,
) : GrammarRepository {

    @OptIn(FlowPreview::class)
    override fun observeGrammars(): Flow<VoskGrammars> =
        combine(wakeRepo.observe(), phraseRepo.observeAllEnabled()) { wake, rows ->
            grammarBuilder.build(wake, rows.map { it.normalized })
        }
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MS)

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
