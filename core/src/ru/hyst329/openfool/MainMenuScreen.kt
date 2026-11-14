package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport

/** Reference-inspired lobby; unavailable network/social features never simulate live services. */
internal class MainMenuScreen(private val game: OpenFoolGame) : Screen {
    private val stage=Stage(FitViewport(480f,1000f))
    private val theme=NeonTheme()
    private val profile=PlayerProfile(game.preferences)
    private val home=Group()
    private val tabs=ArrayList<Pair<Actor,Label>>()
    private lateinit var sectionTitle: Label
    private lateinit var sectionText: Label
    private lateinit var friendAction: Actor
    private lateinit var status: Label
    private var selectedTab=0
    private var message=""
    private var inviteElapsed=0f
    private var inviteBusy=false
    private var alive=true
    init {
        Gdx.input.inputProcessor=stage
        game.orientationHelper.requestOrientation(OrientationHelper.Orientation.PORTRAIT)
        theme.image(stage,"lobby",0f,0f,480f,1000f)
        theme.iconButton(stage,profile.avatarTexture,20f,895f,78f) {
            game.screen=SettingsScreen(game);dispose()
        }
        theme.image(stage,"ui_chip",47f,888f,27f,24f)
        theme.label(stage,"1",47f,888f,27f,24f,14,Color.valueOf("6cf39a"))
        theme.label(stage,profile.nickname,110f,937f,250f,39f,28).apply {
            setAlignment(Align.left);setEllipsis(true)
        }
        theme.image(stage,"ui_chip",110f,902f,106f,29f)
        theme.image(stage,"coin",116f,907f,20f,20f)
        theme.label(stage,"${game.economy.coins}",139f,903f,54f,27f,16,Color.valueOf("ffdc68"))
        theme.iconButton(stage,"ui_plus",196f,909f,15f) { selectTab(1) }
        theme.image(stage,"ui_chip",225f,902f,111f,29f)
        theme.image(stage,"crystal",231f,907f,20f,20f)
        theme.label(stage,"${game.economy.crystals}",254f,903f,48f,27f,16,Color.valueOf("7cdbff"))
        theme.iconButton(stage,"ui_plus",315f,909f,15f) { selectTab(1) }
        theme.iconButton(stage,"ui_gear",423f,929f,35f) {
            game.screen=SettingsScreen(game);dispose()
        }
        val first=stage.actors.size
        theme.image(stage,"banner",20f,690f,440f,186f)
        theme.label(stage,"• Игра с ботами",20f,646f,170f,29f,15,Color.valueOf("8f9bb6")).setAlignment(Align.left)
        theme.label(stage,"•  •  •",201f,648f,73f,25f,18,Color.valueOf("7f8ba5"))
        theme.label(stage,"Уведомления",310f,646f,120f,29f,14,Color.valueOf("8f9bb6"))
        theme.iconButton(stage,"ui_bell",436f,649f,23f) { message="Новых уведомлений нет" }
        status=theme.label(stage,"",20f,612f,440f,24f,14,Color.valueOf("c5d4eb"))
        theme.menuButton(stage,"ИГРАТЬ ОНЛАЙН","green","ui_planet",20f,501f,440f,98f,27) { setup(MatchMode.ONLINE) }
        theme.menuButton(stage,"ИГРАТЬ С ДРУЗЬЯМИ","purple","ui_friends",20f,386f,440f,92f,23) { setup(MatchMode.FRIENDS) }
        theme.menuButton(stage,"ПРОТИВ БОТА","bot_button","ui_robot",20f,271f,440f,92f,27) { setup(MatchMode.BOTS) }
        theme.menuButton(stage,"ТУРНИРЫ","slate","ui_trophy",20f,156f,440f,92f,28) { message="Турниры пока недоступны" }
        theme.image(stage,"ui_badge",92f,232f,81f,26f)
        theme.label(stage,"СКОРО",92f,232f,81f,26f,14)
        // Move only the home content into a switchable group; profile and navigation remain fixed.
        val actors=(first until stage.actors.size).map { stage.actors[it] }
        actors.forEach { home.addActor(it) };stage.addActor(home)
        sectionTitle=theme.label(stage,"",25f,699f,430f,70f,32)
        sectionText=theme.label(stage,"",35f,430f,410f,220f,22,Color.valueOf("aab8d2"))
        sectionText.setWrap(true)
        friendAction=theme.button(stage,"СПИСОК ДРУЗЕЙ","purple",25f,300f,430f,82f,25) { game.screen=FriendsScreen(game);dispose() }
        theme.image(stage,"hud_panel",0f,0f,480f,100f)
        val names=listOf("Главная","Магазин","Друзья","Задания","Лидеры")
        val icons=listOf("ui_home","ui_shop","ui_friends","ui_tasks","ui_trophy")
        for (i in names.indices) {
            val icon=theme.iconButton(stage,icons[i],i*96f+29f,48f,38f) { selectTab(i) }
            val label=theme.label(stage,names[i],i*96f,16f,96f,29f,15)
            label.addListener(object : com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                override fun clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent?,x: Float,y: Float) { selectTab(i) }
            })
            tabs.add(icon to label)
        }
        selectTab(0)
    }
    private fun setup(mode: MatchMode) { game.screen=if(mode==MatchMode.FRIENDS) FriendSetupScreen(game) else BotSetupScreen(game,mode);dispose() }
    private fun selectTab(index: Int) {
        selectedTab=index
        home.isVisible=index==0
        sectionTitle.isVisible=index!=0; sectionText.isVisible=index!=0
        friendAction.isVisible=index==2
        val titles=listOf("","МАГАЗИН","ДРУЗЬЯ","ЗАДАНИЯ","ЛИДЕРЫ")
        val descriptions=listOf("", "${game.economy.coins} монет • ${game.economy.crystals} кристаллов\n\nПокупки и пополнение пока недоступны.",
            "Создайте стол или введите код комнаты. До 6 живых игроков. Боты — только по вашему выбору.\n\nЗапросы в друзья принимаются в списке ниже.",
            "Задания и награды появятся в следующем обновлении.",
            "Таблица лидеров пока недоступна. Онлайн-рейтинг не подключён.")
        sectionTitle.setText(titles[index]);sectionText.setText(descriptions[index])
        tabs.forEachIndexed { i,(icon,label) ->
            val c=if (i==index) Color.valueOf("4ee68a") else Color.valueOf("919bb9")
            icon.color=c;label.color=c
        }
    }
    override fun render(delta: Float) {
        inviteElapsed+=delta
        if(inviteElapsed>5f&&!inviteBusy&&game.online.token.isNotEmpty()) {
            inviteElapsed=0f;inviteBusy=true
            game.online.request("GET","/invites",null,{result ->
                inviteBusy=false
                if(alive&&game.screen===this&&result.get("invites").size>0){
                    game.screen=InvitationScreen(game,result.get("invites").get(0));dispose()
                }
            },{inviteBusy=false})
        }
        game.assetManager.update(3)
        status.setText(message)
        Gdx.gl.glClearColor(0.07f,0.08f,0.17f,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta);stage.draw()
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (selectedTab!=0) selectTab(0) else Gdx.app.exit()
        }
    }
    override fun resize(width: Int,height: Int) { stage.viewport.update(width,height,true) }
    override fun dispose() { alive=false;stage.dispose();theme.dispose() }
    override fun show() {}
    override fun hide() {}
    override fun pause() {}
    override fun resume() {}
}
