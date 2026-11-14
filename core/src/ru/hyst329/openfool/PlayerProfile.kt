package ru.hyst329.openfool

import com.badlogic.gdx.Preferences

/** Local profile. A nickname is display text, never an authentication identifier. */
internal class PlayerProfile(private val preferences: Preferences) {
    val nickname: String get() = normalizeName(preferences.getString(NAME,"Игрок_1"))
    val avatar: Int get() = preferences.getInteger(AVATAR,0).coerceIn(0,9)
    val avatarTexture: String get() = "avatar_$avatar"
    fun save(name: String, selectedAvatar: Int) {
        preferences.putString(NAME,normalizeName(name))
        preferences.putInteger(AVATAR,selectedAvatar.coerceIn(0,9))
        preferences.flush()
    }
    companion object {
        private const val NAME="Profile/Nickname"
        private const val AVATAR="Profile/Avatar"
        val avatarNames=listOf("Кот-босс","Пёс-пират","Жаба-царь","Енот-воришка","Сова-кофеман",
            "Хомяк-обжора","Лиса-хитрюга","Акула-улыбака","Панда-соня","Гусь-хулиган")
        fun allowed(c: Char): Boolean = c in 'А'..'я' || c=='Ё' || c=='ё' ||
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in " _-"
        fun normalizeName(name: String): String = name.filter { allowed(it) }.trim()
            .replace(Regex(" +")," ").take(20).ifBlank { "Игрок_1" }
    }
}
