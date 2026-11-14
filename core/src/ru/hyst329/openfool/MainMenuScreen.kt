package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.viewport.FitViewport

/** Portrait lobby. Unimplemented network features are explicitly labelled, never simulated. */
internal class MainMenuScreen(private val game: OpenFoolGame) : Screen {
    private val stage = Stage(FitViewport(480f, 1000f))
    private val theme = NeonTheme()
    private val status: Label
    private var message = ""
    init {
        Gdx.input.inputProcessor = stage
        game.orientationHelper.requestOrientation(OrientationHelper.Orientation.PORTRAIT)
        theme.image(stage,"lobby",0f,0f,480f,1000f)
        theme.image(stage,"avatar",20f,892f,78f,78f)
        theme.label(stage,"Игрок_1",110f,925f,180f,40f,27)
        theme.label(stage,"${game.economy.coins} монет  •  ${game.economy.crystals} кристаллов",107f,892f,244f,28f,16,Color.valueOf("a9b2d0"))
        theme.button(stage,"...","slate",420f,914f,42f,42f,24) { settings() }
        theme.image(stage,"banner",20f,677f,440f,186f)
        status = theme.label(stage,"",20f,628f,440f,32f,17,Color.valueOf("9fa9c4"))
        theme.button(stage,"ИГРАТЬ ОНЛАЙН","green",20f,495f,440f,102f,29) {
            message = "Онлайн-режим пока в разработке"
        }
        theme.button(stage,"ИГРАТЬ С ДРУЗЬЯМИ","purple",20f,374f,440f,96f,24) {
            message = "Игра по сети пока недоступна"
        }
        theme.button(stage,"ПРОТИВ БОТА","blue",20f,252f,440f,96f,29) { choosePlayers() }
        theme.button(stage,"ТУРНИРЫ  •  СКОРО","slate",20f,132f,440f,94f,25) {
            message = "Турниры появятся в следующих версиях"
        }
        theme.label(stage,"ГЛАВНАЯ",14f,25f,130f,40f,19,Color.valueOf("49e38a"))
        theme.button(stage,"ПРАВИЛА","slate",166f,25f,144f,44f,17) {
            message = "Тяните карту на стол для хода или на карту соперника для отбоя."
        }
        theme.button(stage,"НАСТРОЙКИ","slate",323f,25f,143f,44f,16) { settings() }
    }
    private fun settings() { game.screen = SettingsScreen(game); dispose() }
    private fun choosePlayers() {
        game.screen = BotSetupScreen(game)
        dispose()
    }
    override fun render(delta: Float) {
        game.assetManager.update(3)
        status.setText(message)
        status.setFontScale(if (message.length > 48) 0.7f else 1f)
        Gdx.gl.glClearColor(0.07f,0.08f,0.17f,1f); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta); stage.draw()
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) Gdx.app.exit()
    }
    override fun resize(width: Int,height: Int) { stage.viewport.update(width,height,true) }
    override fun dispose() { stage.dispose(); theme.dispose() }
    override fun show() {}
    override fun hide() {}
    override fun pause() {}
    override fun resume() {}
}
