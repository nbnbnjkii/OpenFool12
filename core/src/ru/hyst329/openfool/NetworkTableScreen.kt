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
    private var serverOffset=room.getLong("serverTime")-System.currentTimeMillis()
    private var warnedDeadline=0L
    private val warningSound=try {Gdx.audio.newSound(Gdx.files.internal("audio/turn_warning.wav"))} catch(_:Exception){null}
    init {game.ensureGameAssets();build()}
    private fun updateRoom(value: JsonValue) {
        val next=value.get("room")
        serverOffset=next.getLong("serverTime")-System.currentTimeMillis()
        if(next.getLong("version")!=room.getLong("version")){room=next;build()}
    }
    private fun command(path: String,extra: Array<Pair<String,Any>> = emptyArray()) {
        call("POST",path,jsonObject("code" to code,*extra)){updateRoom(it)}
    }
    private fun build() {
        base("КОМНАТА $code")
        status.setBounds(25f,325f,430f,55f)
        theme.image(stage,"table",0f,100f,480f,800f).zIndex=1
        // base background stays behind the table.

        val players=room.get("players");val own=room.getInt("seat")
        val me=players.get(own);val phase=room.getString("phase")
        val turn=room.getInt("turn");val defender=room.getInt("defender")
        theme.label(stage,if(room.getBoolean("pass"))"ПЕРЕВОДНОЙ • без ставок" else "ПОДКИДНОЙ • без ставок",25f,870f,430f,35f,18)
        theme.iconButton(stage,"white_flag",428f,935f,32f){back()}
        for(i in 0 until players.size) {
            val p=players.get(i)
            val relative=(i-own+players.size)%players.size
            val seat=SeatOrder.portraitSeat(relative,players.size.coerceAtLeast(2))
            val x=seat[0];val y=if(relative==0)255f else seat[1]
            theme.iconButton(stage,"avatar_${p.getInt("avatar")}",x,y,58f) {
                if(i!=own&&!p.getBoolean("bot")) {
                    call("POST","/friends/request",jsonObject("code" to code,"playerId" to p.getString("id"))) {
                        status.setText("Запрос в друзья отправлен. Принять можно в меню «Друзья».")
                    }
                }
            }
            if(turn==i&&phase=="playing") {
                stage.addActor(object : Actor() {
                    override fun draw(batch: Batch,parentAlpha: Float) {
                        theme.timerRing(batch,x+29f,y+29f,35f,secondsRemaining())
                    }
                })
            }
            val name=p.getString("name").let{if(it.length>11)it.take(9)+"..." else it}
            theme.label(stage,name,x-22f,y-28f,102f,26f,15,if(turn==i)Color.valueOf("73f0c1") else Color.WHITE)
            val caption=when {
                p.getBoolean("surrendered")->"Сдался"
                p.getBoolean("out")->"Вышел"
                phase=="waiting"->if(p.getBoolean("ready"))"Готов" else "Ожидание"
                room.getBoolean("taking")&&i==defender->"Беру"
                else->"${p.getInt("count")} карт"
            }
            theme.label(stage,caption,x-22f,y-52f,102f,25f,14)
        }
        val table=room.get("table")
        for(i in 0 until table.size) {
            val item=table.get(i);val x=100f+(i%3)*100f;val y=560f-(i/3)*135f
            card(item.getString("attack"),x,y,72f,108f)
            if(!item.get("defense").isNull)card(item.getString("defense"),x+16f,y-22f,72f,108f)
        }
        val remaining=room.getInt("remaining")
        if(phase!="waiting") {
            if(remaining>0) {
                card(room.getString("trumpCard"),350f,758f,55f,82f)
                card("back",330f,795f,55f,82f)
                theme.label(stage,"$remaining",330f,814f,55f,42f,28)
            } else theme.label(stage,"Козырь: "+room.getString("trump").let{when(it){"c"->"Трефы";"d"->"Бубны";"h"->"Червы";else->"Пики"}},309f,823f,165f,40f,17)
        }
        val hand=me.get("hand")
        val gap=if(hand.size<=1)0f else (390f/(hand.size-1)).coerceAtMost(48f)
        for(i in 0 until hand.size) {
            val value=hand.getString(i)
            val actor=card(value,18f+i*gap,100f,72f,108f)
            actor.addListener(object : InputListener() {
                var dx=0f;var dy=0f
                override fun touchDown(event: InputEvent,x: Float,y: Float,pointer: Int,button: Int): Boolean {
                    if(busy||confirmExit||phase!="playing"||turn!=own||pointer!=0)return false
                    dx=x;dy=y;dragging=true;actor.toFront();return true
                }
                override fun touchDragged(event: InputEvent,x: Float,y: Float,pointer: Int){actor.setPosition(event.stageX-dx,event.stageY-dy)}
                override fun touchUp(event: InputEvent,x: Float,y: Float,pointer: Int,button: Int) {
                    dragging=false
                    if(event.stageX in 85f..415f&&event.stageY in 325f..710f) {
                        var target=-1
                        for(t in 0 until table.size){val tx=100f+(t%3)*100f;val ty=560f-(t/3)*135f
                            if(event.stageX in tx..tx+72f&&event.stageY in ty..ty+108f)target=t}
                        command("/rooms/action",arrayOf("type" to "play","card" to value,"target" to target,"pass" to passCard))
                    }
                    actor.setPosition(18f+i*gap,100f)
                }
            })
        }
        if(phase=="waiting") {
            if(!me.getBoolean("ready"))theme.button(stage,"ГОТОВ","green",20f,32f,200f,58f,24){command("/rooms/ready")}
            if(room.getString("owner")==game.online.playerId)theme.button(stage,"НАЧАТЬ","blue",245f,32f,215f,58f,23){command("/rooms/start")}
            theme.button(stage,"ПРИГЛАСИТЬ ДРУГА","purple",110f,365f,260f,52f,19){game.screen=FriendsScreen(game,code);dispose()}
            status.setText("Сообщите друзьям код $code.\nКогда все вошли и готовы, создатель нажимает «Начать».")
        } else if(phase=="ended") {
            status.setText("Партия завершена")
            theme.button(stage,"НОВЫЙ СТОЛ","green",25f,28f,430f,60f,24){leave(true)}
        } else if(turn==own&&!me.getBoolean("out")) {
            val defending=own==defender&&!room.getBoolean("taking")
            if(defending) {
                theme.button(stage,"БЕРУ","gold",20f,28f,195f,60f,24){command("/rooms/action",arrayOf("type" to "take"))}
                if(room.getBoolean("pass"))theme.button(stage,if(passCard)"ПЕРЕВОД: ДА" else "ПЕРЕВОД: НЕТ","slate",235f,28f,225f,60f,18){passCard=!passCard;build()}
            } else if(table.size>0)theme.button(stage,if(room.getBoolean("taking"))"ОТДАТЬ" else "БИТО","green",20f,28f,195f,60f,24){command("/rooms/action",arrayOf("type" to "done"))}
            else theme.label(stage,"Ваш ход — перетащите карту",20f,30f,440f,60f,22)
        }
        if(confirmExit) {
            val overlay=theme.image(stage,"slate",20f,355f,440f,285f)
            overlay.touchable=com.badlogic.gdx.scenes.scene2d.Touchable.enabled
            theme.label(stage,"Сдаться и выйти из комнаты?",35f,535f,410f,75f,24).setWrap(true)
            theme.button(stage,"ДА","gold",40f,402f,185f,60f,24){leave(false)}
            theme.button(stage,"НЕТ","blue",255f,402f,185f,60f,24){confirmExit=false;build()}
        }
    }
    private fun card(value: String,x: Float,y: Float,w: Float,h: Float): Actor {
        val actor=object : Actor() {
            override fun draw(batch: Batch,parentAlpha: Float) {
                batch.color=Color.WHITE
                batch.draw(game.assetManager.get("decks/$deckStyle/$value.png",Texture::class.java),this.x,this.y,width,height)
            }
        }
        actor.setBounds(x,y,w,h);stage.addActor(actor);return actor
    }
    private fun leave(newTable: Boolean) {
        // Best-effort departure must not trap the user if the server restarted.
        game.online.request("POST","/rooms/leave",jsonObject("code" to code),{},{})
        if(newTable){game.screen=FriendSetupScreen(game);dispose()}else menu()
    }
    fun onPlatformBack() { back() }
    override fun back() {
        val me=room.get("players").get(room.getInt("seat"))
        if(room.getString("phase")!="playing"||me.getBoolean("out"))leave(false)
        else {confirmExit=!confirmExit;build()}
    }
    private fun secondsRemaining(): Float = ((room.getLong("deadline")-(System.currentTimeMillis()+serverOffset))/1000f).coerceIn(0f,30f)
    override fun dispose(){warningSound?.dispose();super.dispose()}
    override fun update(delta: Float) {
        val deadline=room.getLong("deadline")
        if(room.getString("phase")=="playing"&&room.getInt("turn")==room.getInt("seat")&&secondsRemaining()<=15f&&warnedDeadline!=deadline) {
            warnedDeadline=deadline;warningSound?.play(0.8f)
        }
        elapsed+=delta
        if(elapsed>0.9f&&!busy&&!dragging&&!confirmExit){elapsed=0f;command("/rooms/state")}
    }
}
