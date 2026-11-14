package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.viewport.FitViewport

/** Persistent, explicit bot-game options; the human is included in the player count. */
internal class BotSetupScreen(private val game: OpenFoolGame, private val matchMode: MatchMode = MatchMode.BOTS) : Screen {
    private val stage = Stage(FitViewport(480f, 1000f))
    private val theme = NeonTheme()
    private val rules = RuleSet(game.preferences)
    private val counts = HashMap<Int, Actor>()
    private val decks = HashMap<Int, Actor>()
    private val modes = HashMap<Boolean, Actor>()
    private lateinit var notice: Label
    private val wager: WagerSlider
    init {
        Gdx.input.inputProcessor = stage
        game.orientationHelper.requestOrientation(OrientationHelper.Orientation.PORTRAIT)
        theme.image(stage,"lobby",0f,0f,480f,1000f)
        theme.button(stage,"НАЗАД","slate",20f,924f,110f,48f,18) { back() }
        theme.label(stage,when (matchMode) { MatchMode.BOTS -> "ИГРА С БОТАМИ"; MatchMode.ONLINE -> "ИГРАТЬ ОНЛАЙН"; MatchMode.FRIENDS -> "ИГРА С ДРУГОМ" },20f,834f,440f,60f,28)
        theme.label(stage,"Участников за столом (включая вас)",20f,765f,440f,40f,20)
        for (count in 2..6) {
            counts[count] = theme.button(stage,"$count","purple",20f+(count-2)*90f,679f,80f,70f,30) {
                rules.playerCount = count
                if (rules.cardCount < count*6) {
                    rules.cardCount = RuleSet.compatibleDeckSize(rules.cardCount,count)
                    notice.setText("Для 5–6 участников нужна колода от 36 карт")
                } else notice.setText("")
                refresh()
            }
        }
        theme.label(stage,"КОЛОДА",20f,598f,440f,40f,22)
        for ((index, cards) in listOf(24,36,52).withIndex()) {
            decks[cards] = theme.button(stage,"$cards карты","blue",20f+index*150f,514f,140f,70f,22) {
                if (cards < rules.playerCount*6) {
                    notice.setText("24 карты: максимум 4 участника по 6 карт")
                } else {
                    rules.cardCount = cards
                    notice.setText("")
                }
                refresh()
            }
        }
        theme.label(stage,"РЕЖИМ ИГРЫ",20f,430f,440f,40f,22)
        for ((index, pass) in listOf(false,true).withIndex()) {
            modes[pass] = theme.button(stage,if (pass) "ПЕРЕВОДНОЙ" else "ПОДКИДНОЙ","purple",
                20f+index*225f,347f,215f,70f,20) {
                rules.allowPass = pass; refresh()
            }
        }
        wager = WagerSlider(theme,{ game.economy.coins },game.preferences.getInteger("Game/Stake",250))
        wager.setBounds(20f,175f,440f,155f); stage.addActor(wager)
        notice = theme.label(stage,if (matchMode == MatchMode.BOTS) "Ставка спишется при старте. Выход возвращает ставку." else "Настройки доступны; сетевой режим пока не подключён.",20f,126f,440f,43f,14,Color.valueOf("ffcf71"))
        notice.setWrap(true)
        val caption=when (matchMode) { MatchMode.BOTS -> "НАЧАТЬ ИГРУ"; MatchMode.ONLINE -> "НАЙТИ ИГРУ"; MatchMode.FRIENDS -> "СОЗДАТЬ КОМНАТУ" }
        theme.button(stage,caption,"green",25f,32f,430f,86f,26) {
            val amount=WagerPolicy.clamp(wager.amount,game.economy.coins)
            game.preferences.putInteger("Game/Stake",amount).flush()
            if (matchMode != MatchMode.BOTS) {
                notice.setText("Ставка сохранена. Сетевые игры пока недоступны; монеты не списаны.")
            } else {
                game.preferences.putBoolean(SettingsScreen.ORIENTATION,true).flush()
                rules.teamPlay=false;rules.save(game.preferences)
                game.screen=GameScreen(game,amount);dispose()
            }
        }
        refresh()
    }
    private fun refresh() {
        fun highlight(actor: Actor, active: Boolean) {
            actor.color = if (active) Color.WHITE else Color(0.48f,0.52f,0.65f,1f)
        }
        counts.forEach { (n,a) -> highlight(a,n == rules.playerCount) }
        decks.forEach { (n,a) -> highlight(a,n == rules.cardCount) }
        modes.forEach { (pass,a) -> highlight(a,pass == rules.allowPass) }
    }
    private fun back() { game.screen = MainMenuScreen(game); dispose() }
    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.07f,0.08f,0.17f,1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta); stage.draw()
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) back()
    }
    override fun resize(width: Int,height: Int) { stage.viewport.update(width,height,true) }
    override fun dispose() { stage.dispose(); theme.dispose() }
    override fun show() {}
    override fun hide() {}
    override fun pause() {}
    override fun resume() {}
}
