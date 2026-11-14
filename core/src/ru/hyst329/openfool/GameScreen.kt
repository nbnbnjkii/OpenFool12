package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Screen
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Sprite
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.utils.viewport.FitViewport
import mu.KotlinLogging

import java.security.SecureRandom
import java.util.ArrayList
import java.util.HashMap

import ru.hyst329.openfool.GameScreen.GameState.BEATEN
import ru.hyst329.openfool.GameScreen.GameState.BEATING
import ru.hyst329.openfool.GameScreen.GameState.DRAWING
import ru.hyst329.openfool.GameScreen.GameState.FINISHED
import ru.hyst329.openfool.GameScreen.GameState.READY
import ru.hyst329.openfool.GameScreen.GameState.THROWING
import ru.hyst329.openfool.GameScreen.GameState.THROWN
import ru.hyst329.openfool.ResultScreen.Result.TEAM_DRAW
import ru.hyst329.openfool.ResultScreen.Result.TEAM_LOST
import ru.hyst329.openfool.ResultScreen.Result.TEAM_PARTNER_LOST
import ru.hyst329.openfool.ResultScreen.Result.TEAM_WON
import ru.hyst329.openfool.ResultScreen.Result.DRAW
import ru.hyst329.openfool.ResultScreen.Result.LOST
import ru.hyst329.openfool.ResultScreen.Result.WON
import kotlin.math.min


/**
 * Created by main on 13.03.2017.
 * Licensed under MIT License.
 */

private val logger = KotlinLogging.logger {}

class GameScreen(private val game: OpenFoolGame, private val stake: Int = 0) : Screen, EventListener {
    private val assetsReady = game.ensureGameAssets()
    private val stakeReserved = game.economy.reserveStake(stake,RuleSet(game.preferences).playerCount)
        .also { require(it) { "Stake exceeds wallet balance or another wager is active" } }
    private var wagerReleased = false
    private var confirmSurrender = false
    private var warnAfterResume = false
    private var initialDealStarted = false
    private var readyButton: com.badlogic.gdx.scenes.scene2d.Actor? = null
    private var discardButton: com.badlogic.gdx.scenes.scene2d.Actor? = null
    private var hintButton: com.badlogic.gdx.scenes.scene2d.Actor? = null
    private val neon = NeonTheme()
    private val perks = GamePerks(game.economy)
    private val clock = DecisionTimer()
    private var decisionSerial = 0
    private var discardModal = false
    private var message = ""
    private var messageSeconds = 0f
    private var skipClockFrame = false
    private val warningSound = try { Gdx.audio.newSound(Gdx.files.internal("audio/turn_warning.wav")) }
        catch (error: Exception) { Gdx.app.error("TurnTimer","Warning sound unavailable",error); null }
    private var undoButton: com.badlogic.gdx.scenes.scene2d.Actor? = null
    private var undoWindow = 0f
    private var lastUndo: UndoState? = null
    private data class UndoState(val card: Card, val hand: List<Card>, val attacks: Array<Card?>,
        val defenses: Array<Card?>, val attacker: Int, val thrower: Int, val saidDone: Int,
        val done: BooleanArray, val taking: Boolean, val state: GameState, val limit: Int)

    private val dragLayer = Group()
    private var takeButton: com.badlogic.gdx.scenes.scene2d.Actor? = null
    private var doneButton: com.badlogic.gdx.scenes.scene2d.Actor? = null
    private var giveButton: com.badlogic.gdx.scenes.scene2d.Actor? = null
    private var draggedCard: CardActor? = null
    private var dragPointer = -1
    private var dragStarted = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private val background: Texture
    private val backgroundColor: Color
    private val suitSymbols: Map<Suit, Sprite> = mapOf(
            Suit.SPADES to Sprite(game.assetManager.get("suits/spades.png", Texture::class.java)),
            Suit.DIAMONDS to Sprite(game.assetManager.get("suits/diamonds.png", Texture::class.java)),
            Suit.CLUBS to Sprite(game.assetManager.get("suits/clubs.png", Texture::class.java)),
            Suit.HEARTS to Sprite(game.assetManager.get("suits/hearts.png", Texture::class.java))
    )

    internal enum class GameState {
        READY,
        DRAWING,
        THROWING,
        THROWN,
        BEATING,
        BEATEN,
        FINISHED
    }

    private inner class GameStateChangedAction internal constructor(private val newState: GameState) : Action() {

        override fun act(delta: Float): Boolean {
            gameState = newState
            return true
        }
    }

    private inner class SortAction internal constructor(private val playerIndex: Int) : Action() {

        override fun act(delta: Float): Boolean {
            if (playerIndex == 0)
                sortPlayerCards()
            return true
        }
    }

    private val stage: Stage
    private val discardPileGroup: Group
    private val tableGroup: Group
    private val playerGroups: Array<Group>
    internal lateinit var trumpSuit: Suit
    internal val players: Array<Player>
    internal var attackCards = arrayOfNulls<Card>(DEAL_LIMIT)
        private set
    internal var defenseCards = arrayOfNulls<Card>(DEAL_LIMIT)
        private set
    private val cardActors = HashMap<Card, CardActor>()
    internal var ruleSet = RuleSet(game.preferences)
        private set
    private val flow = TableFlow(ruleSet.playerCount)
    private val deck = Deck(ruleSet.lowestRank)
    private val screenOrientationIsPortrait: Boolean = game.preferences.getBoolean(SettingsScreen.ORIENTATION, true)
    private val cardScalePlayer = if (screenOrientationIsPortrait) CARD_SCALE_PLAYER_PORTRAIT else CARD_SCALE_PLAYER_LANDSCAPE
    private val cardScaleTable = if (screenOrientationIsPortrait) CARD_SCALE_TABLE_PORTRAIT else CARD_SCALE_TABLE_LANDSCAPE
    private val cardScaleAI = if (screenOrientationIsPortrait) CARD_SCALE_AI_PORTRAIT else CARD_SCALE_AI_LANDSCAPE
    private val deckPosition = if (screenOrientationIsPortrait) DECK_POSITION_PORTRAIT else DECK_POSITION_LANDSCAPE
    private val playerPosition = if (screenOrientationIsPortrait) PLAYER_POSITION_PORTRAIT else PLAYER_POSITION_LANDSCAPE
    private val aiPosition = if (screenOrientationIsPortrait) AI_POSITION_PORTRAIT else AI_POSITION_LANDSCAPE
    private val tablePosition = if (screenOrientationIsPortrait) TABLE_POSITION_PORTRAIT else TABLE_POSITION_LANDSCAPE
    private val discardPilePosition = if (screenOrientationIsPortrait) DISCARD_PILE_POSITION_PORTRAIT else DISCARD_PILE_POSITION_LANDSCAPE
    private val playerDelta = if (screenOrientationIsPortrait) PLAYER_DELTA_PORTRAIT else PLAYER_DELTA_LANDSCAPE
    private val aiDelta = if (screenOrientationIsPortrait) AI_DELTA_PORTRAIT else AI_DELTA_LANDSCAPE
    private val tableDelta = if (screenOrientationIsPortrait) TABLE_DELTA_PORTRAIT else TABLE_DELTA_LANDSCAPE
    private val nextLineDelta = if (screenOrientationIsPortrait) NEXT_LINE_DELTA_PORTRAIT else NEXT_LINE_DELTA_LANDSCAPE
    private val offset = if (screenOrientationIsPortrait) 384 else 640
    private var currentAttackerIndex: Int = 0
    private var currentThrowerIndex: Int = 0
    private var playersSaidDone: Int = 0
    private var isPlayerTaking: Boolean = false
    private val random = SecureRandom()
    private val outOfPlay: BooleanArray
    private val discardPile = ArrayList<Card>()
    private var gameState = DRAWING
    private var oldGameState = FINISHED
    private val sortingMode: Player.SortingMode
    private var throwLimit = DEAL_LIMIT
    private var playerDoneStatuses: BooleanArray
    internal var places = IntArray(DEAL_LIMIT) { 0 }

