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
internal class BotSetupScreen(private val game: OpenFoolGame) : Screen {
    private val stage = Stage(FitViewport(480f, 1000f))
    private val theme = NeonTheme()
    private val rules = RuleSet(game.preferences)
    private val counts = HashMap<Int, Actor>()
    private val decks = HashMap<Int, Actor>()
    private val modes = HashMap<Boolean, Actor>()
    private lateinit var notice: Label
    private val summary: Label
    init {
        Gdx.input.inputProcessor = stage
        game.orientationHelper.requestOrientation(OrientationHelper.Orientation.PORTRAIT)
        theme.image(stage,"lobby",0f,0f,480f,1000f)
        theme.button(stage,"НАЗАД","slate",20f,924f,110f,48f,18) { back() }
        theme.label(stage,"ИГРА С БОТАМИ",20f,834f,440f,60f,31)
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
        val help = theme.label(stage,"Тяните карту на стол: сначала отбой, иначе перевод картой того же ранга.\nПереводить можно только до начала отбоя.",25f,238f,430f,85f,17,Color.valueOf("b6c0dc"))
        help.setWrap(true)
        notice = theme.label(stage,"",20f,184f,440f,45f,16,Color.valueOf("ffcf71"))
        summary = theme.label(stage,"",20f,131f,440f,40f,18,Color.valueOf("83f5c5"))
        theme.button(stage,"НАЧАТЬ ИГРУ","green",25f,32f,430f,86f,28) {
            game.preferences.putBoolean(SettingsScreen.ORIENTATION,true).flush()
            rules.teamPlay = false
            rules.save(game.preferences)
            game.screen = GameScreen(game); dispose()
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
        summary.setText("Вы + ${rules.playerCount-1} ботов  •  ${rules.cardCount} карты")
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
