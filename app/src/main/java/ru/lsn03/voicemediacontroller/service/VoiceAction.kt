package ru.lsn03.voicemediacontroller.service

enum class VoiceAction (val description: String) {

    START("Старт медиа"),
    STOP("Стоп медиа"),
    NEXT("Следующий трек"),
    PREV("Предыдущий трек"),
    VOLUME_UP("Увеличь громкость"),
    VOLUME_DOWN("Уменьши громкость"),
    SAY_TIME("Скажи время"),
    SAY_TITLE("Скажи название аудио-дорожки"),
    UNKNOWN("Неизвестная команда");


}
