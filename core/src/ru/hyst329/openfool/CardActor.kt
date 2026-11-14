package ru.hyst329.openfool

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.scenes.scene2d.Actor

/**
 * Created by main on 13.03.2017.
 * Licensed under MIT License.
 */

internal class CardActor(game: OpenFoolGame, val card: Card, deckStyle: String) : Actor() {
    var isFaceUp: Boolean = false
    private val face: Sprite = Sprite(game.assetManager.get("decks/$deckStyle/$card.png", Texture::class.java))
    private val back: Sprite = Sprite(game.assetManager.get("decks/$deckStyle/back.png", Texture::class.java))

    init {
        setSize(face.width, face.height)
        face.setOrigin(face.width / 2, face.height / 2)
        back.setOrigin(back.width / 2, back.height / 2)
    }

    // Keep Sprite and Actor transforms identical, including rotated/scaled hit testing.
    override fun draw(batch: Batch?, alpha: Float) {
        val sprite = if (isFaceUp) face else back
        sprite.setBounds(x, y, width, height)
        sprite.setOrigin(originX, originY)
        sprite.setScale(scaleX, scaleY)
        sprite.rotation = rotation
        sprite.draw(batch!!, alpha)
    }

    fun containsStagePoint(stageX: Float, stageY: Float): Boolean {
        val p = stageToLocalCoordinates(com.badlogic.gdx.math.Vector2(stageX,stageY))
        return p.x >= 0f && p.x <= width && p.y >= 0f && p.y <= height
    }

}
