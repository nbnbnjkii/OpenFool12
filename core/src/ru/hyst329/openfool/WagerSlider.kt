package ru.hyst329.openfool

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.utils.Align

/** Native Scene2D slider: unreachable stakes stay gray and the thumb cannot exceed the wallet. */
internal class WagerSlider(private val theme: NeonTheme, private val balance: () -> Int, initial: Int) : Actor() {
    var amount = WagerPolicy.clamp(initial,balance()); private set
    private var pointer = -1
    init {
        addListener(object : InputListener() {
            override fun touchDown(event: InputEvent?,x: Float,y: Float,pointer: Int,button: Int): Boolean {
                if (button!=0 || this@WagerSlider.pointer!=-1 || y !in 35f..100f) return false
                this@WagerSlider.pointer=pointer; select(x); return true
            }
            override fun touchDragged(event: InputEvent?,x: Float,y: Float,pointer: Int) {
                if (this@WagerSlider.pointer==pointer) select(x)
            }
            override fun touchUp(event: InputEvent?,x: Float,y: Float,pointer: Int,button: Int) {
                if (this@WagerSlider.pointer==pointer) this@WagerSlider.pointer=-1
            }
        })
    }
    private fun select(localX: Float) { amount=WagerPolicy.amount((localX-18f)/(width-36f),balance()) }
    override fun draw(batch: Batch,parentAlpha: Float) {
        amount=WagerPolicy.clamp(amount,balance())
        batch.color=Color.WHITE
        batch.draw(theme.texture("slate"),x,y,width,height)
        theme.font(23).color=Color.WHITE
        theme.font(23).draw(batch,"Ваша ставка",x+14f,y+height-18f)
        theme.font(27).color=Color.WHITE
        theme.font(27).draw(batch,WagerPolicy.label(amount),x+205f,y+height-16f,width-248f,Align.right,false)
        batch.draw(theme.texture("coin"),x+width-37f,y+height-43f,25f,25f)
        val left=x+18f; val track=width-36f; val cy=y+65f
        batch.color=Color.valueOf("253451")
        batch.draw(theme.texture("pixel"),left,cy-4f,track,8f)
        batch.color=Color.valueOf("6483a9")
        batch.draw(theme.texture("pixel"),left,cy-4f,track*WagerPolicy.position(balance()),8f)
        batch.color=Color.valueOf("5ce6c0")
        batch.draw(theme.texture("pixel"),left,cy-4f,track*WagerPolicy.position(amount),8f)
        batch.color=Color.WHITE
        batch.draw(theme.texture("slider_knob"),left+track*WagerPolicy.position(amount)-17f,cy-17f,34f,34f)
        val ticks=listOf(100,1000,10000,100000,1000000,10000000)
        for ((i,tick) in ticks.withIndex()) {
            theme.font(12).color=if (tick<=balance()) Color.WHITE else Color.valueOf("78859c")
            theme.font(12).draw(batch,WagerPolicy.label(tick),left+track*i/5f-23f,y+27f,46f,Align.center,false)
        }
        batch.color=Color.WHITE
    }
}
