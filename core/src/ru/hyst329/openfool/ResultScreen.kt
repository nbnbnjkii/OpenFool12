package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.FitViewport

internal class ResultScreen(private val game: OpenFoolGame,
                            private val result: Result,
                            private val playersPlaces: Map<Int, String>) : Screen {
    internal enum class Result { TEAM_WON, TEAM_LOST, TEAM_PARTNER_LOST, TEAM_DRAW, WON, LOST, DRAW }
    private val stage = Stage(FitViewport(480f, 1000f))
    private val theme = NeonTheme()
    init {
        game.orientationHelper.requestOrientation(OrientationHelper.Orientation.PORTRAIT)
        Gdx.input.inputProcessor = stage
        theme.image(stage,"lobby",0f,0f,480f,1000f)
        theme.image(stage,"avatar",180f,750f,120f,120f)
        val prefix = when (result) {
            Result.TEAM_WON -> "TeamVictory"
            Result.TEAM_LOST -> "TeamDefeat"
            Result.TEAM_PARTNER_LOST -> "TeamPartnerDefeat"
            Result.TEAM_DRAW -> "TeamDraw"
            Result.WON -> "Victory"
            Result.LOST -> "Defeat"
            Result.DRAW -> "Draw"
        }
        theme.label(stage,game.localeBundle.get(prefix+"Header"),20f,640f,440f,80f,32)
        val description = theme.label(stage,game.localeBundle.get(prefix+"Text"),30f,510f,420f,110f,22,Color.valueOf("b6c0dc"))
        description.setWrap(true)
        val places = playersPlaces.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}.  ${it.value}" }
        theme.label(stage,places,30f,285f,420f,200f,25)
        theme.button(stage,"ЕЩЁ ПАРТИЯ","green",30f,165f,420f,86f,27) {
            game.screen = GameScreen(game); dispose()
        }
        theme.button(stage,"ГЛАВНОЕ МЕНЮ","purple",30f,57f,420f,80f,25) { menu() }
    }
    private fun menu() { game.screen = MainMenuScreen(game); dispose() }
    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.07f,0.08f,0.17f,1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta); stage.draw()
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) menu()
    }
    override fun resize(width: Int,height: Int) { stage.viewport.update(width,height,true) }
    override fun dispose() { stage.dispose(); theme.dispose() }
    override fun show() {}
    override fun hide() {}
    override fun pause() {}
    override fun resume() {}
}
