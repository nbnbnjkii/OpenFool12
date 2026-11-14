package ru.hyst329.openfool

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

class AndroidLauncher : AndroidApplication() {
    private lateinit var foolGame: OpenFoolGame
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration()
        config.useAccelerometer = false
        config.useCompass = false
        foolGame = OpenFoolGame(AndroidOrientationHelper(this))
        initialize(foolGame, config)
    }
    @Deprecated("Legacy Back bridge is intentional for the LibGDX backend")
    override fun onBackPressed() {
        if (this::foolGame.isInitialized && foolGame.handleTableBack()) return
        super.onBackPressed()
    }
}