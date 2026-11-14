package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.JsonValue
import com.badlogic.gdx.utils.viewport.FitViewport

internal abstract class OnlineScreen(protected val game: OpenFoolGame) : Screen {
    protected val stage=Stage(FitViewport(480f,1000f))
    protected val theme=NeonTheme()
    protected var alive=true
    protected var busy=false
    protected lateinit var status: Label
    init { Gdx.input.inputProcessor=stage;game.orientationHelper.requestOrientation(OrientationHelper.Orientation.PORTRAIT) }
    protected fun base(title: String) {
        stage.clear();theme.image(stage,"lobby",0f,0f,480f,1000f)
        theme.label(stage,title,20f,910f,440f,60f,28)
        status=theme.label(stage,"",25f,120f,430f,85f,17,Color.valueOf("ffcf83"));status.setWrap(true)
    }
    protected fun field(text: String,y: Float,password: Boolean=false): TextField {
        val style=TextField.TextFieldStyle().apply {
            font=theme.font(23);fontColor=Color.WHITE
            background=TextureRegionDrawable(theme.texture("slate"))
            cursor=TextureRegionDrawable(theme.texture("pixel"));selection=TextureRegionDrawable(theme.texture("blue"))
        }
        val f=TextField(text,style);f.setBounds(35f,y,410f,60f);f.maxLength=if(password)128 else 24
        if(password){f.setPasswordMode(true);f.setPasswordCharacter('*')}
        stage.addActor(f);return f
    }
    protected fun call(method: String,path: String,body: JsonValue?=null,done: (JsonValue)->Unit) {
        if(busy)return
        busy=true
        game.online.request(method,path,body,{ result -> if(alive){busy=false;done(result)} },{message ->if(alive){busy=false;status.setText(message)}})
    }
    protected fun menu() { game.screen=MainMenuScreen(game);dispose() }
    override fun render(delta: Float) {
        if(Gdx.input.isKeyJustPressed(Input.Keys.BACK)||Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)){back();return}
        update(delta)
        Gdx.gl.glClearColor(0.04f,0.05f,0.12f,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta);stage.draw()
    }
    protected open fun update(delta: Float) {}
    protected open fun back() { menu() }
    override fun show() {}
    override fun hide() {Gdx.input.setOnscreenKeyboardVisible(false)}
    override fun resize(width: Int,height: Int){stage.viewport.update(width,height,true)}
    override fun pause() {}
    override fun resume() {}
    override fun dispose(){alive=false;stage.dispose();theme.dispose()}
}

internal class AuthScreen(game: OpenFoolGame,verify: Boolean=true) : OnlineScreen(game) {
    private var registration=game.online.token.isEmpty()
    init {
        form()
        if(verify&&game.online.token.isNotEmpty()) {
            status.setText("Проверяем сохранённый вход...")
            call("GET","/me") {game.online.updateProfile(it.get("user"));enter(it)}
        }
    }
    private fun enter(result: JsonValue) {
        if(result.has("room")){game.screen=NetworkTableScreen(game,result.get("room"));dispose()}else menu()
    }
    private fun form() {
        base(if(registration)"РЕГИСТРАЦИЯ" else "ВХОД В АККАУНТ")
        theme.label(stage,"Логин: латиница, цифры, _ (3–24)",25f,798f,430f,40f,18)
        val login=field("",727f)
        theme.label(stage,"Пароль: минимум 8 символов",25f,658f,430f,40f,18)
        val password=field("",586f,true)
        val confirmation=if(registration){
            theme.label(stage,"Повторите пароль",25f,523f,430f,40f,18);field("",455f,true)
        }else null
        theme.button(stage,if(registration)"ЗАРЕГИСТРИРОВАТЬСЯ" else "ВОЙТИ","green",25f,340f,430f,70f,23) {
            if(confirmation!=null&&confirmation.text!=password.text)status.setText("Пароли не совпадают")
            else {
                Gdx.input.setOnscreenKeyboardVisible(false)
                call("POST",if(registration)"/auth/register" else "/auth/login",jsonObject("login" to login.text,"password" to password.text,"nickname" to login.text)) {
                    password.text="";confirmation?.text="";game.online.remember(it);enter(it)
                }
            }
        }
        theme.button(stage,if(registration)"УЖЕ ЕСТЬ АККАУНТ? ВХОД" else "СОЗДАТЬ АККАУНТ","purple",25f,245f,430f,62f,20) {
            if(!busy){registration=!registration;form()}
        }
        theme.label(stage,"Пароль не сохраняется на телефоне.\nВход запоминается на 30 дней.",25f,35f,430f,75f,17,Color.valueOf("afbed4")).setWrap(true)
    }
    override fun back() {Gdx.app.exit()}
}

