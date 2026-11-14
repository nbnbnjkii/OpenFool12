package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.assets.loaders.I18NBundleLoader
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.I18NBundle
import com.badlogic.gdx.utils.viewport.FitViewport
import java.util.Locale

/** Portrait settings with separate profile and appearance sections; edits save explicitly. */
internal class SettingsScreen(private val game: OpenFoolGame) : Screen {
    private val stage=Stage(FitViewport(480f,1000f))
    private val theme=NeonTheme()
    private val profile=PlayerProfile(game.preferences)
    private val profilePage=Group()
    private val appearancePage=Group()
    private var avatar=profile.avatar
    private var deck=game.preferences.getString(DECK,"rus").let { if (it in decks) it else "rus" }
    private var sorting=game.preferences.getInteger(SORTING_MODE,0).coerceIn(0,4)
    private var background=game.preferences.getInteger(BACKGROUND,1).coerceIn(1,2)
    private var language=game.preferences.getString("Language","ru").let { if (it in languages) it else "ru" }
    private var backgroundColor=game.preferences.getInteger(BACKGROUND_COLOR,0x33cc4dff)
    private var page=0
    private lateinit var nameField: TextField
    private lateinit var previewName: Label
    private lateinit var deckLabel: Label
    private lateinit var sortingLabel: Label
    private lateinit var languageLabel: Label
    private lateinit var backgroundLabel: Label
    private val status: Label

    init {
        Gdx.input.inputProcessor=stage
        game.orientationHelper.requestOrientation(OrientationHelper.Orientation.PORTRAIT)
        theme.image(stage,"lobby",0f,0f,480f,1000f)
        theme.button(stage,"НАЗАД","slate",20f,924f,108f,48f,18) { back() }
        theme.label(stage,"НАСТРОЙКИ",130f,925f,330f,48f,29)
        theme.button(stage,"ПРОФИЛЬ","purple",20f,844f,212f,58f,22) { selectPage(0) }
        theme.button(stage,"ОФОРМЛЕНИЕ","blue",248f,844f,212f,58f,20) { selectPage(1) }
        var first=stage.actors.size
        val preview=object : Actor() {
            override fun draw(batch: Batch,parentAlpha: Float) {
                batch.color=Color.WHITE
                batch.draw(theme.texture("avatar_$avatar"),176f,667f,128f,128f)
                for (i in 0..9) {
                    val x=20f+(i%5)*89f
                    val y=377f-(i/5)*112f
                    batch.draw(theme.texture(if (i==avatar) "gold" else "slate"),x,y,84f,84f)
                }
            }
        }
        stage.addActor(preview)
        previewName=theme.label(stage,PlayerProfile.avatarNames[avatar],20f,625f,440f,32f,23,Color.valueOf("ffdc8b"))
        theme.label(stage,"ВАШ НИК",20f,577f,440f,28f,18,Color.valueOf("aebbd3"))
        val fieldStyle=TextField.TextFieldStyle().apply {
            font=theme.font(24)
            fontColor=Color.WHITE
            background=TextureRegionDrawable(theme.texture("slate"))
            cursor=TextureRegionDrawable(theme.texture("pixel"))
            selection=TextureRegionDrawable(theme.texture("blue"))
        }
        nameField=TextField(profile.nickname,fieldStyle)
        nameField.setBounds(30f,503f,420f,62f)
        nameField.maxLength=20
        nameField.textFieldFilter=TextField.TextFieldFilter { _,c -> PlayerProfile.allowed(c) }
        stage.addActor(nameField)
        theme.label(stage,"До 20 символов • буквы, цифры, пробел",20f,466f,440f,27f,15,Color.valueOf("aebbd3"))
        for (i in 0..9) {
            val x=26f+(i%5)*89f
            val y=383f-(i/5)*112f
            theme.iconButton(stage,"avatar_$i",x,y,72f) {
                avatar=i
                previewName.setText(PlayerProfile.avatarNames[i])
                stage.keyboardFocus=null
                Gdx.input.setOnscreenKeyboardVisible(false)
            }
            theme.label(stage,"${i+1}",x,y-26f,72f,24f,15)
        }
        moveToPage(first,profilePage)
        first=stage.actors.size
        theme.label(stage,"КАРТЫ",20f,766f,440f,35f,20,Color.valueOf("aebbd3"))
        deckLabel=theme.label(stage,"",20f,720f,440f,34f,25)
        theme.button(stage,"СМЕНИТЬ КОЛОДУ","blue",40f,653f,400f,54f,21) {
            deck=decks[(decks.indexOf(deck)+1)%decks.size]
            game.ensureDeck(deck)
            refreshLabels()
        }
        game.ensureDeck(deck)
        val cards=object : Actor() {
            override fun draw(batch: Batch,parentAlpha: Float) {
                batch.color=Color.WHITE
                for ((i,card) in listOf("back","1s","12d","10h").withIndex())
                    batch.draw(game.assetManager.get("decks/$deck/$card.png",Texture::class.java),
                        68f+i*87f,533f,76f,114f)
            }
        }
        stage.addActor(cards)
        sortingLabel=theme.label(stage,"",20f,479f,440f,36f,19)
        theme.button(stage,"СМЕНИТЬ СОРТИРОВКУ","slate",40f,418f,400f,51f,19) {
            sorting=(sorting+1)%5;refreshLabels()
        }
        backgroundLabel=theme.label(stage,"",20f,366f,440f,34f,20)
        theme.button(stage,"СМЕНИТЬ ФОН","slate",40f,306f,400f,51f,20) {
            background=if (background==1) 2 else 1;refreshLabels()
        }
        languageLabel=theme.label(stage,"",20f,251f,440f,34f,20)
        theme.button(stage,"СМЕНИТЬ ЯЗЫК ИГРЫ","slate",40f,190f,400f,51f,19) {
            language=languages[(languages.indexOf(language)+1)%languages.size];refreshLabels()
        }
        moveToPage(first,appearancePage)
        status=theme.label(stage,"Изменения сохраняются кнопкой ниже",20f,131f,440f,32f,15,Color.valueOf("aebbd3"))
        theme.button(stage,"СОХРАНИТЬ","green",20f,48f,440f,66f,26) { save() }
        refreshLabels()
        selectPage(0)
    }

