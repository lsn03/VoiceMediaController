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
import ru.lsn03.voicemediacontroller.db.*
import ru.lsn03.voicemediacontroller.db.DbSeeder.defaultBindings
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
                        dao.upsertSettings(VoiceWakePhraseSettingEntity(wakeWord = "джарвис"))

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
}