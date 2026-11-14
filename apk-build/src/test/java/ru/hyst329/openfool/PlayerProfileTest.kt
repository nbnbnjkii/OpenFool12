package ru.hyst329.openfool

import com.badlogic.gdx.Preferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PlayerProfileTest {
    private fun preferences(): Preferences {
        val values=HashMap<String,Any>()
        return Proxy.newProxyInstance(Preferences::class.java.classLoader,arrayOf(Preferences::class.java)) { proxy,method,args ->
            when (method.name) {
                "putInteger","putString" -> { values[args[0] as String]=args[1];proxy }
                "getInteger","getString" -> values[args[0] as String] ?: args.getOrNull(1)
                "flush" -> null
                else -> throw IllegalStateException(method.name)
            }
        } as Preferences
    }
    @Test fun defaultAndSavedProfileSurviveReopening() {
        val prefs=preferences()
        assertEquals("Игрок_1",PlayerProfile(prefs).nickname)
        PlayerProfile(prefs).save("  Гусь  Босс  ",9)
        val reopened=PlayerProfile(prefs)
        assertEquals("Гусь Босс",reopened.nickname)
        assertEquals(9,reopened.avatar)
        assertEquals("avatar_9",reopened.avatarTexture)
    }
    @Test fun namesAreSafeAndFitSupportedFont() {
        assertEquals("Игрок_1",PlayerProfile.normalizeName("\n\t"))
        assertEquals("Игрок_1",PlayerProfile.normalizeName("😀"))
        assertEquals("ABЁёжик-1",PlayerProfile.normalizeName("A\nBЁёжик-1<>"))
        assertEquals(20,PlayerProfile.normalizeName("a".repeat(50)).length)
    }
    @Test fun allTenAvatarsCanBeSelectedAndBadIndicesAreClamped() {
        val profile=PlayerProfile(preferences())
        assertEquals(10,PlayerProfile.avatarNames.size)
        for (i in 0..9) {
            profile.save("Имя",i)
            assertEquals(i,profile.avatar)
            assertEquals("avatar_$i",profile.avatarTexture)
        }
        profile.save("Имя",-20);assertEquals(0,profile.avatar)
        profile.save("Имя",20);assertEquals(9,profile.avatar)
    }
}
