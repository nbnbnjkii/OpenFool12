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
            call("GET","/me") {game.online.updateProfile(it.get("user"));menu()}
        }
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
                    password.text="";confirmation?.text="";game.online.remember(it);menu()
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

internal class FriendSetupScreen(game: OpenFoolGame) : OnlineScreen(game) {
    private var capacity=6
    private var bots=0
    private var addBots=false
    private var deckSize=36
    private var pass=false
    init {build()}
    private fun build() {
        base("ИГРА С ДРУЗЬЯМИ")
        theme.label(stage,"Мест за столом: $capacity (до 6 живых)",20f,819f,440f,40f,21)
        for(n in 2..6)theme.button(stage,"$n",if(n==capacity)"green" else "slate",20f+(n-2)*90f,745f,80f,58f,25) {
            capacity=n;bots=bots.coerceAtMost(n-2);if(deckSize<n*6)deckSize=36;build()
        }
        theme.button(stage,if(addBots)"[X] ДОБАВИТЬ БОТОВ" else "[ ] ДОБАВИТЬ БОТОВ","purple",25f,649f,430f,60f,22) {
            addBots=!addBots;if(addBots&&capacity>2)bots=1 else bots=0;build()
        }
        theme.label(stage,if(addBots)"Ботов: $bots • минимум 2 места людям" else "По умолчанию — только живые игроки",20f,596f,440f,38f,19)
        if(addBots)for(n in 0..capacity-2)theme.button(stage,"$n",if(n==bots)"green" else "slate",25f+n*86f,530f,76f,52f,23){bots=n;build()}
        theme.button(stage,"Колода: $deckSize • сменить","slate",25f,448f,430f,55f,21) {
            val decks=listOf(24,36,52).filter{it>=capacity*6};deckSize=decks[(decks.indexOf(deckSize)+1)%decks.size];build()
        }
        theme.button(stage,if(pass)"Переводной" else "Подкидной","slate",25f,375f,430f,55f,22){pass=!pass;build()}
        theme.button(stage,"СОЗДАТЬ СТОЛ","green",25f,285f,430f,65f,25) {
            call("POST","/rooms",jsonObject("capacity" to capacity,"addBots" to addBots,"bots" to bots,"deckSize" to deckSize,"pass" to pass)) {
                game.screen=NetworkTableScreen(game,it.get("room"));dispose()
            }
        }
        val code=field("",207f);code.maxLength=4;code.messageText="Код комнаты: 4 цифры"
        code.textFieldFilter=TextField.TextFieldFilter {_,c->c in '0'..'9'}
        theme.button(stage,"ПРИСОЕДИНИТЬСЯ ПО КОДУ","blue",25f,46f,430f,62f,21) {
            call("POST","/rooms/join",jsonObject("code" to code.text)) {game.screen=NetworkTableScreen(game,it.get("room"));dispose()}
        }
        status.setText("Дружеские сетевые партии без ставок.\nСоздатель запускает игру после готовности всех.")
    }
}

internal class FriendsScreen(game: OpenFoolGame,private val code: String?=null) : OnlineScreen(game) {
    init {base(if(code==null)"ДРУЗЬЯ" else "ПРИГЛАСИТЬ ДРУГА");load()}
    private fun load() {
        call("GET","/friends") {response ->
            base(if(code==null)"ДРУЗЬЯ" else "ПРИГЛАСИТЬ ДРУГА")
            var y=800f
            val requests=response.get("requests")
            for(p in requests){
                if(y<300f)break
                theme.label(stage,p.getString("nickname")+" — запрос",20f,y,440f,35f,19);y-=60f
                val id=p.getString("id")
                theme.button(stage,"ПРИНЯТЬ","green",25f,y,205f,48f,19){respond(id,true)}
                theme.button(stage,"ОТКЛОНИТЬ","slate",250f,y,205f,48f,18){respond(id,false)};y-=65f
            }
            for(p in response.get("friends")) {
                if(y<230f)break
                val id=p.getString("id")
                theme.iconButton(stage,"avatar_${p.getInt("avatar")}",25f,y-15f,52f){}
                theme.label(stage,p.getString("nickname"),80f,y-5f,240f,42f,21)
                if(code!=null)theme.button(stage,"ЗВАТЬ","purple",328f,y-5f,125f,46f,20){
                    call("POST","/invites",jsonObject("code" to code,"playerId" to id)){status.setText("Приглашение отправлено на 2 минуты")}
                }
                y-=75f
            }
            if(response.get("friends").size==0&&requests.size==0)status.setText("Нажмите на живого игрока за столом, чтобы отправить запрос в друзья.")
            theme.button(stage,"ОБНОВИТЬ","slate",25f,46f,430f,60f,22){load()}
        }
    }
    private fun respond(id: String,accept: Boolean){call("POST","/friends/respond",jsonObject("playerId" to id,"accept" to accept)){load()}}
    override fun back() {
        if(code==null)menu() else call("POST","/rooms/state",jsonObject("code" to code)){game.screen=NetworkTableScreen(game,it.get("room"));dispose()}
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
    override fun back(){respond(false)}
}
