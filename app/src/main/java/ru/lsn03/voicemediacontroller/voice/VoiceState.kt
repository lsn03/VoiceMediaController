package ru.lsn03.voicemediacontroller.voice

enum class VoiceState(private val description: String) {
    WAKE_LISTENING("слушаем wake / wake+command"),
    COMMAND_LISTENING("слушаем команды после wake"),
    SWITCHING("короткий переходный стейт (для безопасного reset/recreate recognizers)")
}