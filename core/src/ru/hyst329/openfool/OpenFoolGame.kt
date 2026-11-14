package ru.hyst329.openfool

import OrientationHelper
import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.I18NBundleLoader
import com.badlogic.gdx.assets.loaders.TextureLoader
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter
import com.badlogic.gdx.scenes.scene2d.ui.Skin

import com.badlogic.gdx.utils.I18NBundle
import com.kotcrab.vis.ui.VisUI


import java.util.Locale
import kotlin.properties.Delegates


/**
 * Created by hyst329 on 12.03.2017.
 * Licensed under MIT License.
 */

class OpenFoolGame(val orientationHelper: OrientationHelper) : Game() {
    internal var batch: SpriteBatch by Delegates.notNull()
    internal var assetManager: AssetManager by Delegates.notNull()
    internal var font: BitmapFont by Delegates.notNull()
    internal var smallFont: BitmapFont by Delegates.notNull()
    internal var preferences: Preferences by Delegates.notNull()
    internal lateinit var economy: GameEconomy
    private val queuedDecks = HashSet<String>()
    internal var localeBundle: I18NBundle by Delegates.notNull()

    override fun create() {
        batch = SpriteBatch()
        assetManager = AssetManager()
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/8835.otf"))
        val parameter = FreeTypeFontParameter()
        var chars: String = ""
        for (i in 0x20..0x04ff) {
            chars += i.toChar()
        }
        parameter.characters = chars
        parameter.genMipMaps = true
        parameter.minFilter = Texture.TextureFilter.MipMapLinearLinear
        parameter.magFilter = Texture.TextureFilter.Linear
        parameter.size = 72
        font = generator.generateFont(parameter)
        font.data.setScale(1f / 3f)
        parameter.size = 36
        smallFont = generator.generateFont(parameter)
        smallFont.data.setScale(1f / 3f)
        generator.dispose()

        VisUI.load(Gdx.files.internal("ui/temp-skin.json"))
        preferences = Gdx.app.getPreferences("OpenFool")
        economy = GameEconomy(preferences)
        Gdx.input.isCatchBackKey = true
        // Deal with localisation
        var localeString: String? = preferences.getString("Language", null)
        val locale = if (localeString == null) Locale.getDefault() else Locale(localeString)
        assetManager.load("i18n/OpenFool", I18NBundle::class.java,
                I18NBundleLoader.I18NBundleParameter(locale))
        if (localeString == null) {
            localeString = locale.language
            preferences.putString("Language", localeString)
            preferences.flush()
        }
        assetManager.finishLoadingAsset<I18NBundle>("i18n/OpenFool")
        localeBundle = assetManager.get("i18n/OpenFool", I18NBundle::class.java)
        val param: TextureLoader.TextureParameter = TextureLoader.TextureParameter()
        param.minFilter = Texture.TextureFilter.MipMap
        param.genMipMaps = true
        // Only the selected deck is queued; other styles load on demand in settings.
        queueDeck(preferences.getString(SettingsScreen.DECK,"rus"))
        val fullSuits = arrayOf("clubs", "diamonds", "hearts", "spades")
        for (i in 1..2) {
            assetManager.load("backgrounds/background$i.png", Texture::class.java, param)
        }
        for (s in fullSuits) {
            assetManager.load("suits/$s.png", Texture::class.java, param)
        }
        assetManager.load("holidays/hammersickle.png", Texture::class.java, param)
        assetManager.load("holidays/santahat.png", Texture::class.java, param)
        this.setScreen(MainMenuScreen(this))
    }

    private fun queueDeck(style: String) {
        val deck = if (style in listOf("rus","int","fra","psu")) style else "rus"
        if (!queuedDecks.add(deck)) return
        val p=TextureLoader.TextureParameter()
        p.genMipMaps=true; p.minFilter=Texture.TextureFilter.MipMapLinearLinear
        p.magFilter=Texture.TextureFilter.Linear
        for (rank in 1..13) for (suit in "cdhs")
            assetManager.load("decks/$deck/$rank$suit.png",Texture::class.java,p)
        assetManager.load("decks/$deck/back.png",Texture::class.java,p)
    }
    internal fun ensureDeck(style: String) { queueDeck(style); assetManager.finishLoading() }
    internal fun ensureGameAssets() { ensureDeck(preferences.getString(SettingsScreen.DECK,"rus")) }

    /** Android legacy/predictive-back fallback: all table dialogs run on the GL thread. */
    fun handleTableBack(): Boolean {
        val table=screen as? GameScreen ?: return false
        Gdx.app.postRunnable { if (screen===table) table.onPlatformBack() }
        return true
    }

    override fun dispose() {
        screen?.dispose()
        assetManager.dispose()
        batch.dispose()
        font.dispose()
        smallFont.dispose()
        VisUI.dispose()
    }
}