    init {
        // Create the stage depending on screen orientation
        if (screenOrientationIsPortrait) {
            game.orientationHelper.requestOrientation(OrientationHelper.Orientation.PORTRAIT)
        } else {
            game.orientationHelper.requestOrientation(OrientationHelper.Orientation.LANDSCAPE)
        }
        stage = Stage(if (screenOrientationIsPortrait) {
            FitViewport(480f, 1000f)
        } else {
            FitViewport(800f, 480f)
        })
        // Initialise the stage
        Gdx.input.inputProcessor = stage
        // Get background color
        backgroundColor = Color(game.preferences.getInteger(SettingsScreen.BACKGROUND_COLOR, 0x33cc4dff))
        val backgroundNo = game.preferences.getInteger(SettingsScreen.BACKGROUND, 1)
        background = game.assetManager.get("backgrounds/background$backgroundNo.png", Texture::class.java)
        background.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        // Initialise suit symbols
        for ((suit, sprite) in suitSymbols) {
            sprite.setCenter(Gdx.graphics.width / 20f, Gdx.graphics.height / 3.2f)
            sprite.setScale(0.5f)
        }
        // Initialise player info
        playerDoneStatuses = BooleanArray(ruleSet.playerCount)
        outOfPlay = BooleanArray(ruleSet.playerCount)
        val deckStyle = game.preferences.getString(SettingsScreen.DECK, "rus")
        sortingMode = Player.SortingMode.fromInt(game.preferences.getInteger(SettingsScreen.SORTING_MODE, 0))
        // Initialise groups
        tableGroup = Group()
        stage.addActor(tableGroup)
        val deckGroup = Group()
        stage.addActor(deckGroup)
        discardPileGroup = Group()
        stage.addActor(discardPileGroup)
        playerGroups = Array(ruleSet.playerCount) { Group() }
        for (i in 0 until ruleSet.playerCount) {
            playerGroups[i] = Group()
            stage.addActor(playerGroups[i])
        }
        // Initialise players
        // TODO: Replace with settings
        val playerNames = Array(ruleSet.playerCount) { if (it == 0) "Игрок_1" else "Бот_$it" }
        players = Array(ruleSet.playerCount) { i -> Player(ruleSet, playerNames[i], i) }
        for (i in 0 until ruleSet.playerCount) {
            players[i].addListener(this)
            stage.addActor(players[i])
        }
        // Initialise card actors
        val cards = deck.cards
        val listener = object : InputListener() {
            override fun touchDown(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                val actor = event?.listenerActor as? CardActor ?: return false
                if (draggedCard != null || button != 0 || !canDrag(actor)) return false
                draggedCard = actor
                dragPointer = pointer
                dragStarted = false
                dragStartX = event.stageX
                dragStartY = event.stageY
                return true
            }
            override fun touchDragged(event: InputEvent?, x: Float, y: Float, pointer: Int) {
                val actor = draggedCard ?: return
                if (pointer != dragPointer || event == null) return
                if (!dragStarted) {
                    if (com.badlogic.gdx.math.Vector2.dst(dragStartX,dragStartY,event.stageX,event.stageY) < 12f) return
                    dragStarted = true
                    actor.clearActions()
                    dragLayer.toFront()
                    dragLayer.addActor(actor)
                    actor.rotation = 0f
                }
                actor.setPosition(event.stageX - actor.width*actor.scaleX/2,
                    event.stageY - actor.height*actor.scaleY/2)
            }
            override fun touchUp(event: InputEvent?, x: Float, y: Float, pointer: Int, button: Int) {
                if (pointer != dragPointer) return
                val actor = draggedCard ?: return
                val moved = dragStarted
                draggedCard = null
                dragPointer = -1
                // Tap, canceled focus, wrong turn, wrong target, and off-table release all restore the fan.
                if (event != null && !event.isTouchFocusCancel && moved && canDrag(actor)) {
                    playDroppedCard(actor,event.stageX,event.stageY)
                }
                if (players[0].hand.contains(actor.card)) {
                    playerGroups[0].addActor(actor)
                    layoutHumanHand()
                }
            }
        }
        for (i in cards!!.indices) {
            val c = cards[i]
            val cardActor = CardActor(game, c, deckStyle)
            cardActors.put(c, cardActor)
            cardActor.addListener(this)
            cardActor.addListener(listener)
            cardActor.touchable = Touchable.enabled
            deckGroup.addActor(cardActor)
            cardActor.zIndex = i
        }
        // Starting the game
        for (cardActor in cardActors.values) {
            cardActor.isFaceUp = false
            cardActor.setScale(cardScaleTable)
            cardActor.setPosition(deckPosition[0], deckPosition[1])
            // cardActor.setDebug(true);
        }
        // Determine trump
        val trumpCard = deck.cards?.get(0)
        val trump = cardActors[trumpCard]
        trump?.rotation = -90.0f
        trump?.isFaceUp = true
        trump?.moveBy(90 * cardScaleTable, 0f)
        trumpSuit = trumpCard!!.suit
        logger.debug("Trump suit is $trumpSuit")
        stage.addActor(dragLayer)
        if (screenOrientationIsPortrait) {
            // Opaque panel masks the lower half of the fan and blocks touches on hidden cards.
            neon.image(stage,"hud_panel",0f,0f,480f,HandFan.PANEL_TOP).touchable = Touchable.enabled
            readyButton = neon.button(stage,"ГОТОВ","green",12f,28f,177f,62f,26) {
                if (flow.surrendered) {
                    if (stake > game.economy.coins) {
                        toast("Недостаточно монет для новой ставки")
                    } else {
                        dispose()
                        val next=GameScreen(game,stake)
                        game.screen=next
                        next.flow.humanReady()
                        next.updateActionButtons()
                    }
                } else { flow.humanReady(); updateActionButtons() }
            }
            doneButton = neon.button(stage,"БИТО","green",12f,28f,177f,62f,26) {
                if (humanActions().done) {
                    currentThrower.sayDone()
                }
                updateActionButtons()
            }
            takeButton = neon.button(stage,"БЕРУ","gold",12f,28f,177f,62f,26) {
                if (humanActions().take) currentDefender.sayTake()
                updateActionButtons()
            }
            // When a bot takes, the human may still throw in, then explicitly hand over the pile.
            giveButton = neon.button(stage,"ОТДАТЬ","green",12f,28f,177f,62f,24) {
                if (humanActions().give) {
                    currentThrower.sayDone()
                }
                updateActionButtons()
            }
            undoButton = neon.iconButton(stage,"perk_undo",309f,40f,44f) { undoLastCard() }
            discardButton = neon.iconButton(stage,"perk_discard",367f,40f,44f) {
                if (perks.unlockDiscard()) openDiscard() else toast("Недостаточно кристаллов: просмотр стоит 3")
            }
            hintButton = neon.iconButton(stage,"perk_hint",425f,40f,44f) {
                val already = perks.highlightEnabled
                if (perks.unlockHighlight()) toast(if (already) "Подсветка уже активна на эту партию" else "Подсветка на партию включена: -1 кристалл")
                else toast("Недостаточно кристаллов: подсветка стоит 1")
            }
            updateActionButtons()
            neon.iconButton(stage,"white_flag",22f,937f,38f) { askSurrender() }
        }
    }

    override fun show() {

    }

