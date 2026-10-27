package ru.lsn03.voicemediacontroller.di

import android.os.Handler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.lsn03.voicemediacontroller.core.log.AndroidLogger
import ru.lsn03.voicemediacontroller.core.log.AppLogger
import ru.lsn03.voicemediacontroller.core.speech.SpeechRecognizer
import ru.lsn03.voicemediacontroller.core.speech.VoskSpeechRecognizer
import ru.lsn03.voicemediacontroller.core.text.TextPublisher
import ru.lsn03.voicemediacontroller.core.time.AndroidClock
import ru.lsn03.voicemediacontroller.core.time.Clock
import ru.lsn03.voicemediacontroller.core.time.HandlerScheduler
import ru.lsn03.voicemediacontroller.core.time.Scheduler
import ru.lsn03.voicemediacontroller.voice.status.RecognizerStatusBus
import ru.lsn03.voicemediacontroller.vosk.VoskEngine
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = AndroidClock()

    @Provides
    @Singleton
    fun provideScheduler(handler: Handler): Scheduler = HandlerScheduler(handler)

    @Provides
    @Singleton
    fun provideAppLogger(): AppLogger = AndroidLogger()

    @Provides
    @Singleton
    fun provideSpeechRecognizer(voskEngine: VoskEngine): SpeechRecognizer =
        VoskSpeechRecognizer(voskEngine)

    @Provides
    @Singleton
    fun provideTextPublisher(bus: RecognizerStatusBus): TextPublisher = bus
}
