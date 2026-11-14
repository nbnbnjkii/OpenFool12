package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.utils.JsonValue

/** Server-owned table: opponent hands never reach the client. Cards are played by drag/drop. */
internal class NetworkTableScreen(game: OpenFoolGame,private var room: JsonValue) : OnlineScreen(game) {
    private val code=room.getString("code")
    private val deckStyle=game.preferences.getString(SettingsScreen.DECK,"rus")
    private var elapsed=0f
    private var dragging=false
    private var confirmExit=false
    private var passCard=false
    private var animateDeal=false
    private var discardOpen=false
    private var undoIcon: Actor?=null
    private var serverOffset=room.getLong("serverTime")-System.currentTimeMillis()
    private var warnedDeadline=0L
    private val warningSound=try {Gdx.audio.newSound(Gdx.files.internal("audio/turn_warning.wav"))} catch(_:Exception){null}
    init {game.ensureGameAssets();build()}
    private fun updateRoom(value: JsonValue) {
        val next=value.get("room")
        serverOffset=next.getLong("serverTime")-System.currentTimeMillis()
        if(next.getLong("version")!=room.getLong("version")){
            animateDeal=room.getString("phase")=="waiting"&&next.getString("phase")=="playing"
            room=next;build();animateDeal=false
        }
    }
    private fun command(path: String,extra: Array<Pair<String,Any>> = emptyArray()) {
        if(path=="/rooms/action"&&locked()){status.setText("Подождите 3-секундное окно отмены хода");return}
        call("POST",path,jsonObject("code" to code,"economyVersion" to 1,*extra)){updateRoom(it)}
    }
    private fun build() {
        base("")
        status.setBounds(15f,267f,450f,45f)
        theme.image(stage,"table",0f,0f,480f,1000f).zIndex=1
        // base background stays behind the table.

        val players=room.get("players");val own=room.getInt("seat")
        val me=players.get(own);val phase=room.getString("phase")
        val turn=room.getInt("turn");val defender=room.getInt("defender")
        theme.iconButton(stage,"white_flag",22f,937f,38f){back()}
        theme.iconButton(stage,"ui_friends",22f,873f,38f){game.screen=FriendsScreen(game,code);dispose()}
        for(i in 0 until players.size) {
            val p=players.get(i)
            val relative=(i-own+players.size)%players.size
            val seat=SeatOrder.portraitSeat(relative,players.size.coerceAtLeast(2))
            val x=seat[0];val y=seat[1]
            if(relative!=0) {
                val hx=if(x<100f)-10f else if(x>300f)425f else 202f
                for(n in 0 until p.getInt("count").coerceAtMost(12))card("back",hx+n*5f,y-65f-n*5f,36f,54f)
            }
            theme.iconButton(stage,"avatar_${p.getInt("avatar")}",x,y,64f) {
                if(i!=own&&!p.getBoolean("bot")) {
                    call("POST","/friends/request",jsonObject("code" to code,"playerId" to p.getString("id"))) {
                        status.setText("Запрос в друзья отправлен. Принять можно в меню «Друзья».")
                    }
                }
            }
            if(turn==i&&phase=="playing") {
                stage.addActor(object : Actor() {
                    override fun draw(batch: Batch,parentAlpha: Float) {
                        theme.timerRing(batch,x+32f,y+32f,39f,secondsRemaining())
                    }
                })
            }
            val name=p.getString("name").let{if(it.length>11)it.take(9)+"..." else it}
            if(relative!=0)theme.label(stage,name,x-12f,y-28f,90f,25f,17,if(turn==i)Color.valueOf("73f0c1") else Color.WHITE)
            val caption=when {
                p.getBoolean("surrendered")->"Сдался"
                p.getBoolean("out")->"Вышел"
                phase=="waiting"->if(p.getBoolean("ready"))"Готов" else "Ожидание"
                room.getBoolean("taking")&&i==defender->"Беру"
                else->"${p.getInt("count")} карт"
            }
            if(relative!=0)theme.label(stage,caption,x-12f,y-50f,90f,25f,15)
        }
        val table=room.get("table")
        for(i in 0 until table.size) {
            val item=table.get(i);val (x,y)=PortraitTableLayout.attack(i)
            card(item.getString("attack"),x,y,72f,108f)
            if(!item.get("defense").isNull)card(item.getString("defense"),x+10f,y-10f,72f,108f)
        }
        val remaining=room.getInt("remaining")
        if(phase!="waiting"&&remaining>0) {
            card(room.getString("trumpCard"),PortraitTableLayout.DECK_X+18f,PortraitTableLayout.DECK_Y,72f,108f).rotation=-90f
            card("back",PortraitTableLayout.DECK_X,PortraitTableLayout.DECK_Y,72f,108f)
        }
        val hand=me.get("hand")
        for(i in 0 until hand.size) {
            val value=hand.getString(i)
            val faceWidth=game.assetManager.get("decks/$deckStyle/$value.png",Texture::class.java).width*0.36f
            val pose=PortraitTableLayout.hand(i,hand.size,faceWidth)
            val actor=card(value,pose.x,pose.y,PortraitTableLayout.HAND_CARD_WIDTH,PortraitTableLayout.HAND_CARD_HEIGHT)
            actor.rotation=pose.angle
            if(animateDeal) {
                actor.setPosition(PortraitTableLayout.DECK_X,PortraitTableLayout.DECK_Y)
                actor.rotation=0f;actor.isVisible=false
                actor.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.delay(i*0.055f),
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.visible(true),
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.parallel(
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.moveTo(pose.x,pose.y,0.35f),
                        com.badlogic.gdx.scenes.scene2d.actions.Actions.rotateTo(pose.angle,0.35f))))
            }
            actor.addListener(object : InputListener() {
                var originalZ=0
                override fun touchDown(event: InputEvent,x: Float,y: Float,pointer: Int,button: Int): Boolean {
                    if(busy||dragging||locked()||discardOpen||actor.actions.size>0||confirmExit||phase!="playing"||turn!=own||pointer!=0)return false
                    originalZ=actor.zIndex;dragging=true;actor.toFront();actor.rotation=0f
                    actor.setPosition(event.stageX-actor.width/2,event.stageY-actor.height/2)
                    return true
                }
                override fun touchDragged(event: InputEvent,x: Float,y: Float,pointer: Int){actor.setPosition(event.stageX-actor.width/2,event.stageY-actor.height/2)}
                override fun touchUp(event: InputEvent,x: Float,y: Float,pointer: Int,button: Int) {
                    dragging=false
                    if(PortraitTableLayout.onTable(event.stageX,event.stageY)) {
                        var target=-1
                        for(t in 0 until table.size){val (tx,ty)=PortraitTableLayout.attack(t)
                            if(event.stageX in tx..tx+72f&&event.stageY in ty..ty+108f)target=t}
                        command("/rooms/action",arrayOf("type" to "play","card" to value,"target" to target,"pass" to passCard))
                    }
                    actor.setPosition(pose.x,pose.y);actor.rotation=pose.angle;actor.zIndex=originalZ
                }
            })
        }
        // Opaque panel masks the bottom half of the same HandFan used by the bot table.
        theme.image(stage,"hud_panel",0f,0f,480f,HandFan.PANEL_TOP).touchable=com.badlogic.gdx.scenes.scene2d.Touchable.enabled
        theme.iconButton(stage,"avatar_${me.getInt("avatar")}",210f,25f,66f){}
        val actions=TurnControls.actions(phase=="playing"&&turn==own&&!me.getBoolean("out"),false,
            table.size,(0 until table.size).count{!table.get(it).get("defense").isNull},
            own==defender,own==turn&&own!=defender,room.getBoolean("taking"),false)
        var slot="Ожидание"
        if(phase=="waiting") {
            if(!me.getBoolean("ready"))theme.button(stage,"ГОТОВ","green",12f,28f,177f,62f,26){command("/rooms/ready")}
            else if(room.getString("owner")==game.online.playerId)theme.button(stage,"НАЧАТЬ","green",12f,28f,177f,62f,24){command("/rooms/start")}
            else slot="Ждём игроков"
            status.setText("Код $code • Все нажимают «Готов», создатель — «Начать».")
        } else if(phase=="ended") {
            status.setText("Партия завершена")
            theme.button(stage,"ГОТОВ","green",12f,28f,177f,62f,26){leave(true)}
        } else if(actions.take)theme.button(stage,"БЕРУ","gold",12f,28f,177f,62f,26){command("/rooms/action",arrayOf("type" to "take"))}
        else if(actions.done||actions.give)theme.button(stage,if(actions.give)"ОТДАТЬ" else "БИТО","green",12f,28f,177f,62f,26){command("/rooms/action",arrayOf("type" to "done"))}
        else if(TurnControls.showYourTurn(actions,turn==own,phase=="playing"&&!me.getBoolean("out"),room.getBoolean("taking")&&own==defender,false))slot="Ваш ход"
        if(phase=="playing"&&actions.take&&room.getBoolean("pass"))theme.button(stage,if(passCard)"ПЕРЕВОД: ДА" else "ПЕРЕВОД: НЕТ","slate",12f,310f,177f,38f,16){passCard=!passCard;build()}
        undoIcon=null
        // The server validates ownership, unlock state, cost and undo deadline.
        if(phase=="playing"&&!me.getBoolean("out"))for((index,icon) in listOf("perk_undo","perk_discard","perk_hint").withIndex()) {
            theme.iconButton(stage,icon,309f+index*58f,40f,44f){
                if(!busy) {
                    val type=listOf("undo","discard","highlight")[index]
                    call("POST","/rooms/perk",jsonObject("code" to code,"type" to type)) {
                        updateRoom(it)
                        if(type=="discard"){discardOpen=true;build()}
                    }
                }
            }.apply { if(index==0)undoIcon=this }
        }
        val slotText=slot
        stage.addActor(object : Actor() {
            override fun draw(batch: Batch,parentAlpha: Float) {
                drawHud(batch,phase,remaining)
                val showSlot=phase=="playing"&&!actions.take&&!actions.done&&!actions.give || phase=="waiting"&&me.getBoolean("ready")&&room.getString("owner")!=game.online.playerId
                if(showSlot) {
                    theme.font(if(slotText=="Ваш ход")25 else 18).apply {
                        color=if(slotText=="Ваш ход")Color.valueOf("73f0c1") else Color.valueOf("aebbd3")
                        draw(batch,slotText,12f,69f,177f,com.badlogic.gdx.utils.Align.center,false)
                    }
                }
                if(phase=="playing"&&turn==own)theme.timerRing(batch,243f,58f,40f,secondsRemaining())
                theme.font(14).color=Color.WHITE
                theme.font(14).draw(batch,me.getString("name").take(10),198f,18f,90f,com.badlogic.gdx.utils.Align.center,false)
            }
        })
        if(discardOpen) {
            val cards=room.get("discard")
            theme.image(stage,"slate",0f,0f,480f,1000f)
            theme.label(stage,"ОТБОЙ • нажмите в любом месте",15f,920f,450f,45f,23)
            if(cards!=null)for(i in 0 until cards.size)card(cards.getString(i),25f+(i%8)*54f,812f-(i/8)*105f,48f,72f)
            if(cards==null||cards.size==0)theme.label(stage,"Отбой пока пуст",20f,500f,440f,60f,25)
            val close=object : Actor() {}
            close.setBounds(0f,0f,480f,1000f)
            close.addListener(object : InputListener() {
                override fun touchDown(event: InputEvent,x: Float,y: Float,pointer: Int,button: Int): Boolean {
                    discardOpen=false;build();return true
                }
            })
            stage.addActor(close)
        }
        if(confirmExit) {
            theme.image(stage,"pixel",0f,0f,480f,1000f).apply {
                color=Color(0f,0f,0f,0.78f)
                touchable=com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            }
            val overlay=theme.image(stage,"slate",20f,355f,440f,285f)
            overlay.touchable=com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            theme.label(stage,"Сдаться и выйти? Ставка ${room.getInt("stake",0)} будет проиграна. Доплаты за выход нет.",35f,520f,410f,100f,22).setWrap(true)
            theme.button(stage,"ДА","gold",40f,402f,185f,60f,24){leave(false)}
            theme.button(stage,"НЕТ","blue",255f,402f,185f,60f,24){confirmExit=false;build()}
        }
    }
    private fun drawHud(batch: Batch,phase: String,remaining: Int) {
        batch.color=Color.WHITE
        theme.font(18).color=Color.valueOf("c7d8ee")
        theme.font(18).draw(batch,if(room.getBoolean("pass"))"ПЕРЕВОДНОЙ" else "ПОДКИДНОЙ",90f,973f,235f,com.badlogic.gdx.utils.Align.center,false)
        batch.draw(theme.texture(if(room.getBoolean("pass"))"mode_pass" else "mode_throw"),191f,923f,35f,35f)
        theme.font(16).color=Color.WHITE
        theme.font(16).draw(batch,"Ставка: ${WagerPolicy.label(room.getInt("stake",0))}",320f,968f,123f,com.badlogic.gdx.utils.Align.right,false)
        batch.draw(theme.texture("coin"),450f,950f,21f,21f)
        theme.font(14).color=Color.valueOf("afbed4")
        theme.font(14).draw(batch,"Код: $code",12f,927f,110f,com.badlogic.gdx.utils.Align.left,false)
        if(phase!="waiting"&&remaining==0) {
            val suit=when(room.getString("trump")){"c"->"clubs";"d"->"diamonds";"h"->"hearts";else->"spades"}
            batch.draw(game.assetManager.get("suits/$suit.png",Texture::class.java),430f,850f,32f,32f)
            theme.font(15).color=Color.WHITE
            theme.font(15).draw(batch,"Козырь",413f,908f,65f,com.badlogic.gdx.utils.Align.center,false)
        }
        if(phase!="waiting"&&remaining>0) {
            theme.font(30).color=Color.WHITE
            theme.font(30).draw(batch,"$remaining",PortraitTableLayout.DECK_X,PortraitTableLayout.DECK_Y+70f,72f,com.badlogic.gdx.utils.Align.center,false)
        }
        if(phase=="playing") {
            val perks=room.get("perks")
            val prices=listOf(2,if(perks?.getBoolean("discard",false)==true)0 else 3,if(perks?.getBoolean("highlight",false)==true)0 else 1)
            for((i,cost) in prices.withIndex()) {
                val x=309f+58f*i
                theme.font(15).color=Color.valueOf("d6e6ff")
                theme.font(15).draw(batch,"$cost",x,105f,25f,com.badlogic.gdx.utils.Align.center,false)
                batch.draw(theme.texture("crystal"),x+25f,90f,14f,16f)
            }
            if(locked()) {
                theme.font(14).color=Color.valueOf("ffcf83")
                theme.font(14).draw(batch,"Окно отмены хода: 3 сек",15f,263f,450f,com.badlogic.gdx.utils.Align.center,false)
            }
        }
        batch.draw(theme.texture("coin"),310f,7f,16f,16f)
        batch.draw(theme.texture("crystal"),399f,7f,16f,16f)
        theme.font(13).color=Color.WHITE
        theme.font(13).draw(batch,"${game.online.coins}",331f,22f,66f,com.badlogic.gdx.utils.Align.left,false)
        theme.font(13).draw(batch,"${game.online.crystals}",420f,22f,50f,com.badlogic.gdx.utils.Align.left,false)
    }
    private fun card(value: String,x: Float,y: Float,w: Float,h: Float): Actor {
        val texture=game.assetManager.get("decks/$deckStyle/$value.png",Texture::class.java)
        val actor=object : Actor() {
            override fun draw(batch: Batch,parentAlpha: Float) {
                batch.color=Color.WHITE
                val ownHand=room.get("players").get(room.getInt("seat")).get("hand")
                val hints=room.get("hints")
                if(hints!=null&&hints.any{it.asString()==value}&&ownHand.any{it.asString()==value})batch.color=Color(0.65f,1f,0.76f,1f)
                batch.draw(texture,this.x,this.y,0f,0f,width,height,1f,1f,rotation,0,0,texture.width,texture.height,false,false)
                batch.color=Color.WHITE
            }
        }
        actor.setBounds(x,y,w*texture.width/360f,h);stage.addActor(actor);return actor
    }
    private fun leave(newTable: Boolean) {
        if(busy)return
        call("POST","/rooms/leave",jsonObject("code" to code,"economyVersion" to 1)) {
            if(newTable){game.screen=FriendSetupScreen(game);dispose()}else menu()
        }
    }
    private fun locked(): Boolean = room.getLong("lockedUntil",0L)>System.currentTimeMillis()+serverOffset
    fun onPlatformBack() { back() }
    override fun back() {
        if(discardOpen){discardOpen=false;build();return}
        val me=room.get("players").get(room.getInt("seat"))
        if(room.getString("phase")!="playing"||me.getBoolean("out"))leave(false)
        else {confirmExit=!confirmExit;build()}
    }
    private fun secondsRemaining(): Float = ((room.getLong("deadline")-(System.currentTimeMillis()+serverOffset))/1000f).coerceIn(0f,30f)
    override fun dispose(){warningSound?.dispose();super.dispose()}
    override fun update(delta: Float) {
        undoIcon?.color=if(room.getLong("undoUntil",0L)>System.currentTimeMillis()+serverOffset)Color.WHITE else Color(0.45f,0.45f,0.55f,1f)
        val deadline=room.getLong("deadline")
        if(room.getString("phase")=="playing"&&room.getInt("turn")==room.getInt("seat")&&secondsRemaining()<=15f&&warnedDeadline!=deadline) {
            warnedDeadline=deadline;warningSound?.play(0.8f)
        }
        elapsed+=delta
        if(elapsed>0.9f&&!busy&&!dragging&&!confirmExit&&!discardOpen){elapsed=0f;command("/rooms/state")}
    }
}
