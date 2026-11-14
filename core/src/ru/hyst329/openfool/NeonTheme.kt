package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable

/** Shared portrait artwork and controls. All coordinates are virtual, not device pixels. */
internal class NeonTheme : Disposable {
    private val textures = HashMap<String, Texture>()
    private val fonts = HashMap<Int, BitmapFont>()
    fun texture(name: String): Texture = textures.getOrPut(name) {
        Texture(Gdx.files.internal("neon/$name.png"), true).apply {
            setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
        }
    }
    fun font(size: Int): BitmapFont = fonts.getOrPut(size) {
        val generator = FreeTypeFontGenerator(Gdx.files.internal("fonts/8835.otf"))
        val p = FreeTypeFontGenerator.FreeTypeFontParameter()
        p.size = size * 3
        p.genMipMaps = true
        p.minFilter = Texture.TextureFilter.MipMapLinearLinear
        p.magFilter = Texture.TextureFilter.Linear
        p.characters = (32..126).map { it.toChar() }.joinToString("") +
                (0x410..0x44f).map { it.toChar() }.joinToString("") + "Ёё•→"
        val result = generator.generateFont(p)
        result.data.setScale(1f / 3f)
        generator.dispose()
        result
    }
    fun image(stage: Stage, name: String, x: Float, y: Float, w: Float, h: Float): Image {
        val image = Image(texture(name)); image.setBounds(x,y,w,h); stage.addActor(image); return image
    }
    fun label(stage: Stage, text: String, x: Float, y: Float, w: Float, h: Float,
              size: Int = 22, color: Color = Color.WHITE): Label {
        val label = Label(text, Label.LabelStyle(font(size), color))
        label.setAlignment(Align.center); label.setBounds(x,y,w,h); stage.addActor(label); return label
    }
    fun button(stage: Stage, text: String, color: String, x: Float, y: Float,
               w: Float, h: Float, size: Int = 24, action: () -> Unit): Actor {
        val actor = object : Actor() {
            override fun draw(batch: Batch, parentAlpha: Float) {
                val press = if (listeners.size > 0 && isPressed) 3f else 0f
                batch.color = this.color
                batch.draw(texture(color), x, y - press, width, height)
                batch.color = Color.WHITE
                val f = font(size)
                f.color = Color.WHITE
                f.draw(batch, text, x, y + height/2 + size/2 - press, width, Align.center, false)
            }
            var isPressed = false
        }
        actor.setBounds(x,y,w,h)
        actor.addListener(object : ClickListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                actor.isPressed = true
                return super.touchDown(event,x,y,pointer,button)
            }
            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                actor.isPressed = false
                super.touchUp(event,x,y,pointer,button)
            }
            override fun clicked(event: InputEvent?, x: Float, y: Float) { action() }
        })
        stage.addActor(actor); return actor
    }
    override fun dispose() { textures.values.forEach { it.dispose() }; fonts.values.forEach { it.dispose() } }
}