/** Same visual rows and controls as BotSetupScreen; only the friend-specific extras differ. */
internal class FriendSetupScreen(game: OpenFoolGame) : OnlineScreen(game) {
    private var capacity=6
    private var bots=0
    private var addBots=false
    private var deckSize=36
    private var pass=false
    private var joining=false
    private var editingBots=false
    private var stake=game.preferences.getInteger("Network/Stake",250)
    private var wager: WagerSlider?=null
    init {build();call("GET","/me"){game.online.updateProfile(it.get("user"));build()}}
    private fun build() {
        base("")
        status.setBounds(20f,126f,440f,43f)
        theme.button(stage,"НАЗАД","slate",20f,924f,110f,48f,18) {
            if(joining||editingBots){joining=false;editingBots=false;build()}else menu()
        }
        theme.button(stage,if(joining)"СОЗДАТЬ СТОЛ" else "ВВЕСТИ КОД","blue",280f,924f,180f,48f,18) {
            joining=!joining;Gdx.input.setOnscreenKeyboardVisible(false);build()
        }
        theme.label(stage,"ИГРА С ДРУЗЬЯМИ",20f,834f,440f,60f,28)
        theme.label(stage,"Сетевой баланс: ${game.online.coins} монет • ${game.online.crystals} кристаллов",20f,896f,440f,25f,14)
        if(editingBots) {
            theme.button(stage,if(addBots)"[X] ДОБАВИТЬ БОТОВ" else "[ ] ДОБАВИТЬ БОТОВ","slate",20f,679f,440f,70f,22) {
                addBots=!addBots;bots=if(addBots&&capacity>2)1 else 0;build()
            }
            if(addBots)for(n in 0..capacity-2)theme.button(stage,"$n",if(n==bots)"purple" else "slate",25f+n*86f,514f,76f,70f,26){bots=n;build()}
            status.setText("Минимум 2 места людям. Боты используют ту же ставку.")
            theme.button(stage,"ГОТОВО","green",25f,32f,430f,86f,26){editingBots=false;build()}
            return
        }

        if(joining) {
            theme.label(stage,"КОД КОМНАТЫ",20f,679f,440f,40f,22)
            val code=field("",580f);code.maxLength=4;code.messageText="4 цифры"
            code.textFieldFilter=TextField.TextFieldFilter {_,c->c in '0'..'9'}
            theme.button(stage,"ПРИСОЕДИНИТЬСЯ","green",25f,32f,430f,86f,26) {
                Gdx.input.setOnscreenKeyboardVisible(false)
                call("POST","/rooms/join",jsonObject("code" to code.text)) {game.screen=NetworkTableScreen(game,it.get("room"));dispose()}
            }
            status.setText("Введите код, который сообщил создатель комнаты.")
            return
        }
        theme.label(stage,"Участников за столом (включая вас)",20f,765f,440f,40f,20)
        for(n in 2..6)theme.button(stage,"$n",if(n==capacity)"purple" else "slate",20f+(n-2)*90f,679f,80f,70f,30) {
            capacity=n;bots=bots.coerceAtMost(n-2);if(deckSize<n*6)deckSize=36;build()
        }
        theme.label(stage,"КОЛОДА",20f,598f,440f,40f,22)
        for((index,n) in listOf(24,36,52).withIndex())theme.button(stage,"$n карты",if(n==deckSize)"blue" else "slate",20f+index*150f,514f,140f,70f,22) {
            if(n<capacity*6)status.setText("24 карты: максимум 4 участника по 6 карт")
            else {deckSize=n;build()}
        }
        theme.label(stage,"РЕЖИМ ИГРЫ",20f,430f,440f,40f,22)
        for((index,value) in listOf(false,true).withIndex())theme.button(stage,if(value)"ПЕРЕВОДНОЙ" else "ПОДКИДНОЙ",if(value==pass)"purple" else "slate",20f+index*225f,347f,215f,70f,20) {
            pass=value;build()
        }
        theme.button(stage,"БОТЫ: ${if(addBots)bots else 0}","slate",145f,924f,120f,48f,17) {
            stake=wager?.amount?:stake;editingBots=true;build()
        }
        wager=WagerSlider(theme,{game.online.coins},stake).also {it.setBounds(20f,175f,440f,155f);stage.addActor(it)}
        theme.button(stage,"СОЗДАТЬ КОМНАТУ","green",25f,32f,430f,86f,26) {
            if(!game.online.supportsEconomy){status.setText("Сначала обновите сервер для ставок и инструментов");return@button}
            stake=wager?.amount?:stake
            game.preferences.putInteger("Network/Stake",stake).flush()
            call("POST","/rooms",jsonObject("capacity" to capacity,"addBots" to addBots,"bots" to bots,"deckSize" to deckSize,"pass" to pass,"stake" to stake)) {
                game.screen=NetworkTableScreen(game,it.get("room"));dispose()
            }
        }
        status.setText("Ставка резервируется при старте. Сдача — проигрыш ставки, без доплаты за выход.")
    }
    override fun back(){if(joining||editingBots){joining=false;editingBots=false;build()}else menu()}
}

