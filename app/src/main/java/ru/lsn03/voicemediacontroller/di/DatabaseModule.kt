package ru.lsn03.voicemediacontroller.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.db.*
import ru.lsn03.voicemediacontroller.db.DbSeeder.defaultBindings
import ru.lsn03.voicemediacontroller.text.TextNormalizer
import ru.lsn03.voicemediacontroller.voice.GrammarBuilder
import ru.lsn03.voicemediacontroller.voice.repo.GrammarRepository
import ru.lsn03.voicemediacontroller.voice.repo.GrammarRepositoryImpl
import ru.lsn03.voicemediacontroller.voice.repo.VoicePhraseRepository
import ru.lsn03.voicemediacontroller.voice.repo.VoicePhraseRepositoryImpl
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepository
import ru.lsn03.voicemediacontroller.voice.repo.WakeWordRepositoryImpl
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDb(
        @ApplicationContext context: Context,
        daoProvider: Provider<VoiceCommandDao>
    ): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "voice.db")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = daoProvider.get()
                        dao.upsertSettings(
                            VoiceWakePhraseSettingEntity(wakeWord = WakeWordRepository.DEFAULT_WAKE_WORD)
                        )

                        defaultBindings().forEach { (action, phrases) ->
                            phrases.forEach { raw ->
                                val p = raw.trim()
                                if (p.isNotEmpty()) {
                                    dao.insertPhrase(
                                        VoicePhraseEntity(
                                            action = action.name,
                                            phrase = p,
                                            normalized = p.lowercase(),
                                            enabled = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            })
            .build()
    }

    @Provides @Singleton
    fun provideVoiceDao(db: AppDatabase): VoiceCommandDao = db.voiceCommandDao()

    @Provides
    @Singleton
    fun provideVoicePhraseRepository(
        dao: VoiceCommandDao,
        normalizer: TextNormalizer,
        logger: AppLogger,
    ): VoicePhraseRepository = VoicePhraseRepositoryImpl(dao, normalizer, logger)

    @Provides
    @Singleton
    fun provideWakeWordRepository(
        dao: VoiceCommandDao,
        normalizer: TextNormalizer,
    ): WakeWordRepository = WakeWordRepositoryImpl(dao, normalizer)

    @Provides
    @Singleton
    fun provideGrammarRepository(
        phraseRepo: VoicePhraseRepository,
        wakeRepo: WakeWordRepository,
        grammarBuilder: GrammarBuilder,
    ): GrammarRepository = GrammarRepositoryImpl(phraseRepo, wakeRepo, grammarBuilder)
}