    override fun render(delta: Float) {
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.BACK) ||
            Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            onPlatformBack()
            return
        }
        if (warnAfterResume) { warnAfterResume=false;askSurrender() }
        val step = if (skipClockFrame || confirmSurrender) 0f else delta.coerceAtLeast(0f)
        skipClockFrame = false
        if (!confirmSurrender) {
            flow.tick(step)
            if (flow.phase==TableFlow.Phase.DEALING && !initialDealStarted) {
                initialDealStarted=true
                startInitialDeal()
            }
            if (flow.phase==TableFlow.Phase.DEALING && initialDealStarted &&
                cardActors.values.all { it.actions.size==0 }) {
                flow.dealt();gameState=READY;sortPlayerCards();decisionSerial++
                toast(if (flow.surrendered) "Наблюдение за партией" else "Играем!")
            }
        }
        messageSeconds = (messageSeconds-step).coerceAtLeast(0f)
        undoWindow = (undoWindow-step).coerceAtLeast(0f)
        if (undoWindow == 0f) lastUndo = null
        val tick = clock.tick(decisionOwner(),decisionSerial,step)
        if (tick.warning) warningSound?.play(0.8f)
        if (tick.timeout) onDecisionTimeout()
        Gdx.gl.glClearColor(backgroundColor.r, backgroundColor.g, backgroundColor.b, backgroundColor.a)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        val eligibleThrowers = SeatOrder.throwers(currentAttacker.index,currentDefender.index,outOfPlay,ruleSet.teamPlay)
        if (flow.playing && eligibleThrowers.isNotEmpty() && eligibleThrowers.all { playerDoneStatuses[it] }
                && gameState != DRAWING && gameState != BEATING && gameState != THROWING) {
            gameState = FINISHED
        }
        if (oldGameState != gameState) {
            logger.debug("Game state is $gameState")
            oldGameState = gameState
        }
        // Three seconds after a human card are reserved for paid undo. No bot can reveal a reply meanwhile.
        if (flow.playing && !confirmSurrender && undoWindow <= 0f) when (gameState) {
            READY -> {
                throwLimit = min(if (ruleSet.loweredFirstDiscardLimit && discardPile.isEmpty()) 5 else DEAL_LIMIT,
                    currentDefender.hand.size)
                if (currentAttacker.index != 0 || flow.surrendered)
                    currentAttacker.startTurn(trumpSuit, cardsRemaining(), players.map { it.hand.size }.toTypedArray())
            }
            DRAWING -> {
            }
            THROWING -> {
            }
            THROWN -> {
                if (currentDefender.index != 0 || flow.surrendered) {
                    if (!isPlayerTaking) {
                        currentDefender.tryBeat(attackCards, defenseCards, trumpSuit, cardsRemaining(),
                            players.map { it.hand.size }.toTypedArray(), nextDefender.hand.size)
                    } else {
                        currentDefender.sayTake()
                    }
                }
                if (isPlayerTaking)
                    gameState = BEATEN
            }
            BEATING -> {
            }
            BEATEN -> {
                val limitReached = currentDefender.hand.size == 0 ||
                    attackCards.count { it != null } >= throwLimit
                val waitingForHuman = !flow.surrendered && currentThrower.index == 0 && currentDefender.index != 0 && !playerDoneStatuses[0]
                if (limitReached && !waitingForHuman) {
                    gameState = FINISHED
                } else if (currentThrower.index != 0 || flow.surrendered) {
                    if (limitReached) {
                        currentThrower.sayDone()
                    } else {
                        currentThrower.throwOrDone(attackCards, defenseCards, trumpSuit, cardsRemaining(), players.map { it.hand.size }.toTypedArray())
                    }
                }
            }
            FINISHED -> {
                val playerTook = isPlayerTaking
                val currentDefenderIndex = currentDefender.index
                endTurn(if (isPlayerTaking) currentDefender.index else -1)
                if (isGameOver) {
                    logger.debug("GAME OVER")
                } else {
                    currentAttackerIndex = if (playerTook || outOfPlay[currentDefenderIndex])
                        SeatOrder.next(currentDefenderIndex,outOfPlay) else currentDefenderIndex
                    currentThrowerIndex = currentAttackerIndex
                    logger.debug("${currentAttacker.name} (${currentAttacker.index}) -> ${currentDefender.name} (${currentDefender.index})")
                }
            }
        }
        // Draw background
        game.batch.transformMatrix = stage.viewport.camera.view
        game.batch.projectionMatrix = stage.viewport.camera.projection
        game.batch.begin()
        game.batch.color = if (screenOrientationIsPortrait) Color.WHITE else backgroundColor
        if (screenOrientationIsPortrait) game.batch.draw(neon.texture("table"),0f,0f,480f,1000f)
        else game.batch.draw(background, 0f, 0f, 0, 0, Gdx.graphics.width, Gdx.graphics.height)
        game.batch.color = Color(Color.WHITE)
        if (flow.phase==TableFlow.Phase.DEALING) {
            val style=game.preferences.getString(SettingsScreen.DECK,"rus")
            game.batch.draw(game.assetManager.get("decks/$style/back.png",Texture::class.java),
                deckPosition[0],deckPosition[1],360f*cardScaleTable,540f*cardScaleTable)
        }
        game.batch.end()
        // Draw stage
        if (!confirmSurrender) stage.act(delta)
        layoutHumanHand()
        updateHandHints()
        updateActionButtons()
        undoButton?.color = if (lastUndo != null && undoWindow > 0f) Color.WHITE else Color(0.45f,0.45f,0.55f,1f)
        stage.draw()
        // Draw player labels
        game.batch.transformMatrix = stage.viewport.camera.view
        game.batch.projectionMatrix = stage.viewport.camera.projection
        game.batch.begin()
        if (screenOrientationIsPortrait) {
            for (i in players.indices) {
                val seat = SeatOrder.portraitSeat(i,players.size)
                val x = seat[0]
                val y = seat[1]
                val size = if (i == 0) 66f else 64f
                game.batch.draw(neon.texture(if (i == 0) "avatar" else "bot"),x,y,size,size)
                if (decisionOwner() == i) neon.timerRing(game.batch,x+size/2,y+size/2,size/2+7f,clock.remaining)
                val f = neon.font(if (i == 0) 14 else 17)
                f.color = if (currentDefender.index == i || currentThrower.index == i) Color.valueOf("6bffce") else Color.WHITE
                f.draw(game.batch,players[i].name,x-12f,if (i == 0) 18f else y-3f,90f,com.badlogic.gdx.utils.Align.center,false)
                if (i > 0) {
                    val status=when {
                        flow.phase==TableFlow.Phase.WAITING || flow.phase==TableFlow.Phase.ANNOUNCING -> if (flow.ready[i]) "Готов" else "Ожидание"
                        flow.surrendered -> "Ожидание"
                        else -> "${players[i].hand.size} карт"
                    }
                    f.draw(game.batch,status,x-12f,y-25f,90f,com.badlogic.gdx.utils.Align.center,false)
                    if (isPlayerTaking && currentDefender.index==i) {
                        f.color=Color.valueOf("ffce73")
                        f.draw(game.batch,"Беру",if (x>300f) x-100f else x+76f,y+37f,95f,com.badlogic.gdx.utils.Align.center,false)
                    }
                }
            }
            drawGameHud()
            if (messageSeconds > 0f) neon.font(14).draw(game.batch,message,15f,276f,450f,com.badlogic.gdx.utils.Align.center,true)
            else if (undoWindow > 0f) neon.font(14).draw(game.batch,"Возврат карты: 2 кристалла (${kotlin.math.ceil(undoWindow).toInt()} с)",15f,276f,450f,com.badlogic.gdx.utils.Align.center,false)

            if (flow.phase==TableFlow.Phase.ANNOUNCING || flow.phase==TableFlow.Phase.DEALING) {
                neon.font(26).color=Color.WHITE
                neon.font(26).draw(game.batch,if (flow.phase==TableFlow.Phase.ANNOUNCING) "Все готовы!" else "Раздача карт...",0f,480f,480f,com.badlogic.gdx.utils.Align.center,false)
            }
        } else {
        for (i in 0 until ruleSet.playerCount) {
            val position = (if (i == 0) playerPosition else aiPosition).clone()
            if (i > 0 && ruleSet.playerCount > 2)
                position[0] += ((i - 1) * offset / (ruleSet.playerCount - 2)).toFloat()
            if (screenOrientationIsPortrait) {
                if (i > 0) {
                    position[1] -= 320 * cardScaleAI
                } else {
                    position[1] += 760 * cardScalePlayer
                }
            } else {
                position[1] += 640 * if (i == 0) cardScalePlayer else cardScaleAI
            }
            var playerFormat = "${players[i].name} (${players[i].hand.size})"
            if (playerDoneStatuses[i]) playerFormat += "\n" + game.localeBundle["PlayerDone"]
            if (isPlayerTaking && currentDefender.index == i)
                playerFormat += "\n" + game.localeBundle["PlayerTakes"]
            game.font.draw(game.batch, playerFormat,
                    position[0], position[1])

        }

        game.font.draw(game.batch, "${cardsRemaining()}", stage.viewport.worldWidth / 10f, stage.viewport.worldHeight / 3f)
        var turnString = "${currentAttacker.name} -> ${currentDefender.name}"
        val newLineOrSpaces = if (screenOrientationIsPortrait) "   " else "\n"
        if (currentAttacker.index == 0)
            turnString += newLineOrSpaces + game.localeBundle["YourTurn"]
        if (currentDefender.index == 0)
            turnString += newLineOrSpaces + game.localeBundle["Defend"]
        suitSymbols[trumpSuit]?.draw(game.batch)
        if (screenOrientationIsPortrait) {
            game.font.draw(game.batch, turnString, stage.viewport.worldWidth / 5f, stage.viewport.worldHeight / 3.2f)
        } else {
            game.font.draw(game.batch, turnString, stage.viewport.worldWidth / 40f, stage.viewport.worldHeight / 4.8f)
        }
        }
        if (discardModal) drawDiscardModal()
        if (confirmSurrender) drawSurrenderDialog()
        game.batch.end()
        // Check if the game is over
        if (!flow.surrendered && isGameOver) {
            var gameResult: ResultScreen.Result = if (outOfPlay[0]) WON else LOST
            if (outOfPlay.all { it })
                gameResult = DRAW
            if (ruleSet.teamPlay) gameResult = if (outOfPlay[0]) TEAM_WON else TEAM_LOST
            if (ruleSet.teamPlay && (1 until players.size step 2).all { outOfPlay[it] } && gameResult == TEAM_WON)
                gameResult = if ((0 until players.size step 2).all { outOfPlay[it] }) TEAM_DRAW else TEAM_PARTNER_LOST
            var position = players.size
            val playersPlaces: Map<Int, String> = players.map { (if (places[it.index] == 0) position-- else places[it.index]) to it.name }.toMap()

            if (flow.surrendered) gameResult=LOST
            game.economy.settleStake(when (gameResult) {
                WON, TEAM_WON -> 1
                DRAW, TEAM_DRAW -> 0
                else -> -1
            })
            wagerReleased = true
            game.screen = ResultScreen(game, gameResult, playersPlaces)
            dispose()
        }
    }

    override fun resize(width: Int, height: Int) {
        stage.viewport.update(width, height, true)
        stage.viewport.camera.update()
    }

    override fun pause() {
        skipClockFrame = true
        if (flow.needsExitWarning && !wagerReleased) warnAfterResume=true
        stage.cancelTouchFocus()
    }

    override fun resume() {
        skipClockFrame = true
    }

    override fun hide() {

    }

    override fun dispose() {
        releaseWager()
        warningSound?.dispose()
        stage.dispose()
        neon.dispose()
    }

    private fun endTurn(playerIndex: Int) {
        lastUndo = null; undoWindow = 0f; decisionSerial++
        playersSaidDone = 0
        playerDoneStatuses = BooleanArray(ruleSet.playerCount)
        val tableCards = ArrayList<Card>()
        for (i in attackCards.indices) {
            if (attackCards[i] != null) {
                tableCards.add(attackCards[i] as Card)
                //attackCards[i] = null;
            }
            if (defenseCards[i] != null) {
                tableCards.add(defenseCards[i] as Card)
                //defenseCards[i] = null;
            }
        }
        attackCards = arrayOfNulls(DEAL_LIMIT)
        defenseCards = arrayOfNulls(DEAL_LIMIT)
        if (playerIndex < 0) {
            for (card in tableCards) {
                val cardActor = cardActors[card]
                discardPile.add(card)
                discardPileGroup.addActor(cardActor)
                cardActor?.isFaceUp = false
                cardActor?.zIndex = discardPile.size - 1
                cardActor?.rotation = random.nextFloat() * 20 - 10
                val dx = random.nextFloat() * 20 - 10
                val dy = random.nextFloat() * 20 - 10
                cardActor?.addAction(
                        Actions.moveTo(discardPilePosition[0] + dx, discardPilePosition[1] + dy, 0.6f))
            }
        } else {
            val player = players[playerIndex]
            for (card in tableCards) {
                player.addCard(card)
                val cardActor = cardActors[card]
                cardActor?.isFaceUp = (playerIndex == 0)
                val position = handPosition(playerIndex)
                val delta = handDelta(playerIndex)
                val index = player.hand.size - 1
                val posX = position[0] + index * delta[0]
                val posY = position[1] + index * delta[1]
                cardActor?.addAction(Actions.moveTo(posX,posY,0.4f))
                cardActor?.rotation = 0.0f
                cardActor?.setScale(if (playerIndex == 0) cardScalePlayer else cardScaleAI)
                playerGroups[playerIndex].addActor(cardActor)
                cardActor?.zIndex = index
            }
            player.addAction(Actions.sequence(
                    Actions.delay(0.39f),
                    SortAction(playerIndex)
            ))
        }
        if (deck.cards?.isEmpty() == false) {
            val refill = (0 until players.size).map { (currentAttacker.index+it)%players.size }
                .filter { it != currentDefender.index } + currentDefender.index
            for (i in refill) {
                val cardsToDraw = DEAL_LIMIT - players[i].hand.size
                if (cardsToDraw > 0) {
                    drawCardsToPlayer(i, cardsToDraw)
                }
                if (deck.cards?.isEmpty() != false)
                    break
            }
        }
        // Check if someone is out of play
        if (deck.cards?.isEmpty() != false) {
            for (i in 0 until ruleSet.playerCount) {
                val oldOutOfPlay = outOfPlay[i]
                outOfPlay[i] = players[i].hand.size == 0
                if (outOfPlay[i] && !oldOutOfPlay) {
                    places[i] = outOfPlay.count { it }
                }
            }
        }
        isPlayerTaking = false
        gameState = READY
    }

    private // TODO: Generalise
    val isGameOver: Boolean
        get() {
            if (ruleSet.teamPlay)
                return (0 until players.size step 2).all { outOfPlay[it] } ||
                    (1 until players.size step 2).all { outOfPlay[it] }
            else {
                // Simply check if only one player remains
                return (outOfPlay.map { if (it) 0 else 1 }.fold(initial = 0) { total, current -> total + current }) <= 1
            }
        }

    val areAllCardsBeaten: Boolean
        get() {
            return (0 until DEAL_LIMIT).map { (attackCards[it] != null) == (defenseCards[it] != null) }
                    .fold(initial = true) { total, current -> total && current }
        }

    internal fun cardsRemaining(): Int {
        return deck.cards?.size ?: 0
    }

    override fun handle(event: Event): Boolean {
        if (event is Player.CardThrownEvent || event is Player.CardBeatenEvent ||
            event is Player.CardPassedEvent || event is Player.TakeEvent || event is Player.DoneEvent) {
            decisionSerial++
            val source = event.target as? Player
            if (source?.index != 0 || event is Player.TakeEvent || event is Player.DoneEvent) {
                lastUndo = null; undoWindow = 0f
            }
        }
        if (event is Player.CardThrownEvent) {
            gameState = THROWING
            // Handle when card is thrown
            playersSaidDone = 0
            playerDoneStatuses = BooleanArray(ruleSet.playerCount)
            var throwIndex = 0
            while (attackCards[throwIndex] != null) throwIndex++
            val throwCard = event.card
            attackCards[throwIndex] = throwCard
            val throwCardActor = cardActors[throwCard]
            throwCardActor?.isFaceUp = true
            throwCardActor?.rotation = 0f
            tableGroup.addActor(throwCardActor)
            throwCardActor?.zIndex = 2 * throwIndex
            throwCardActor?.setScale(cardScaleTable)
            val throwPos = tablePosition.clone()
            throwPos[0] += (90 * throwIndex).toFloat()
            if (throwIndex >= 3) {
                throwPos[0] += nextLineDelta[0]
                throwPos[1] += nextLineDelta[1]
            }
            throwCardActor?.addAction(Actions.sequence(
                    GameStateChangedAction(GameState.THROWING),
                    Actions.moveTo(throwPos[0], throwPos[1], 0.4f),
                    Actions.delay(0.2f),
                    GameStateChangedAction(GameState.THROWN)))
            val thrower = event.getTarget() as Player
            logger.debug("${thrower.name} (${thrower.index}) throws $throwCard")
            for (i in 0 until thrower.hand.size) {
                val cardActor = cardActors[thrower.hand[i]]
                val position = handPosition(thrower.index)
                val delta = handDelta(thrower.index)
                val posX = position[0] + i * delta[0]
                val posY = position[1] + i * delta[1]
                //cardActor.addAction(Actions.moveTo(posX, posY, 0.1f));
                cardActor?.setPosition(posX, posY)
                cardActor?.rotation = 0.0f
                cardActor?.setScale(if (thrower.index == 0) cardScalePlayer else cardScaleAI)
                cardActor?.zIndex = i
            }
            return true
        }
        if (event is Player.CardBeatenEvent) {
            gameState = BEATING
            // Handle when card is beaten
            playersSaidDone = 0
            playerDoneStatuses = BooleanArray(ruleSet.playerCount)
            val beatIndex = if (event.attackIndex >= 0) event.attackIndex else
                attackCards.indices.firstOrNull { attackCards[it] != null && defenseCards[it] == null } ?: return false
            val beatCard = event.card
            defenseCards[beatIndex] = beatCard
            val beatCardActor = cardActors[beatCard]
            beatCardActor?.isFaceUp = true
            beatCardActor?.rotation = 0f
            tableGroup.addActor(beatCardActor)
            beatCardActor?.zIndex = 2 * beatIndex + 1
            beatCardActor?.setScale(cardScaleTable)
            val beatPos = tablePosition.clone()
            beatPos[0] += (90 * beatIndex).toFloat()
            if (beatIndex >= 3) {
                beatPos[0] += nextLineDelta[0]
                beatPos[1] += nextLineDelta[1]
            }
            beatCardActor?.addAction(Actions.sequence(
                    GameStateChangedAction(BEATING),
                    Actions.moveTo(beatPos[0] + tableDelta[0], beatPos[1] + tableDelta[1], 0.4f),
                    Actions.delay(0.2f),
                    GameStateChangedAction(if (areAllCardsBeaten) BEATEN else THROWN)))
            val beater = event.getTarget() as Player
            logger.debug("${beater.name} (${beater.index}) beats with $beatCard\n")
            for (i in 0 until beater.hand.size) {
                val cardActor = cardActors[beater.hand[i]]
                val position = handPosition(beater.index)
                val delta = handDelta(beater.index)
                val posX = position[0] + i * delta[0]
                val posY = position[1] + i * delta[1]
                //cardActor.addAction(Actions.moveTo(posX, posY, 0.1f));
                cardActor?.setPosition(posX, posY)
                cardActor?.rotation = 0.0f
                cardActor?.setScale(if (beater.index == 0) cardScalePlayer else cardScaleAI)
                cardActor?.zIndex = i
            }
            return true
        }
        if (event is Player.CardPassedEvent) {
            gameState = THROWING
            // Handle when player passes
            playersSaidDone = 0
            playerDoneStatuses = BooleanArray(ruleSet.playerCount)
            var passIndex = 0
            while (attackCards[passIndex] != null) passIndex++
            val passCard = event.card
            attackCards[passIndex] = passCard
            val passCardActor = cardActors[passCard]
            passCardActor?.isFaceUp = true
            passCardActor?.rotation = 0f
            tableGroup.addActor(passCardActor)
            passCardActor?.zIndex = 2 * passIndex
            passCardActor?.setScale(cardScaleTable)
            val passPos = tablePosition.clone()
            passPos[0] += (90 * passIndex).toFloat()
            if (passIndex >= 3) {
                passPos[0] += nextLineDelta[0]
                passPos[1] += nextLineDelta[1]
            }
            passCardActor?.addAction(Actions.sequence(
                    GameStateChangedAction(GameState.THROWING),
                    Actions.moveTo(passPos[0], passPos[1], 0.4f),
                    Actions.delay(0.2f),
                    GameStateChangedAction(GameState.THROWN)))
            val thrower = event.getTarget() as Player
            logger.debug("${thrower.name} (${thrower.index}) passes with $passCard\n")
            for (i in 0 until thrower.hand.size) {
                val cardActor = cardActors[thrower.hand[i]]
                val position = handPosition(thrower.index)
                val delta = handDelta(thrower.index)
                val posX = position[0] + i * delta[0]
                val posY = position[1] + i * delta[1]
                //cardActor.addAction(Actions.moveTo(posX, posY, 0.1f));
                cardActor?.setPosition(posX, posY)
                cardActor?.rotation = 0.0f
                cardActor?.setScale(if (thrower.index == 0) cardScalePlayer else cardScaleAI)
                cardActor?.zIndex = i
            }
            do {
                currentAttackerIndex++
                if (currentAttackerIndex == players.size) currentAttackerIndex = 0
            } while (outOfPlay[currentAttackerIndex])
            currentThrowerIndex = currentAttackerIndex
            throwLimit = min(DEAL_LIMIT, currentDefender.hand.size)
            logger.debug("Passed to: ${currentAttacker.name} -> ${currentDefender.name}")
        }
        if (event is Player.TakeEvent) {
            // Handle when player takes
            playersSaidDone = 0
            playerDoneStatuses = BooleanArray(ruleSet.playerCount)
            isPlayerTaking = true
            val player = event.getTarget() as Player
            logger.debug("${player.name} (${player.index}) decides to take")
            return true

        }
        if (event is Player.DoneEvent) {
            // Handle when player says done
            val player = event.getTarget() as Player
            if (playerDoneStatuses[player.index]) return true
            playersSaidDone++
            playerDoneStatuses[player.index] = true
            currentThrowerIndex = SeatOrder.nextThrower(player.index,currentAttacker.index,
                currentDefender.index,outOfPlay,playerDoneStatuses,ruleSet.teamPlay) ?: player.index
            logger.debug("${player.name} (${player.index}) says done\n")
            return true
        }
        return false
    }

    private fun drawCardsToPlayer(playerIndex: Int, cardCount: Int) {
        val player = players[playerIndex]
        if (!deck.cards!!.isEmpty() && flow.phase!=TableFlow.Phase.DEALING) {
            player.addAction(Actions.sequence(
                    GameStateChangedAction(GameState.DRAWING),
                    Actions.delay(0.39f),
                    GameStateChangedAction(READY),
                    SortAction(playerIndex)
            ))
        }
        for (i in 0 until cardCount) {
            if (deck.cards!!.isEmpty())
                break
            val card = deck.draw() as Card
            player.addCard(card)
            val cardActor = cardActors[card]
            cardActor?.isFaceUp = (playerIndex == 0)
            val position = handPosition(playerIndex)
            val delta = handDelta(playerIndex)
            val index = player.hand.size - 1
            val posX = position[0] + index * delta[0]
            val posY = position[1] + index * delta[1]
            if (flow.phase==TableFlow.Phase.DEALING) {
                cardActor?.isVisible=false
                cardActor?.addAction(Actions.sequence(
                    Actions.delay((i*ruleSet.playerCount+playerIndex)*0.055f),
                    Actions.visible(true),Actions.moveTo(posX,posY,0.35f)))
            } else cardActor?.addAction(Actions.moveTo(posX, posY, 0.4f))
            cardActor?.rotation = 0.0f
            cardActor?.setScale(if (playerIndex == 0) cardScalePlayer else cardScaleAI)
            playerGroups[playerIndex].addActor(cardActor)
            cardActor?.zIndex = index
        }
    }

    private val currentAttacker: Player
        get() = players[if (!outOfPlay[currentAttackerIndex]) currentAttackerIndex else
            SeatOrder.next(currentAttackerIndex,outOfPlay)]

    private val currentDefender: Player
        get() = players[SeatOrder.next(currentAttacker.index,outOfPlay)]

    private val nextDefender: Player
        get() = players[SeatOrder.next(currentDefender.index,outOfPlay)]

    private val currentThrower: Player
        get() = players[if (!outOfPlay[currentThrowerIndex]) currentThrowerIndex else
            SeatOrder.nextThrower(currentThrowerIndex,currentAttacker.index,currentDefender.index,
                outOfPlay,playerDoneStatuses,ruleSet.teamPlay) ?: currentAttacker.index]

    fun onPlatformBack() {
        if (discardModal) { closeDiscard();return }
        if (!flow.needsExitWarning) { leaveToMenu();return }
        if (confirmSurrender) cancelSurrender() else askSurrender()
    }

    private fun askSurrender() {
        if (!flow.needsExitWarning) { leaveToMenu();return }
        if (confirmSurrender) return
        closeDiscard()
        stage.cancelTouchFocus()
        confirmSurrender=true
        Gdx.input.inputProcessor=object : com.badlogic.gdx.InputAdapter() {
            override fun touchDown(x: Int,y: Int,pointer: Int,button: Int): Boolean {
                val p=stage.viewport.unproject(com.badlogic.gdx.math.Vector2(x.toFloat(),y.toFloat()))
                val cy=stage.viewport.worldHeight/2f
                val center=stage.viewport.worldWidth/2f
                if (p.y in cy-80f..cy-26f) {
                    if (p.x in center-185f..center-20f) acceptSurrender()
                    else if (p.x in center+20f..center+185f) cancelSurrender()
                }
                return true
            }
            override fun keyDown(keycode: Int): Boolean = true // Back is handled once in render().
        }
        updateActionButtons()
    }

    private fun cancelSurrender() {
        confirmSurrender=false
        Gdx.input.inputProcessor=stage
        skipClockFrame=true
        updateActionButtons()
    }

    private fun acceptSurrender() {
        if (flow.surrendered) { leaveToMenu();return }
        if (flow.surrender()) {
            // Stake was reserved on entry: finalize its loss, never subtract it a second time.
            game.economy.settleStake(-1)
            wagerReleased=true
            lastUndo=null;undoWindow=0f
            decisionSerial++
            warnAfterResume=false
            draggedCard=null
            players.forEach { it.clearActions() }
            cardActors.values.forEach { it.clearActions();it.isVisible=false }
            isPlayerTaking=false
            playerDoneStatuses.fill(false)
            toast("Вы сдались. Партия завершена. Ставка ${WagerPolicy.label(stake)} проиграна")
        }
        cancelSurrender()
    }

    private fun drawSurrenderDialog() {
        val batch=game.batch
        val w=stage.viewport.worldWidth; val cy=stage.viewport.worldHeight/2f
        batch.color=Color(0f,0f,0f,0.82f)
        batch.draw(neon.texture("pixel"),0f,0f,w,stage.viewport.worldHeight)
        batch.color=Color.valueOf("202e4b")
        batch.draw(neon.texture("slate"),w/2-215f,cy-120f,430f,235f)
        batch.color=Color.WHITE
        neon.font(23).color=Color.WHITE
        neon.font(23).draw(batch,if (flow.surrendered) "Покинуть стол?" else "Вы действительно хотите сдаться?",w/2-195f,cy+78f,390f,com.badlogic.gdx.utils.Align.center,true)
        neon.font(16).color=Color.valueOf("ffd28b")
        neon.font(16).draw(batch,if (flow.surrendered) "Ставка уже проиграна. Дополнительных списаний нет." else "Будет проиграна ставка: ${WagerPolicy.label(stake)}",w/2-190f,cy+3f,380f,com.badlogic.gdx.utils.Align.center,true)
        batch.draw(neon.texture("gold"),w/2-185f,cy-80f,165f,54f)
        batch.draw(neon.texture("blue"),w/2+20f,cy-80f,165f,54f)
        neon.font(23).color=Color.WHITE
        neon.font(23).draw(batch,"ДА",w/2-185f,cy-42f,165f,com.badlogic.gdx.utils.Align.center,false)
        neon.font(23).draw(batch,"НЕТ",w/2+20f,cy-42f,165f,com.badlogic.gdx.utils.Align.center,false)
    }

    private fun startInitialDeal() {
        // Animated deal starts only after every seat is ready
        for (i in 0 until ruleSet.playerCount) {
            drawCardsToPlayer(i, DEAL_LIMIT)
        }
        // TODO: Check for redeal
        // Determine the first attacker and thrower
        var lowestTrump = Rank.ACE
        var lowestTrumpCard = Card(Suit.SPADES, Rank.ACE)
        var firstAttacker = 0
        for (p in players) {
            for (c in p.hand) {
                if (c.suit === trumpSuit && (c.rank !== Rank.ACE && c.rank.value < lowestTrump.value || lowestTrump === Rank.ACE)) {
                    firstAttacker = p.index
                    lowestTrump = c.rank
                    lowestTrumpCard = c
                }
            }
        }

        logger.debug(players[firstAttacker].name +
                " (${players[firstAttacker].index})" +
                " has the lowest trump $lowestTrump")
        currentAttackerIndex = firstAttacker
        currentThrowerIndex = firstAttacker
    }

    private fun releaseWager() {
        if (stakeReserved && !wagerReleased) { game.economy.refundStake(); wagerReleased=true }
    }
    private fun leaveToMenu() {
        releaseWager()
        game.screen=MainMenuScreen(game)
        dispose()
    }

    private fun drawGameHud() {
        val batch = game.batch
        val mode = if (ruleSet.allowPass) "ПЕРЕВОДНОЙ" else "ПОДКИДНОЙ"
        neon.font(18).color = Color.valueOf("c7d8ee")
        neon.font(18).draw(batch,mode,90f,973f,235f,com.badlogic.gdx.utils.Align.center,false)
        batch.draw(neon.texture(if (ruleSet.allowPass) "mode_pass" else "mode_throw"),191f,923f,35f,35f)
        neon.font(16).color = Color.WHITE
        // The displayed stake is the actual amount reserved from the local wallet.
        neon.font(16).draw(batch,"Ставка: ${WagerPolicy.label(stake)}",320f,968f,123f,com.badlogic.gdx.utils.Align.right,false)
        batch.draw(neon.texture("coin"),450f,950f,21f,21f)
        val suit = suitSymbols[trumpSuit]
        suit?.setBounds(400f,872f,28f,28f)
        suit?.setScale(1f)
        suit?.draw(batch)
        neon.font(15).color = Color.WHITE
        neon.font(15).draw(batch,"Козырь",391f,925f)
        neon.font(16).draw(batch,"${cardsRemaining()} карт",326f,835f,130f,com.badlogic.gdx.utils.Align.center,false)


        val actions = humanActions()
        val hasAction = actions.take || actions.done || actions.give
        val humanTurn = TurnControls.showYourTurn(actions,decisionOwner()==0,
            flow.playing && !flow.surrendered && !confirmSurrender && draggedCard==null,
            isPlayerTaking && currentDefender.index==0,playerDoneStatuses[0])
        if (!hasAction && readyButton?.isVisible!=true) {
            val f = neon.font(if (humanTurn) 25 else 18)
            f.color = if (humanTurn) Color.valueOf("73f0c1") else Color.valueOf("aebbd3")
            val text=when {
                flow.surrendered -> "Партия завершена"
                flow.phase==TableFlow.Phase.WAITING -> "Ждём игроков"
                flow.phase==TableFlow.Phase.ANNOUNCING -> "Все готовы"
                flow.phase==TableFlow.Phase.DEALING -> "Раздача карт"
                humanTurn -> "Ваш ход"
                else -> "Ожидание"
            }
            f.draw(batch,text,12f,69f,177f,com.badlogic.gdx.utils.Align.center,false)
        }
        val prices = listOf(2,if (perks.discardVisible) 0 else 3,if (perks.highlightEnabled) 0 else 1)
        if (flow.playing && !flow.surrendered) for ((index,cost) in prices.withIndex()) {
            val x = 309f+58f*index
            neon.font(15).color = Color.valueOf("d6e6ff")
            neon.font(15).draw(batch,"$cost",x,105f,25f,com.badlogic.gdx.utils.Align.center,false)
            batch.draw(neon.texture("crystal"),x+25f,90f,14f,16f)
        }
        batch.draw(neon.texture("coin"),310f,7f,16f,16f)
        batch.draw(neon.texture("crystal"),399f,7f,16f,16f)
        neon.font(13).color = Color.WHITE
        neon.font(13).draw(batch,"${game.economy.coins}",331f,22f,66f,com.badlogic.gdx.utils.Align.left,false)
        neon.font(13).draw(batch,"${game.economy.crystals}",420f,22f,50f,com.badlogic.gdx.utils.Align.left,false)
    }

    private fun toast(text: String) { message = text; messageSeconds = 4f }

    private fun decisionOwner(): Int = if (!flow.playing || confirmSurrender) -1 else when (gameState) {
        READY -> currentAttacker.index
        THROWN -> if (isPlayerTaking) currentThrower.index else currentDefender.index
        BEATEN -> currentThrower.index
        else -> -1
    }

    private fun onDecisionTimeout() {
        val owner = decisionOwner()
        if (owner < 0) return
        stage.cancelTouchFocus()
        closeDiscard()
        lastUndo = null; undoWindow = 0f
        val player = players[owner]
        if (gameState == THROWN && !isPlayerTaking) player.sayTake()
        else if (attackCards.any { it != null }) {
            player.sayDone()
        } else {
            player.startTurn(trumpSuit,cardsRemaining(),players.map { it.hand.size }.toTypedArray())
        }
        if (owner == 0) toast("Время вышло: ход выполнен автоматически")
    }

    private fun undoLastCard() {
        val saved = lastUndo
        if (saved == null || undoWindow <= 0f || draggedCard != null || discardModal) {
            toast("Возврат доступен 3 секунды после вашей карты"); return
        }
        if (!perks.payUndo()) { toast("Недостаточно кристаллов: возврат стоит 2"); return }
        val actor = cardActors[saved.card]!!
        actor.clearActions()
        playerGroups[0].addActor(actor)
        actor.isFaceUp = true
        players[0].hand.clear(); players[0].hand.addAll(saved.hand)
        attackCards = saved.attacks.clone(); defenseCards = saved.defenses.clone()
        currentAttackerIndex = saved.attacker; currentThrowerIndex = saved.thrower
        playersSaidDone = saved.saidDone; playerDoneStatuses = saved.done.clone()
        isPlayerTaking = saved.taking; throwLimit = saved.limit
        gameState = saved.state; oldGameState = saved.state
        lastUndo = null; undoWindow = 0f; decisionSerial++
        layoutHumanHand(); updateActionButtons()
        toast("Карта возвращена: -2 кристалла")
    }

    private fun updateHandHints() {
        for (actor in cardActors.values) actor.setHint(false)
        if (!perks.highlightEnabled || discardModal) return
        val user = players[0]
        for (card in user.hand) {
            val actor = cardActors[card] ?: continue
            if (!canDrag(actor)) continue
            val legal = if (currentDefender === user)
                user.defenseTarget(card,attackCards,defenseCards,trumpSuit) != null ||
                    user.cardCanBePassed(card,attackCards,defenseCards,nextDefender.hand.size)
                else user.cardCanBeThrown(card,attackCards,defenseCards)
            actor.setHint(legal)
        }
    }

    private fun openDiscard() {
        stage.cancelTouchFocus()
        discardModal = true
        Gdx.input.inputProcessor = object : com.badlogic.gdx.InputAdapter() {
            override fun touchDown(x: Int,y: Int,pointer: Int,button: Int): Boolean {
                closeDiscard(); return true
            }
            override fun keyDown(keycode: Int): Boolean {
                // render() handles Back once; closing here too would also navigate to the lobby.
                return true
            }
        }
    }

    private fun closeDiscard() {
        if (!discardModal) return
        discardModal = false
        Gdx.input.inputProcessor = stage
    }

    private fun drawDiscardModal() {
        val batch = game.batch
        val w = stage.viewport.worldWidth
        val h = stage.viewport.worldHeight
        batch.color = Color(0f,0f,0f,0.82f)
        batch.draw(neon.texture("pixel"),0f,0f,w,h)
        batch.color = Color.valueOf("17243f")
        batch.draw(neon.texture("pixel"),20f,110f,w-40f,h-220f)
        batch.color = Color.WHITE
        neon.font(23).color = Color.WHITE
        neon.font(23).draw(batch,"ОТБОЙ: ${discardPile.size} карт",30f,h-135f,w-60f,com.badlogic.gdx.utils.Align.center,false)
        neon.font(15).color = Color.WHITE
        neon.font(15).draw(batch,"Нажмите в любом месте, чтобы закрыть",25f,140f,w-50f,com.badlogic.gdx.utils.Align.center,false)
        if (discardPile.isEmpty()) {
            neon.font(21).draw(batch,"В отбое пока нет карт",30f,h/2,w-60f,com.badlogic.gdx.utils.Align.center,false)
            return
        }
        val columns = if (screenOrientationIsPortrait) 7 else 13
        val rows = (discardPile.size+columns-1)/columns
        val cw = kotlin.math.min((w-80f)/columns-6f,(h-340f)/rows/1.5f-4f)
        val ch = cw*1.5f
        val left = (w-(cw+6f)*columns)/2f
        val style = game.preferences.getString(SettingsScreen.DECK,"rus")
        for ((i,card) in discardPile.withIndex()) {
            val x = left+(i%columns)*(cw+6f)
            val y = h-185f-ch-(i/columns)*(ch+6f)
            batch.draw(game.assetManager.get("decks/$style/$card.png",Texture::class.java),x,y,cw,ch)
        }
    }

    private fun humanActions(): TurnControls.Actions = if (!flow.playing || flow.surrendered || confirmSurrender)
        TurnControls.Actions() else TurnControls.actions(
        settled = gameState == READY || gameState == THROWN || gameState == BEATEN,
        dragging = draggedCard != null,
        attacks = attackCards.count { it != null }, defenses = defenseCards.count { it != null },
        humanDefender = currentDefender.index == 0, humanThrower = currentThrower.index == 0,
        taking = isPlayerTaking, alreadyDone = playerDoneStatuses[0]
    )

    private fun updateActionButtons() {
        val actions = humanActions()
        fun show(actor: com.badlogic.gdx.scenes.scene2d.Actor?, visible: Boolean) {
            actor?.isVisible = visible
            actor?.touchable = if (visible) Touchable.enabled else Touchable.disabled
        }
        show(readyButton,flow.showReady && !confirmSurrender)
        val tools=flow.playing && !flow.surrendered && !confirmSurrender
        show(undoButton,tools);show(discardButton,tools);show(hintButton,tools)
        show(takeButton,actions.take)
        show(doneButton,actions.done)
        show(giveButton,actions.give)
    }

    private fun canDrag(actor: CardActor): Boolean =
        flow.playing && !flow.surrendered && !confirmSurrender &&
        (gameState == READY || gameState == THROWN || gameState == BEATEN) &&
        players[0].hand.contains(actor.card) &&
        ((currentThrower.index == 0 && !playerDoneStatuses[0] &&
            attackCards.count { it != null } < throwLimit && currentDefender.hand.isNotEmpty()) ||
            (currentDefender.index == 0 && !isPlayerTaking)) &&
        actor.actions.size == 0

    private fun onPlayingArea(x: Float,y: Float): Boolean = if (screenOrientationIsPortrait)
        x in 90f..390f && y in 355f..695f else x in 140f..650f && y in 215f..360f

    private fun attackAt(x: Float,y: Float): Int? = attackCards.indices.reversed().firstOrNull {
        val card = attackCards[it]
        card != null && defenseCards[it] == null && cardActors[card]?.containsStagePoint(x,y) == true
    }

    private fun playDroppedCard(actor: CardActor,x: Float,y: Float) {
        val user = players[0]
        val card = actor.card
        val snapshot = UndoState(card,user.hand.toList(),attackCards.clone(),defenseCards.clone(),
            currentAttackerIndex,currentThrowerIndex,playersSaidDone,playerDoneStatuses.clone(),isPlayerTaking,gameState,throwLimit)
        if (currentDefender === user && !isPlayerTaking) {
            val preferred = attackAt(x,y)
            if (preferred == null && !onPlayingArea(x,y)) return
            val target = user.defenseTarget(card,attackCards,defenseCards,trumpSuit,preferred)
            if (target != null) {
                user.beatWithCard(card,attackCards,defenseCards,trumpSuit,target)
            } else if (user.cardCanBePassed(card,attackCards,defenseCards,nextDefender.hand.size)) {
                user.passWithCard(card,attackCards,defenseCards,nextDefender.hand.size)
            }
        } else if (currentThrower === user && onPlayingArea(x,y)) {
            user.throwCard(card,attackCards,defenseCards,trumpSuit)
        }
        if (!user.hand.contains(card)) { lastUndo = snapshot; undoWindow = 3f }
    }

    private fun layoutHumanHand() {
        val hand = players[0].hand
        for ((index,card) in hand.withIndex()) {
            val actor = cardActors[card] ?: continue
            if (actor === draggedCard || actor.actions.size > 0) continue
            val pose = HandFan.pose(index,hand.size,actor.width*cardScalePlayer,
                actor.height*cardScalePlayer,screenOrientationIsPortrait)
            actor.setScale(cardScalePlayer)
            actor.setPosition(pose.x,pose.y)
            actor.rotation = pose.angle
            actor.zIndex = index
        }
    }

    private fun handDelta(index: Int): FloatArray {
        if (index != 0) return aiDelta.clone()
        if (!screenOrientationIsPortrait) return playerDelta.clone()
        return floatArrayOf(min(54f, 315f / kotlin.math.max(1, players[0].hand.size - 1)), 0f)
    }

    private fun handPosition(index: Int): FloatArray {
        if (index == 0) return playerPosition.clone()
        if (screenOrientationIsPortrait) {
            val seat=SeatOrder.portraitSeat(index,players.size)
            return floatArrayOf(if (seat[0]<100f) -10f else if (seat[0]>300f) 425f else 202f,seat[1]-65f)
        }
        val position = aiPosition.clone()
        if (ruleSet.playerCount > 2) position[0] += ((index - 1) * offset / (ruleSet.playerCount - 2)).toFloat()
        return position
    }

    private fun sortPlayerCards() {
        // TODO: Generalise to other players
        val player = players[0]
        player.sortCards(sortingMode, trumpSuit)
        // Reposition all cards
        for (i in 0 until player.hand.size) {
            val cardActor = cardActors[player.hand[i]]
            val position = handPosition(player.index)
            val delta = handDelta(player.index)
            val posX = position[0] + i * delta[0]
            val posY = position[1] + i * delta[1]
            cardActor?.setPosition(posX, posY)
            cardActor?.rotation = 0.0f
            cardActor?.setScale(if (player.index == 0) cardScalePlayer else cardScaleAI)
            cardActor?.zIndex = i
        }
    }

    companion object {

        private const val DEAL_LIMIT = 6

        private const val CARD_SCALE_TABLE_LANDSCAPE = 0.24f
        private const val CARD_SCALE_AI_LANDSCAPE = 0.18f
        private const val CARD_SCALE_PLAYER_LANDSCAPE = 0.28f
        // Half-widths and half-heights
        private const val HW_TABLE_LANDSCAPE = CARD_SCALE_TABLE_LANDSCAPE * 180
        private const val HW_AI_LANDSCAPE = CARD_SCALE_AI_LANDSCAPE * 180
        private const val HW_PLAYER_LANDSCAPE = CARD_SCALE_PLAYER_LANDSCAPE * 180
        private const val HH_TABLE_LANDSCAPE = CARD_SCALE_TABLE_LANDSCAPE * 270
        private const val HH_AI_LANDSCAPE = CARD_SCALE_AI_LANDSCAPE * 270
        private const val HH_PLAYER_LANDSCAPE = CARD_SCALE_PLAYER_LANDSCAPE * 270

        private val DECK_POSITION_LANDSCAPE = floatArrayOf(60 - HW_TABLE_LANDSCAPE, 240 - HH_TABLE_LANDSCAPE)
        private val DISCARD_PILE_POSITION_LANDSCAPE = floatArrayOf(680 - HW_TABLE_LANDSCAPE, 180 - HH_TABLE_LANDSCAPE)
        private val PLAYER_POSITION_LANDSCAPE = floatArrayOf(240 - HW_PLAYER_LANDSCAPE, 80 - HH_PLAYER_LANDSCAPE)
        private val AI_POSITION_LANDSCAPE = floatArrayOf(60 - HW_AI_LANDSCAPE, 400 - HH_AI_LANDSCAPE)
        private val TABLE_POSITION_LANDSCAPE = floatArrayOf(200 - HW_TABLE_LANDSCAPE, 280 - HH_TABLE_LANDSCAPE)
        private val TABLE_DELTA_LANDSCAPE = floatArrayOf(10f, -10f)
        private val PLAYER_DELTA_LANDSCAPE = floatArrayOf(40f, 0f)
        private val AI_DELTA_LANDSCAPE = floatArrayOf(5f, -5f)
        private val NEXT_LINE_DELTA_LANDSCAPE = floatArrayOf(0f, 0f)

        private const val CARD_SCALE_TABLE_PORTRAIT = 0.20f
        private const val CARD_SCALE_AI_PORTRAIT = 0.10f
        private const val CARD_SCALE_PLAYER_PORTRAIT = 0.36f
        // Half-widths and half-heights
        private const val HW_TABLE_PORTRAIT = CARD_SCALE_TABLE_PORTRAIT * 180
        private const val HW_AI_PORTRAIT = CARD_SCALE_AI_PORTRAIT * 180
        private const val HW_PLAYER_PORTRAIT = CARD_SCALE_PLAYER_PORTRAIT * 180
        private const val HH_TABLE_PORTRAIT = CARD_SCALE_TABLE_PORTRAIT * 270
        private const val HH_AI_PORTRAIT = CARD_SCALE_AI_PORTRAIT * 270
        private const val HH_PLAYER_PORTRAIT = CARD_SCALE_PLAYER_PORTRAIT * 270

        private val DECK_POSITION_PORTRAIT = floatArrayOf(332f, 844f)
        private val DISCARD_PILE_POSITION_PORTRAIT = floatArrayOf(435 - HW_TABLE_PORTRAIT, 400 - HH_TABLE_PORTRAIT)
        private val PLAYER_POSITION_PORTRAIT = floatArrayOf(90 - HW_PLAYER_PORTRAIT, 195 - HH_PLAYER_PORTRAIT)
        private val AI_POSITION_PORTRAIT = floatArrayOf(40 - HW_AI_PORTRAIT, 800 - HH_AI_PORTRAIT)
        private val TABLE_POSITION_PORTRAIT = floatArrayOf(145 - HW_TABLE_PORTRAIT, 590 - HH_TABLE_PORTRAIT)
        private val TABLE_DELTA_PORTRAIT = floatArrayOf(10f, -10f)
        private val PLAYER_DELTA_PORTRAIT = floatArrayOf(40f, 0f)
        private val AI_DELTA_PORTRAIT = floatArrayOf(5f, -5f)
        private val NEXT_LINE_DELTA_PORTRAIT = floatArrayOf(-270f, -160f)
    }
}