internal class FriendsScreen(game: OpenFoolGame,private val code: String?=null) : OnlineScreen(game) {
    private var page=0
    init {base(if(code==null)"ДРУЗЬЯ" else "ПРИГЛАСИТЬ ДРУГА");load()}
    private fun load() {
        call("GET","/friends") {response ->
            base(if(code==null)"ДРУЗЬЯ" else "ПРИГЛАСИТЬ ДРУГА")
            val entries=ArrayList<Pair<JsonValue,Boolean>>()
            for(p in response.get("requests"))entries.add(p to true)
            for(p in response.get("friends"))entries.add(p to false)
            val pages=((entries.size+4)/5).coerceAtLeast(1)
            page=page.coerceIn(0,pages-1)
            var y=800f
            for((p,request) in entries.drop(page*5).take(5)) {
                val id=p.getString("id")
                if(request) {
                    theme.label(stage,p.getString("nickname")+" — запрос",20f,y,440f,35f,19)
                    theme.button(stage,"ПРИНЯТЬ","green",25f,y-58f,205f,48f,19){respond(id,true)}
                    theme.button(stage,"ОТКЛОНИТЬ","slate",250f,y-58f,205f,48f,18){respond(id,false)}
                } else {
                    theme.iconButton(stage,"avatar_${p.getInt("avatar")}",25f,y-15f,52f){}
                    theme.label(stage,p.getString("nickname"),80f,y-5f,240f,42f,21).setEllipsis(true)
                    if(code!=null)theme.button(stage,"ЗВАТЬ","purple",328f,y-5f,125f,46f,20){
                        call("POST","/invites",jsonObject("code" to code,"playerId" to id)){status.setText("Приглашение отправлено на 2 минуты")}
                    }
                }
                y-=112f
            }
            if(entries.isEmpty())status.setText("Нажмите на живого игрока за столом, чтобы отправить запрос в друзья.")
            theme.button(stage,"<","slate",25f,225f,90f,48f,24){page=(page-1).coerceAtLeast(0);load()}
            theme.label(stage,"${page+1} / $pages",125f,225f,230f,48f,19)
            theme.button(stage,">","slate",365f,225f,90f,48f,24){page=(page+1).coerceAtMost(pages-1);load()}
            theme.button(stage,"ОБНОВИТЬ","slate",25f,46f,430f,60f,22){load()}
        }
    }
    private fun respond(id: String,accept: Boolean){call("POST","/friends/respond",jsonObject("playerId" to id,"accept" to accept)){load()}}
    override fun back() {
        if(code==null)menu() else {
            if(busy)return
            busy=true
            game.online.request("POST","/rooms/state",jsonObject("code" to code),{
                if(alive){game.screen=NetworkTableScreen(game,it.get("room"));dispose()}
            },{if(alive)menu()})
        }
    }
}

internal class InvitationScreen(game: OpenFoolGame,private val invitation: JsonValue) : OnlineScreen(game) {
    init {
        base("ПРИГЛАШЕНИЕ В ИГРУ")
        theme.label(stage,invitation.get("from").getString("nickname")+" приглашает вас в игру",30f,570f,420f,190f,28).setWrap(true)
        theme.button(stage,"ДА, ПРИСОЕДИНИТЬСЯ","green",25f,395f,430f,70f,23){respond(true)}
        theme.button(stage,"НЕТ","slate",25f,290f,430f,65f,23){respond(false)}
    }
    private fun respond(accept: Boolean){call("POST","/invites/respond",jsonObject("inviteId" to invitation.getString("id"),"accept" to accept)) {
        if(accept){game.screen=NetworkTableScreen(game,it.get("room"));dispose()}else menu()
    }}
    override fun back(){
        game.online.request("POST","/invites/respond",jsonObject("inviteId" to invitation.getString("id"),"accept" to false),{},{})
        menu()
    }
}