    private fun moveToPage(first: Int,group: Group) {
        (first until stage.actors.size).map { stage.actors[it] }.forEach { group.addActor(it) }
        stage.addActor(group)
    }
    private fun selectPage(index: Int) {
        page=index
        profilePage.isVisible=index==0
        appearancePage.isVisible=index==1
        stage.keyboardFocus=null
        Gdx.input.setOnscreenKeyboardVisible(false)
    }
    private fun refreshLabels() {
        deckLabel.setText(listOf("Русская","Международная","Французская","Народы СССР")[decks.indexOf(deck)])
        sortingLabel.setText("Сортировка: "+listOf("без сортировки","по масти ↑","по масти ↓","по рангу ↑","по рангу ↓")[sorting].replace("↑","возр.").replace("↓","убыв."))
        backgroundLabel.setText("Фон стола: $background / 2")
        languageLabel.setText("Язык игры: "+listOf("Русский","English","Čeština","Deutsch")[languages.indexOf(language)].replace("Čeština","Cestina"))
    }
    private fun save() {
        val normalized=PlayerProfile.normalizeName(nameField.text)
        profile.save(normalized,avatar)
        nameField.text=normalized
        game.preferences.putString(DECK,deck).putString("Language",language)
            .putInteger(SORTING_MODE,sorting).putInteger(BACKGROUND,background)
            .putInteger(BACKGROUND_COLOR,backgroundColor).putBoolean(ORIENTATION,true).flush()
        game.assetManager.unload("i18n/OpenFool")
        game.assetManager.load("i18n/OpenFool",I18NBundle::class.java,
            I18NBundleLoader.I18NBundleParameter(Locale(language)))
        game.assetManager.finishLoadingAsset<I18NBundle>("i18n/OpenFool")
        game.localeBundle=game.assetManager.get("i18n/OpenFool",I18NBundle::class.java)
        stage.keyboardFocus=null
        Gdx.input.setOnscreenKeyboardVisible(false)
        status.setText("Профиль и настройки сохранены")
    }
    private fun back() {
        Gdx.input.setOnscreenKeyboardVisible(false)
        game.screen=MainMenuScreen(game)
        dispose()
    }
    override fun render(delta: Float) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (stage.keyboardFocus!=null) {
                stage.keyboardFocus=null;Gdx.input.setOnscreenKeyboardVisible(false)
            } else back()
            return
        }
        Gdx.gl.glClearColor(0.04f,0.05f,0.12f,1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta);stage.draw()
    }
    override fun resize(width: Int,height: Int) { stage.viewport.update(width,height,true) }
    override fun show() {}
    override fun hide() {}
    override fun pause() {}
    override fun resume() {}
    override fun dispose() { stage.dispose();theme.dispose() }
    companion object {
        const val BACKGROUND_COLOR="BackgroundColor"
        const val DECK="Deck"
        const val SORTING_MODE="SortingMode"
        const val BACKGROUND="Background"
        const val ORIENTATION="Orientation"
        private val decks=listOf("rus","int","fra","psu")
        private val languages=listOf("ru","en","cs","de")
    }
}
