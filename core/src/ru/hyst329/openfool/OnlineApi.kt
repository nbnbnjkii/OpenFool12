package ru.hyst329.openfool

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Net
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

internal fun jsonObject(vararg fields: Pair<String,Any>): JsonValue {
    val value=JsonValue(JsonValue.ValueType.`object`)
    for ((key,v) in fields) value.addChild(key,when(v) {
        is Boolean -> JsonValue(v)
        is Int -> JsonValue(v.toLong())
        is Long -> JsonValue(v)
        else -> JsonValue(v.toString())
    })
    return value
}

/** Passwords are sent over HTTPS only and are never written to preferences. */
internal class OnlineApi(private val game: OpenFoolGame) {
    var token: String
        get()=game.preferences.getString("Auth/Token","")
        private set(value) { game.preferences.putString("Auth/Token",value).flush() }
    var playerId: String
        get()=game.preferences.getString("Auth/PlayerId","")
        private set(value) { game.preferences.putString("Auth/PlayerId",value).flush() }
    val supportsEconomy: Boolean get()=game.preferences.getBoolean("Auth/ServerEconomy",false)
    val coins: Int get()=game.preferences.getInteger("Auth/Coins",10000)
    val crystals: Int get()=game.preferences.getInteger("Auth/Crystals",20)
    fun updateWallet(wallet: JsonValue?) {
        if(wallet==null)return
        game.preferences.putInteger("Auth/Coins",wallet.getInt("coins"))
            .putInteger("Auth/Crystals",wallet.getInt("crystals")).flush()
    }
    fun remember(response: JsonValue) {
        token=response.getString("token")
        updateProfile(response.get("user"))
    }
    fun updateProfile(user: JsonValue) {
        playerId=user.getString("id")
        game.preferences.putBoolean("Auth/ServerEconomy",user.has("wallet")).flush()
        updateWallet(user.get("wallet"))
        PlayerProfile(game.preferences).save(user.getString("nickname"),user.getInt("avatar"))
    }
    fun logout() {
        request("POST","/auth/logout",jsonObject(),{},{})
        token="";playerId=""
    }
    fun request(method: String,path: String,body: JsonValue?=null,ok: (JsonValue)->Unit,error: (String)->Unit) {
        val req=Net.HttpRequest(method)
        req.url="${ServerEndpoint.BASE_URL}$path"
        req.timeOut=12000
        req.setHeader("Accept","application/json")
        if (token.isNotEmpty()) req.setHeader("Authorization","Bearer $token")
        if (body!=null) { req.setHeader("Content-Type","application/json");req.content=body.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json) }
        Gdx.net.sendHttpRequest(req,object : Net.HttpResponseListener {
            override fun handleHttpResponse(response: Net.HttpResponse) {
                val status=response.status.statusCode
                val parsed=try { JsonReader().parse(response.resultAsString) } catch (_: Exception) { null }
                Gdx.app.postRunnable {
                    if (status in 200..299 && parsed!=null) {
                        updateWallet(parsed.get("wallet"))
                        updateWallet(parsed.get("room")?.get("wallet"))
                        ok(parsed)
                    }
                    else error(parsed?.getString("error",null) ?: "Сервер недоступен (HTTP $status). Проверьте установку и HTTPS.")
                }
            }
            override fun failed(t: Throwable?) { Gdx.app.postRunnable { error("Нет соединения с сервером. Проверьте интернет и HTTPS сервера.") } }
            override fun cancelled() { Gdx.app.postRunnable { error("Запрос отменён") } }
        })
    }
}
